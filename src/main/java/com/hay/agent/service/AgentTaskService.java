package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.RefineStepPreviewRequest;
import com.hay.agent.api.dto.UpdateStepPreviewRequest;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.planner.Planner;
import com.hay.agent.service.preview.StepPreviewGenerator;
import com.hay.agent.store.TaskStore;
import com.hay.agent.tool.ToolExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import lombok.extern.slf4j.Slf4j;


import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文件作用：任务编排核心服务（业务层）。
 * 项目角色：维护任务状态机，驱动规划器和工具执行器，并统一记录任务事件。
 */

@Slf4j
@Service
public class AgentTaskService {

    private final TaskStore taskStore;
    private final Planner planner;
    private final ToolExecutor toolExecutor;
    private final List<StepPreviewGenerator> stepPreviewGenerators;
    private final PreviewRefinementService previewRefinementService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;

    @Value("${agent.task.auto-run:false}")
    private boolean autoRun;

    public AgentTaskService(TaskStore taskStore,
                            Planner planner,
                            ToolExecutor toolExecutor,
                            List<StepPreviewGenerator> stepPreviewGenerators,
                            PreviewRefinementService previewRefinementService,
                            com.fasterxml.jackson.databind.ObjectMapper objectMapper) {
        this.taskStore = taskStore;
        this.planner = planner;
        this.toolExecutor = toolExecutor;
        this.stepPreviewGenerators = stepPreviewGenerators;
        this.previewRefinementService = previewRefinementService;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建任务
     * @param request   创建任务请求
     * @return          创建的任务实例
     */
    public AgentTask createTask(CreateTaskRequest request) {
        if(request.getUserId() == null || request.getUserId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"用户ID不能为空");
        }
        if(request.getRequestId() == null || request.getRequestId().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"请求ID不能为空");
        }

        Instant now = Instant.now();
        AgentTask task = AgentTask.builder()
                .taskId(UUID.randomUUID().toString())
                .requestId(request.getRequestId())
                .source(request.getSource() == null || request.getSource().isBlank() ? "im_text" : request.getSource())
                .userId(request.getUserId())
                .inputText(request.getInputText())
                .status(TaskStatus.CREATED)
                .nextAction("plan")
                .createdAt(now)
                .updatedAt(now)
                .build();

        //记录任务创建事件
        addEvent(task, "TASK_CREATED", "Task created", Map.of("status", task.getStatus().name()));
        
        // 先保存任务到数据库，确保异步执行时能查到
        AgentTask savedTask = save(task);
        
        if (!autoRun) {
            log.info("当前已关闭自动执行，任务{}仅创建并等待前端手动规划/执行", savedTask.getTaskId());
            return savedTask;
        }

        //独立异步线程 - 异步自动执行规划和所有步骤
          CompletableFuture.runAsync(() -> {
              try{
                  //1.自动规划任务
                  AgentTask plannedTask = planTask(savedTask.getTaskId());
                  //2.遍历执行所有步骤
                  for(PlanStep step : plannedTask.getPlanSteps()) {
                      // 执行步骤
                      Optional<Artifact> artifactOpt = toolExecutor.execute(step,
                          plannedTask.getTaskId(),
                          plannedTask.getInputText());
                          
                      // 保存生成的工件
                      artifactOpt.ifPresent(artifact -> plannedTask.getArtifacts().add(artifact));
                      
                      //更新步骤状态为已完成
                      step.setStatus(StepStatus.DONE);
                      
                      // 添加步骤完成事件
                      addEvent(plannedTask, "STEP_COMPLETED", "步骤执行完成", 
                          Map.of("stepId", step.getStepId(), "stepName", step.getAction()));
                  }
                  //3.标记任务完成，状态为已交付
                  plannedTask.setStatus(TaskStatus.DELIVERED);
                  plannedTask.setNextAction("none");  //没有下一步操作
                  
                  // 添加任务完成事件
                  addEvent(plannedTask, "TASK_COMPLETED", "任务全部执行完成",
                      Map.of("artifactCount", String.valueOf(plannedTask.getArtifacts().size())));
                      
                  save(plannedTask);
                  log.info("任务{}自动执行完成，生成工件{}个",savedTask.getTaskId(), plannedTask.getArtifacts().size());
              }catch (Exception e) {
                  // 任务失败时必须回写状态并持久化，避免 Redis 长期停留在 CREATED。
                  savedTask.setStatus(TaskStatus.FAILED);
                  savedTask.setNextAction("none");
                  addEvent(savedTask, "TASK_FAILED", "任务执行失败: " + e.getMessage(), Map.of("cause", e.getClass().getSimpleName()));
                  save(savedTask);
                  log.error("任务{}自动执行失败，已回写失败状态",savedTask.getTaskId(),e);
              }
          });
        return savedTask;
    }

    /**
     * 规划任务
     * @param taskId    任务ID
     * @return          规划后的任务实例
     */
    public AgentTask planTask(String taskId) {
        AgentTask task = getTask(taskId);
        if (task.getStatus() != TaskStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "任务必须处于 CREATED 状态才能生成规划");
        }

        // 规划步骤由 Planner 抽象提供：当前是 Mock，后续可平滑替换为大模型规划结果。
        List<PlanStep> steps = planner.plan(task.getInputText());
        task.setPlanSteps(steps);
        task.setStatus(TaskStatus.PLANNED);
        task.setNextAction("execute");
        addEvent(task, "TASK_PLANNED", "Planner generated step list", Map.of("steps", String.valueOf(steps.size())));

        return save(task);
    }

    /**
     * 确认任务步骤
     * @param taskId    任务ID
     * @param request   确认任务步骤请求
     * @return          确认后的任务实例
     */
    public AgentTask confirmStep(String taskId, ConfirmTaskRequest request) {
        AgentTask task = getTask(taskId);
        PlanStep step = task.getPlanSteps().stream()
                .filter(s -> s.getStepId().equals(request.getStepId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));

        if (!step.isRequiresConfirm()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "This step does not require confirmation");
        }

        if (step.getStatus() != StepStatus.WAIT_CONFIRM) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Step is not waiting for confirmation");
        }

        // 人工拒绝在 MVP 中直接终止任务，用于清晰展示“人机协同可控”。
        if (Boolean.TRUE.equals(request.getApproved())) {
            step.setStatus(StepStatus.APPROVED);
            task.setStatus(TaskStatus.PLANNED);
            task.setNextAction("execute");
            addEvent(task, "STEP_APPROVED", "Step approved by user", Map.of("stepId", step.getStepId()));
        } else {
            step.setStatus(StepStatus.SKIPPED);
            task.setStatus(TaskStatus.FAILED);
            task.setNextAction("none");
            addEvent(task, "STEP_REJECTED", "Step rejected by user", Map.of("stepId", step.getStepId()));
        }

        return save(task);
    }

    /**
     * 为指定步骤生成预览产物。用于 confirm1 之后、正式创建飞书产物之前的 confirm2。
     */
    public AgentTask generateStepPreview(String taskId, String stepId, GenerateStepPreviewRequest request) {
        AgentTask task = getTask(taskId);
        PlanStep step = task.getPlanSteps().stream()
                .filter(s -> s.getStepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));

        StepPreviewGenerator generator = findPreviewGenerator(step);

        try {
            Object preview = generator.generate(task, step, request);
            JsonNode previewData = objectMapper.valueToTree(preview);
            step.setPreviewData(previewData);
            replacePreviewArtifact(task, step, generator.previewArtifactType(), generator.previewTitle(), previewData);

            step.setStatus(StepStatus.WAIT_CONFIRM);
            task.setStatus(TaskStatus.WAIT_CONFIRM);
            task.setNextAction("confirm:" + step.getStepId());
            addEvent(task, "STEP_PREVIEW_READY", "步骤预览已生成",
                    Map.of("stepId", step.getStepId(), "artifactType", generator.previewArtifactType()));
            return save(task);
        } catch (Exception e) {
            step.setStatus(StepStatus.FAILED);
            task.setStatus(TaskStatus.FAILED);
            task.setNextAction("none");
            addEvent(task, "STEP_PREVIEW_FAILED", "步骤预览生成失败：" + e.getMessage(), Map.of("stepId", step.getStepId()));
            save(task);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "生成预览失败：" + e.getMessage(), e);
        }
    }

    /**
     * 保存卡片2/详情页编辑后的结构化预览数据，供 confirm2 后的正式创建复用。
     */
    public AgentTask updateStepPreview(String taskId, String stepId, UpdateStepPreviewRequest request) {
        AgentTask task = getTask(taskId);
        PlanStep step = task.getPlanSteps().stream()
                .filter(s -> s.getStepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        StepPreviewGenerator generator = findPreviewGenerator(step);

        if (request == null || request.getPreviewData() == null || !request.getPreviewData().isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "previewData 必须是 JSON 对象");
        }

        ObjectNode previewData = request.getPreviewData().deepCopy();
        normalizeAndValidatePreviewData(step, generator, previewData);
        step.setPreviewData(previewData);
        replacePreviewArtifact(task, step, generator.previewArtifactType(), readPreviewTitle(previewData, generator.previewTitle()), previewData);

        step.setStatus(StepStatus.WAIT_CONFIRM);
        task.setStatus(TaskStatus.WAIT_CONFIRM);
        task.setNextAction("confirm:" + step.getStepId());
        addEvent(task, "STEP_PREVIEW_UPDATED", "用户已更新步骤预览",
                Map.of("stepId", step.getStepId(), "artifactType", generator.previewArtifactType()));
        return save(task);
    }

    /**
     * 自然语言精修入口。优先调用大模型生成新的结构化预览数据；
     * 大模型不可用或输出无效时，降级为确定性轻量规则修改。
     */
    public AgentTask refineStepPreview(String taskId, String stepId, RefineStepPreviewRequest request) {
        AgentTask task = getTask(taskId);
        PlanStep step = task.getPlanSteps().stream()
                .filter(s -> s.getStepId().equals(stepId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Step not found"));
        StepPreviewGenerator generator = findPreviewGenerator(step);

        if (step.getPreviewData() == null || !step.getPreviewData().isObject()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "请先生成预览，再进行精修");
        }
        if (request == null || request.getInstruction() == null || request.getInstruction().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "精修指令不能为空");
        }

        ObjectNode previewData = previewRefinementService
                .refine(step.getPreviewData(), request.getInstruction(), generator.expectedPreviewDataType())
                .filter(JsonNode::isObject)
                .map(refined -> (ObjectNode) refined.deepCopy())
                .orElseGet(() -> {
                    ObjectNode fallbackPreviewData = step.getPreviewData().deepCopy();
                    applyPreviewInstruction(fallbackPreviewData, request.getInstruction());
                    return fallbackPreviewData;
                });
        normalizeAndValidatePreviewData(step, generator, previewData);
        step.setPreviewData(previewData);
        replacePreviewArtifact(task, step, generator.previewArtifactType(), readPreviewTitle(previewData, generator.previewTitle()), previewData);

        step.setStatus(StepStatus.WAIT_CONFIRM);
        task.setStatus(TaskStatus.WAIT_CONFIRM);
        task.setNextAction("confirm:" + step.getStepId());
        addEvent(task, "STEP_PREVIEW_REFINED", "步骤预览已按自然语言指令精修",
                Map.of("stepId", step.getStepId(), "artifactType", generator.previewArtifactType()));
        return save(task);
    }

    private StepPreviewGenerator findPreviewGenerator(PlanStep step) {
        return stepPreviewGenerators.stream()
                .filter(generator -> generator.supports(step))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "当前仅文档和 PPT 步骤支持预览"));
    }

    private void replacePreviewArtifact(AgentTask task, PlanStep step, String artifactType, String title, JsonNode previewData) {
        task.getArtifacts().removeIf(artifact -> step.getStepId().equals(artifact.getStepId())
                && artifact.getType() != null
                && artifact.getType().endsWith("-preview"));
        task.getArtifacts().add(Artifact.builder()
                .type(artifactType)
                .stepId(step.getStepId())
                .title(title)
                .url("preview://" + task.getTaskId() + "/" + step.getStepId())
                .previewData(previewData)
                .build());
    }

    private void normalizeAndValidatePreviewData(PlanStep step, StepPreviewGenerator generator, ObjectNode previewData) {
        String artifactType = previewData.path("artifactType").asText("");
        if (!artifactType.isBlank() && !generator.expectedPreviewDataType().equals(artifactType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "previewData 的 artifactType 必须是 " + generator.expectedPreviewDataType());
        }
        previewData.put("artifactType", generator.expectedPreviewDataType());

        if ("D_SLIDES".equals(step.getStepId()) && previewData.path("theme").asText("").isBlank()) {
            String previousTheme = step.getPreviewData() == null ? "" : step.getPreviewData().path("theme").asText("");
            previewData.put("theme", previousTheme.isBlank() ? "business" : previousTheme);
        }
    }

    private String readPreviewTitle(JsonNode previewData, String fallback) {
        String title = previewData == null ? "" : previewData.path("title").asText("");
        return title.isBlank() ? fallback : title;
    }

    private void applyPreviewInstruction(ObjectNode previewData, String instruction) {
        String lower = instruction.toLowerCase();
        if (containsAny(lower, "business", "商务", "稳重", "正式")) {
            previewData.put("theme", "business");
        } else if (containsAny(lower, "tech", "科技", "蓝色", "技术")) {
            previewData.put("theme", "tech");
        } else if (containsAny(lower, "campaign", "营销", "大促", "红色", "活动")) {
            previewData.put("theme", "campaign");
        } else if (containsAny(lower, "minimal", "极简", "简洁", "清爽")) {
            previewData.put("theme", "minimal");
        }

        Matcher titleMatcher = Pattern.compile("(?:标题|主题|命名|改名|title)[为成叫：: ]+([^，。\\n]+)").matcher(instruction);
        if (titleMatcher.find()) {
            previewData.put("title", titleMatcher.group(1).trim());
        }

        previewData.put("lastRefineInstruction", instruction);
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行任务
     * @param taskId    任务ID
     * @return          执行后的任务实例
     */
    public AgentTask executeTask(String taskId) {
        AgentTask task = getTask(taskId);
        if (task.getStatus() == TaskStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "执行任务前必须先生成规划");
        }
        if (task.getStatus() == TaskStatus.DELIVERED || task.getStatus() == TaskStatus.FAILED) {
            return task;
        }

        task.setStatus(TaskStatus.RUNNING);
        task.setNextAction("running");

        for (PlanStep step : task.getPlanSteps()) {
            if (step.getStatus() == StepStatus.DONE || step.getStatus() == StepStatus.SKIPPED) {
                continue;
            }

            // 风险动作（发布、交付等）先进入确认闸门，等待前端明确同意。
            if (step.isRequiresConfirm() && step.getStatus() != StepStatus.APPROVED) {
                step.setStatus(StepStatus.WAIT_CONFIRM);
                task.setStatus(TaskStatus.WAIT_CONFIRM);
                task.setNextAction("confirm:" + step.getStepId());
                addEvent(task, "STEP_WAIT_CONFIRM", "Step needs user confirmation", Map.of("stepId", step.getStepId()));
                return save(task);
            }

            step.setStatus(StepStatus.RUNNING);
            addEvent(task, "STEP_RUNNING", "Executing step", Map.of("stepId", step.getStepId(), "tool", step.getTool()));
            // 控制类步骤可能不产出实体结果，因此工具执行返回值允许为空。
            toolExecutor.execute(step, task.getTaskId(), task.getInputText()).ifPresent(task.getArtifacts()::add);
            step.setStatus(StepStatus.DONE);
            addEvent(task, "STEP_DONE", "Step completed", Map.of("stepId", step.getStepId()));
        }

        //当前任务执行完成，生成了结果，状态设置为交付状态，任务执行完成
        task.setStatus(TaskStatus.DELIVERED);
        task.setNextAction("none");
        addEvent(task, "TASK_DELIVERED", "Task completed and delivered", Map.of("artifacts", String.valueOf(task.getArtifacts().size())));
        return save(task);
    }

    /**
     * 获取任务详情
     * @param taskId    任务ID
     * @return          任务实例
     */
    public AgentTask getTask(String taskId) {
        return taskStore.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    /**
     * 获取任务事件列表
     * @param taskId    任务ID
     * @return          任务事件列表
     */
    public List<TaskEvent> listEvents(String taskId) {
        return getTask(taskId).getEvents();
    }

    /*
    私有方法
     */

    /**
     * 保存任务
     * @param task    任务实例
     * @return        保存后的任务实例
     */
    private AgentTask save(AgentTask task) {
        task.setUpdatedAt(Instant.now());
        return taskStore.save(task);
    }

    /**
     * 添加任务事件
     * @param task    任务实例
     * @param type    事件类型
     * @param message 事件消息
     * @param metadata 事件元数据
     */
    private void addEvent(AgentTask task, String type, String message, Map<String, String> metadata) {
        task.getEvents().add(TaskEvent.builder()
                .timestamp(Instant.now())
                .type(type)
                .message(message)
                .metadata(metadata)
                .build());
    }
}

