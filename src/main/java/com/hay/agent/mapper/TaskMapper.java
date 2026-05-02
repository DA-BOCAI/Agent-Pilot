package com.hay.agent.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.hay.agent.api.dto.TaskCardView;
import com.hay.agent.api.dto.TaskView;
import com.hay.agent.api.dto.TaskWorkspaceView;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class TaskMapper {

    public TaskView toView(AgentTask task) {
        return TaskView.builder()
                .taskId(task.getTaskId())
                .requestId(task.getRequestId())
                .source(task.getSource())
                .userId(task.getUserId())
                .inputText(task.getInputText())
                .status(task.getStatus())
                .nextAction(task.getNextAction())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .planSteps(task.getPlanSteps())
                .artifacts(task.getArtifacts())
                .events(task.getEvents())
                .build();
    }

    public TaskCardView toCardView(AgentTask task) {
        return TaskCardView.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus().name())
                .nextAction(task.getNextAction())
                .steps(task.getPlanSteps().stream().map(this::toStepProgress).toList())
                .confirm(toConfirmCard(task))
                .completion(toCompletionCard(task))
                .build();
    }

    public TaskWorkspaceView toWorkspaceView(AgentTask task) {
        String title = resolveTaskTitle(task);
        TaskWorkspaceView.Preview preview = toCurrentPreview(task);
        return TaskWorkspaceView.builder()
                .taskId(task.getTaskId())
                .title(title)
                .status(task.getStatus().name())
                .displayStatus(displayTaskStatus(task))
                .nextAction(task.getNextAction())
                .source(task.getSource())
                .userId(task.getUserId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .inputSummary(summarizeTitle(task.getInputText()))
                .contextText(task.getInputText())
                .steps(task.getPlanSteps().stream().map(step -> toWorkspaceStep(task, step)).toList())
                .confirmation(toWorkspaceConfirmation(task, preview))
                .preview(preview)
                .adjustments(toWorkspaceAdjustments(task, preview))
                .outputs(task.getArtifacts().stream()
                        .filter(artifact -> !isPreviewArtifact(artifact))
                        .map(this::toWorkspaceOutput)
                        .toList())
                .timeline(task.getEvents().stream().map(this::toWorkspaceTimelineEvent).toList())
                .debugTask(toView(task))
                .build();
    }

    private TaskWorkspaceView.StepItem toWorkspaceStep(AgentTask task, PlanStep step) {
        String status = step.getStatus() == null ? StepStatus.PENDING.name() : step.getStatus().name();
        return TaskWorkspaceView.StepItem.builder()
                .stepId(step.getStepId())
                .code(step.getStepId())
                .name(summarizeTitle(step.getAction()))
                .action(step.getAction())
                .status(status)
                .displayStatus(displayStatus(step.getStatus()))
                .requiresConfirm(step.isRequiresConfirm())
                .active(isActiveStep(task, step))
                .build();
    }

    private TaskWorkspaceView.Confirmation toWorkspaceConfirmation(AgentTask task, TaskWorkspaceView.Preview preview) {
        Optional<PlanStep> waitingStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst();
        if (waitingStep.isEmpty()) {
            return TaskWorkspaceView.Confirmation.builder()
                    .waiting(false)
                    .title("当前无需确认")
                    .description("Agent 正在自动推进或任务已经结束。")
                    .previewReady(preview != null && preview.isAvailable())
                    .preview(preview)
                    .build();
        }

        PlanStep step = waitingStep.get();
        boolean confirm2 = hasPreviewData(step);
        return TaskWorkspaceView.Confirmation.builder()
                .waiting(true)
                .phase(confirm2 ? "confirm2" : "confirm1")
                .stepId(step.getStepId())
                .title(confirm2 ? "预览已生成，请确认是否正式创建飞书产物" : "请确认 Agent 是否正确理解你的需求")
                .description(confirm2
                        ? "你可以查看预览并精修；确认后，Agent 将正式创建飞书产物。"
                        : "确认后，Agent 将先生成结构化预览，随后进入第二次确认。")
                .action(step.getAction())
                .artifactType(readArtifactType(step))
                .theme(step.getPreviewData() == null ? null : step.getPreviewData().path("theme").asText(null))
                .previewReady(confirm2)
                .preview(confirm2 ? preview : null)
                .build();
    }

    private TaskWorkspaceView.Preview toCurrentPreview(AgentTask task) {
        Optional<PlanStep> waitingPreviewStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .filter(this::hasPreviewData)
                .findFirst();
        if (waitingPreviewStep.isPresent()) {
            PlanStep step = waitingPreviewStep.get();
            return toWorkspacePreview(step.getStepId(), step.getPreviewData());
        }

        return task.getArtifacts().stream()
                .filter(this::isPreviewArtifact)
                .filter(artifact -> artifact.getPreviewData() != null)
                .findFirst()
                .map(artifact -> toWorkspacePreview(artifact.getStepId(), artifact.getPreviewData()))
                .orElse(TaskWorkspaceView.Preview.builder()
                        .available(false)
                        .build());
    }

    private TaskWorkspaceView.Preview toWorkspacePreview(String stepId, JsonNode previewData) {
        if (previewData == null || previewData.isNull() || previewData.isMissingNode()) {
            return TaskWorkspaceView.Preview.builder().available(false).build();
        }
        String artifactType = previewData.path("artifactType").asText("");
        String type = "PRESENTATION".equalsIgnoreCase(artifactType) ? "slides"
                : "DOCUMENT".equalsIgnoreCase(artifactType) ? "doc"
                : artifactType.toLowerCase();
        return TaskWorkspaceView.Preview.builder()
                .available(true)
                .type(type)
                .title(previewData.path("title").asText(""))
                .theme(previewData.path("theme").asText(null))
                .stepId(stepId)
                .data(previewData)
                .build();
    }

    private TaskWorkspaceView.Output toWorkspaceOutput(Artifact artifact) {
        return TaskWorkspaceView.Output.builder()
                .type(displayOutputType(artifact.getType()))
                .title(artifact.getTitle())
                .url(artifact.getUrl())
                .stepId(artifact.getStepId())
                .build();
    }

    private TaskWorkspaceView.Adjustments toWorkspaceAdjustments(AgentTask task, TaskWorkspaceView.Preview preview) {
        Optional<PlanStep> waitingStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .filter(this::hasPreviewData)
                .findFirst();
        if (waitingStep.isEmpty() || preview == null || !preview.isAvailable()) {
            return TaskWorkspaceView.Adjustments.builder()
                    .available(false)
                    .actions(List.of())
                    .build();
        }

        PlanStep step = waitingStep.get();
        String endpoint = "/api/v1/tasks/" + task.getTaskId() + "/steps/" + step.getStepId() + "/preview";
        List<TaskWorkspaceView.AdjustmentAction> actions = "slides".equals(preview.getType())
                ? List.of(
                adjustment("theme", "切换主题", "select", "preview.data.theme",
                        List.of("business", "tech", "campaign", "minimal"), preview.getTheme(), endpoint),
                adjustment("title", "修改标题", "text", "preview.data.title",
                        List.of(), preview.getTitle(), endpoint),
                adjustment("slides", "编辑页面结构", "structured", "preview.data.slides",
                        List.of(), null, endpoint),
                refineAction(task.getTaskId(), step.getStepId()))
                : List.of(
                adjustment("title", "修改标题", "text", "preview.data.title",
                        List.of(), preview.getTitle(), endpoint),
                adjustment("sections", "编辑文档结构", "structured", "preview.data.sections",
                        List.of(), null, endpoint),
                adjustment("rawMarkdown", "编辑正文 Markdown", "markdown", "preview.data.rawMarkdown",
                        List.of(), null, endpoint),
                refineAction(task.getTaskId(), step.getStepId()));

        return TaskWorkspaceView.Adjustments.builder()
                .available(true)
                .stepId(step.getStepId())
                .phase("confirm2")
                .actions(actions)
                .build();
    }

    private TaskWorkspaceView.AdjustmentAction adjustment(String key,
                                                          String label,
                                                          String type,
                                                          String target,
                                                          List<String> options,
                                                          String currentValue,
                                                          String endpoint) {
        return TaskWorkspaceView.AdjustmentAction.builder()
                .key(key)
                .label(label)
                .type(type)
                .target(target)
                .options(options)
                .currentValue(currentValue)
                .endpoint(endpoint)
                .method("PUT")
                .build();
    }

    private TaskWorkspaceView.AdjustmentAction refineAction(String taskId, String stepId) {
        return TaskWorkspaceView.AdjustmentAction.builder()
                .key("naturalLanguageRefine")
                .label("自然语言精修")
                .type("instruction")
                .target("preview.data")
                .options(List.of())
                .endpoint("/api/v1/tasks/" + taskId + "/steps/" + stepId + "/preview/refine")
                .method("POST")
                .build();
    }

    private TaskWorkspaceView.TimelineEvent toWorkspaceTimelineEvent(TaskEvent event) {
        String type = event.getType() == null ? "TASK_EVENT" : event.getType();
        return TaskWorkspaceView.TimelineEvent.builder()
                .timestamp(event.getTimestamp())
                .type(type)
                .title(eventTitle(type))
                .message(event.getMessage())
                .level(eventLevel(type))
                .stepId(event.getMetadata() == null ? null : event.getMetadata().get("stepId"))
                .metadata(event.getMetadata() == null ? Map.of() : event.getMetadata())
                .build();
    }

    private String resolveTaskTitle(AgentTask task) {
        return task.getArtifacts().stream()
                .map(Artifact::getPreviewData)
                .filter(previewData -> previewData != null && !previewData.isNull() && !previewData.isMissingNode())
                .map(previewData -> previewData.path("title").asText(""))
                .filter(title -> !title.isBlank())
                .findFirst()
                .or(() -> task.getArtifacts().stream()
                        .map(Artifact::getTitle)
                        .filter(title -> title != null && !title.isBlank())
                        .findFirst())
                .or(() -> task.getPlanSteps().stream()
                        .map(PlanStep::getAction)
                        .filter(action -> action != null && !action.isBlank())
                        .findFirst()
                        .map(this::summarizeTitle))
                .orElseGet(() -> summarizeTitle(task.getInputText()));
    }

    private String summarizeTitle(String text) {
        if (text == null || text.isBlank()) {
            return "Agent 任务";
        }
        String cleaned = text
                .replaceFirst("^用户明确需求[:：]", "")
                .replaceFirst("^IM 补充信息[:：]", "")
                .replaceAll("@_user_\\d+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "Agent 任务";
        }
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32) + "...";
    }

    private TaskCardView.StepProgress toStepProgress(PlanStep step) {
        return TaskCardView.StepProgress.builder()
                .stepId(step.getStepId())
                .action(step.getAction())
                .status(step.getStatus() == null ? StepStatus.PENDING.name() : step.getStatus().name())
                .displayStatus(displayStatus(step.getStatus()))
                .requiresConfirm(step.isRequiresConfirm())
                .build();
    }

    private boolean isActiveStep(AgentTask task, PlanStep step) {
        if (step.getStatus() == StepStatus.RUNNING || step.getStatus() == StepStatus.WAIT_CONFIRM) {
            return true;
        }
        String nextAction = task.getNextAction();
        return nextAction != null && nextAction.endsWith(":" + step.getStepId());
    }

    private boolean hasPreviewData(PlanStep step) {
        return step.getPreviewData() != null && !step.getPreviewData().isNull() && !step.getPreviewData().isMissingNode();
    }

    private TaskCardView.ConfirmCard toConfirmCard(AgentTask task) {
        Optional<PlanStep> waitingStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst();
        if (waitingStep.isEmpty()) {
            return TaskCardView.ConfirmCard.builder().waiting(false).build();
        }

        PlanStep step = waitingStep.get();
        JsonNode previewData = step.getPreviewData();
        return TaskCardView.ConfirmCard.builder()
                .waiting(true)
                .stepId(step.getStepId())
                .phase(previewData == null || previewData.isMissingNode() ? "confirm1" : "confirm2")
                .action(step.getAction())
                .artifactType(readArtifactType(step))
                .recommendedTheme(previewData == null ? null : previewData.path("theme").asText(null))
                .previewData(previewData)
                .build();
    }

    private TaskCardView.CompletionCard toCompletionCard(AgentTask task) {
        boolean finished = task.getStatus() == TaskStatus.DELIVERED;
        boolean failed = task.getStatus() == TaskStatus.FAILED;
        List<TaskCardView.LinkItem> links = task.getArtifacts().stream()
                .filter(artifact -> artifact.getUrl() != null && !artifact.getUrl().startsWith("preview://"))
                .map(this::toLinkItem)
                .toList();

        return TaskCardView.CompletionCard.builder()
                .finished(finished)
                .failed(failed)
                .links(links)
                .message(failed ? "任务执行失败，请查看事件列表" : finished ? "任务已完成" : "任务进行中")
                .build();
    }

    private TaskCardView.LinkItem toLinkItem(Artifact artifact) {
        return TaskCardView.LinkItem.builder()
                .type(artifact.getType())
                .title(artifact.getTitle())
                .url(artifact.getUrl())
                .build();
    }

    private String displayStatus(StepStatus status) {
        if (status == null || status == StepStatus.PENDING) {
            return "待完成";
        }
        if (status == StepStatus.RUNNING) {
            return "完成中";
        }
        if (status == StepStatus.DONE) {
            return "已完成";
        }
        if (status == StepStatus.WAIT_CONFIRM) {
            return "等待确认";
        }
        if (status == StepStatus.FAILED) {
            return "失败";
        }
        if (status == StepStatus.APPROVED) {
            return "已确认";
        }
        if (status == StepStatus.SKIPPED) {
            return "已跳过";
        }
        return status.name();
    }

    private String displayTaskStatus(AgentTask task) {
        if (isCancelled(task)) {
            return "已取消";
        }
        TaskStatus status = task == null ? null : task.getStatus();
        if (status == TaskStatus.CREATED) {
            return "已创建";
        }
        if (status == TaskStatus.PLANNED) {
            return "已规划";
        }
        if (status == TaskStatus.WAIT_CONFIRM) {
            return "等待确认";
        }
        if (status == TaskStatus.RUNNING) {
            return "执行中";
        }
        if (status == TaskStatus.DELIVERED) {
            return "已完成";
        }
        if (status == TaskStatus.FAILED) {
            return "失败";
        }
        return status == null ? "未知" : status.name();
    }

    private boolean isCancelled(AgentTask task) {
        return task != null
                && task.getEvents() != null
                && task.getEvents().stream()
                .anyMatch(event -> "TASK_CANCELLED".equals(event.getType()));
    }

    private String eventTitle(String type) {
        return switch (type) {
            case "TASK_CREATED" -> "任务已创建";
            case "TASK_PLANNED" -> "计划已生成";
            case "STEP_WAIT_CONFIRM" -> "等待用户确认";
            case "STEP_APPROVED" -> "步骤已确认";
            case "STEP_REJECTED" -> "步骤已拒绝";
            case "STEP_RUNNING" -> "步骤执行中";
            case "STEP_DONE", "STEP_COMPLETED" -> "步骤已完成";
            case "STEP_PREVIEW_READY" -> "预览已生成";
            case "STEP_PREVIEW_UPDATED" -> "预览已更新";
            case "STEP_PREVIEW_REFINED" -> "预览已精修";
            case "STEP_PREVIEW_FAILED" -> "预览生成失败";
            case "TASK_DELIVERED", "TASK_COMPLETED" -> "任务已完成";
            case "TASK_FAILED" -> "任务失败";
            case "TASK_CANCELLED" -> "任务已取消";
            case "IM_SUPPLEMENT_RECEIVED" -> "收到补充信息";
            default -> type;
        };
    }

    private String eventLevel(String type) {
        if (type == null) {
            return "info";
        }
        if (type.contains("FAILED") || type.contains("REJECTED")) {
            return "error";
        }
        if (type.contains("WAIT_CONFIRM") || type.contains("PREVIEW_READY")) {
            return "warning";
        }
        if (type.contains("DONE") || type.contains("COMPLETED") || type.contains("DELIVERED") || type.contains("APPROVED")) {
            return "success";
        }
        return "info";
    }

    private String readArtifactType(PlanStep step) {
        if (step.getPreviewData() != null && step.getPreviewData().hasNonNull("artifactType")) {
            return displayArtifactType(step.getPreviewData().path("artifactType").asText());
        }
        if ("D_SLIDES".equals(step.getStepId())) {
            return "PPT";
        }
        if ("C_DOC".equals(step.getStepId())) {
            return "文档";
        }
        return null;
    }

    private String displayArtifactType(String artifactType) {
        if ("PRESENTATION".equalsIgnoreCase(artifactType) || "slides-preview".equalsIgnoreCase(artifactType)) {
            return "PPT";
        }
        if ("DOCUMENT".equalsIgnoreCase(artifactType) || "docs-preview".equalsIgnoreCase(artifactType)) {
            return "文档";
        }
        return artifactType;
    }

    private String displayOutputType(String artifactType) {
        if ("doc".equalsIgnoreCase(artifactType) || "document".equalsIgnoreCase(artifactType)) {
            return "doc";
        }
        if ("slides".equalsIgnoreCase(artifactType) || "ppt".equalsIgnoreCase(artifactType)
                || "presentation".equalsIgnoreCase(artifactType)) {
            return "slides";
        }
        return artifactType;
    }

    private boolean isPreviewArtifact(Artifact artifact) {
        return artifact.getType() != null && artifact.getType().endsWith("-preview");
    }
}
