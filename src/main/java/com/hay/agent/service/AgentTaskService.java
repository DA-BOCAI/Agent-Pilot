package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.planner.Planner;
import com.hay.agent.store.TaskStore;
import com.hay.agent.tool.ToolExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 文件作用：任务编排核心服务（业务层）。
 * 项目角色：维护任务状态机，驱动规划器和工具执行器，并统一记录任务事件。
 */

@Service
public class AgentTaskService {

    private final TaskStore taskStore;
    private final Planner planner;
    private final ToolExecutor toolExecutor;

    public AgentTaskService(TaskStore taskStore, Planner planner, ToolExecutor toolExecutor) {
        this.taskStore = taskStore;
        this.planner = planner;
        this.toolExecutor = toolExecutor;
    }

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

        addEvent(task, "TASK_CREATED", "Task created", Map.of("status", task.getStatus().name()));
        return save(task);
    }

    public AgentTask planTask(String taskId) {
        AgentTask task = getTask(taskId);
        if (task.getStatus() != TaskStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Task must be in CREATED status before planning");
        }

        // 规划步骤由 Planner 抽象提供：当前是 Mock，后续可平滑替换为大模型规划结果。
        List<PlanStep> steps = planner.plan(task.getInputText());
        task.setPlanSteps(steps);
        task.setStatus(TaskStatus.PLANNED);
        task.setNextAction("execute");
        addEvent(task, "TASK_PLANNED", "Planner generated step list", Map.of("steps", String.valueOf(steps.size())));

        return save(task);
    }

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

    public AgentTask executeTask(String taskId) {
        AgentTask task = getTask(taskId);
        if (task.getStatus() == TaskStatus.CREATED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Plan must be generated before execution");
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

        task.setStatus(TaskStatus.DELIVERED);
        task.setNextAction("none");
        addEvent(task, "TASK_DELIVERED", "Task completed and delivered", Map.of("artifacts", String.valueOf(task.getArtifacts().size())));
        return save(task);
    }

    public AgentTask getTask(String taskId) {
        return taskStore.findById(taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Task not found"));
    }

    public List<TaskEvent> listEvents(String taskId) {
        return getTask(taskId).getEvents();
    }

    private AgentTask save(AgentTask task) {
        task.setUpdatedAt(Instant.now());
        return taskStore.save(task);
    }

    private void addEvent(AgentTask task, String type, String message, Map<String, String> metadata) {
        task.getEvents().add(TaskEvent.builder()
                .timestamp(Instant.now())
                .type(type)
                .message(message)
                .metadata(metadata)
                .build());
    }
}

