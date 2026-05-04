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
                .artifacts(task.getArtifacts().stream()
                        .filter(this::isPublicDeliveryArtifact)
                        .toList())
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
        TaskWorkspaceView.Progress progress = toWorkspaceProgress(task, preview);
        List<TaskWorkspaceView.Output> outputs = task.getArtifacts().stream()
                .filter(this::isPublicDeliveryArtifact)
                .map(this::toWorkspaceOutput)
                .toList();
        return TaskWorkspaceView.builder()
                .taskId(task.getTaskId())
                .title(title)
                .status(task.getStatus().name())
                .displayStatus(displayTaskStatus(task))
                .nextAction(task.getNextAction())
                .source(task.getSource())
                .sourceDisplay(displayTaskSource(task.getSource()))
                .userId(task.getUserId())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .inputSummary(summarizeTitle(task.getInputText()))
                .contextText(task.getInputText())
                .steps(task.getPlanSteps().stream().map(step -> toWorkspaceStep(task, step)).toList())
                .progress(progress)
                .confirmation(toWorkspaceConfirmation(task, preview))
                .preview(preview)
                .adjustments(toWorkspaceAdjustments(task, preview))
                .outputs(outputs)
                .timeline(task.getEvents().stream().map(this::toWorkspaceTimelineEvent).toList())
                .sync(toWorkspaceSync(task))
                .diagnostics(toWorkspaceDiagnostics(task, preview, progress, outputs))
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
                .displayStatus(displayWorkspaceStepStatus(step))
                .phase(workspaceStepPhase(step))
                .requiresConfirm(step.isRequiresConfirm())
                .active(isActiveStep(task, step))
                .build();
    }

    private TaskWorkspaceView.Progress toWorkspaceProgress(AgentTask task, TaskWorkspaceView.Preview preview) {
        if (isCancelled(task)) {
            return progress(100, 6, "cancelled", "已取消");
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            return progress(100, 6, "failed", "失败");
        }
        if (task.getStatus() == TaskStatus.DELIVERED) {
            return progress(100, 6, "delivered", "已完成");
        }

        Optional<PlanStep> waitingStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst();
        if (waitingStep.isPresent()) {
            PlanStep step = waitingStep.get();
            boolean laterArtifactStage = hasCompletedArtifactWorkBefore(task, step);
            if (hasPreviewData(step)) {
                if (laterArtifactStage) {
                    return progress(85, 5, "confirm2", "后续预览待确认");
                }
                return progress(60, 4, "confirm2", "预览待确认");
            }
            if (laterArtifactStage) {
                return progress(70, 5, "confirm1", "后续产物需求待确认");
            }
            return progress(40, 3, "confirm1", "需求待确认");
        }

        boolean hasApprovedPreviewStep = task.getPlanSteps().stream()
                .anyMatch(step -> step.getStatus() == StepStatus.APPROVED && !hasPreviewData(step));
        if (hasApprovedPreviewStep) {
            boolean laterArtifactStage = task.getPlanSteps().stream()
                    .filter(step -> step.getStatus() == StepStatus.APPROVED && !hasPreviewData(step))
                    .findFirst()
                    .map(step -> hasCompletedArtifactWorkBefore(task, step))
                    .orElse(false);
            if (laterArtifactStage) {
                return progress(75, 5, "preview_generating", "正在生成后续预览");
            }
            return progress(50, 3, "preview_generating", "正在生成预览");
        }

        boolean hasRunningStep = task.getPlanSteps().stream()
                .anyMatch(step -> step.getStatus() == StepStatus.RUNNING);
        if (hasRunningStep || task.getStatus() == TaskStatus.RUNNING) {
            int percent = preview != null && preview.isAvailable() ? 80 : 25;
            int phaseIndex = preview != null && preview.isAvailable() ? 5 : 2;
            return progress(percent, phaseIndex, "running", "执行中");
        }

        if (task.getStatus() == TaskStatus.PLANNED || !task.getPlanSteps().isEmpty()) {
            return progress(25, 2, "planned", "计划已生成");
        }
        if (task.getStatus() == TaskStatus.CREATED) {
            return progress(10, 1, "created", "任务已创建");
        }
        return progress(0, 0, "unknown", "等待同步");
    }

    private boolean hasCompletedArtifactWorkBefore(AgentTask task, PlanStep currentStep) {
        if (task == null || currentStep == null) {
            return false;
        }
        int currentIndex = task.getPlanSteps().indexOf(currentStep);
        if (currentIndex <= 0) {
            return false;
        }
        for (int i = 0; i < currentIndex; i++) {
            PlanStep previousStep = task.getPlanSteps().get(i);
            if (previousStep.getStatus() == StepStatus.DONE || hasPreviewData(previousStep)) {
                return true;
            }
            String previousStepId = previousStep.getStepId();
            boolean hasArtifact = task.getArtifacts().stream()
                    .anyMatch(artifact -> previousStepId != null && previousStepId.equals(artifact.getStepId()));
            if (hasArtifact) {
                return true;
            }
        }
        return false;
    }

    private TaskWorkspaceView.Progress progress(int percent, int phaseIndex, String phase, String label) {
        return TaskWorkspaceView.Progress.builder()
                .percent(percent)
                .phaseIndex(phaseIndex)
                .totalPhases(6)
                .phase(phase)
                .label(label)
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
        String endpoint = "/api/v1/tasks/" + task.getTaskId() + "/workspace/steps/" + step.getStepId() + "/preview";
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
                .endpoint("/api/v1/tasks/" + taskId + "/workspace/steps/" + stepId + "/preview/refine")
                .method("POST")
                .build();
    }

    private TaskWorkspaceView.TimelineEvent toWorkspaceTimelineEvent(TaskEvent event) {
        String type = event.getType() == null ? "TASK_EVENT" : event.getType();
        Map<String, String> metadata = event.getMetadata() == null ? Map.of() : event.getMetadata();
        String source = metadata.getOrDefault("source", inferEventSource(type));
        return TaskWorkspaceView.TimelineEvent.builder()
                .timestamp(event.getTimestamp())
                .type(type)
                .title(eventTitle(type))
                .message(event.getMessage())
                .level(eventLevel(type))
                .stepId(metadata.get("stepId"))
                .source(source)
                .sourceDisplay(displayEventSource(source))
                .metadata(metadata)
                .build();
    }

    private TaskWorkspaceView.Sync toWorkspaceSync(AgentTask task) {
        String taskId = task.getTaskId();
        return TaskWorkspaceView.Sync.builder()
                .realtimeEnabled(true)
                .snapshotEndpoint("/api/v1/tasks/" + taskId + "/workspace")
                .streamEndpoint("/api/v1/tasks/" + taskId + "/workspace/stream")
                .lastEventId(task.getUpdatedAt() == null ? "" : task.getUpdatedAt().toString())
                .serverTime(java.time.Instant.now())
                .reconnectAfterMillis(3000)
                .build();
    }

    private TaskWorkspaceView.Diagnostics toWorkspaceDiagnostics(AgentTask task,
                                                                 TaskWorkspaceView.Preview preview,
                                                                 TaskWorkspaceView.Progress progress,
                                                                 List<TaskWorkspaceView.Output> outputs) {
        JsonNode previewData = preview == null ? null : preview.getData();
        List<String> previewWarnings = readStringArray(previewData == null ? null : previewData.path("warnings"));
        Integer slidePageCount = previewData != null && "PRESENTATION".equalsIgnoreCase(previewData.path("artifactType").asText(""))
                ? previewData.path("pageCount").asInt(0)
                : null;
        int hiddenInternalArtifactCount = (int) task.getArtifacts().stream()
                .filter(artifact -> !isPublicDeliveryArtifact(artifact))
                .count();
        return TaskWorkspaceView.Diagnostics.builder()
                .progressSource("backend_authoritative")
                .progressPercent(progress.getPercent())
                .progressPhase(progress.getPhase())
                .publicOutputCount(outputs == null ? 0 : outputs.size())
                .hiddenInternalArtifactCount(hiddenInternalArtifactCount)
                .previewWarningCount(previewWarnings.size())
                .previewWarnings(previewWarnings)
                .slidePageCount(slidePageCount)
                .shouldUseBackendProgress(true)
                .build();
    }

    private List<String> readStringArray(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        java.util.ArrayList<String> values = new java.util.ArrayList<>();
        for (JsonNode item : node) {
            String value = item.asText("");
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
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
        String source = extractSection(text, "【用户明确需求】")
                .orElseGet(() -> extractSection(text, "【IM后续补充信息】")
                        .orElse(text));
        String cleaned = source
                .replaceFirst("^用户明确需求[:：]", "")
                .replaceFirst("^IM 补充信息[:：]", "")
                .replaceFirst("^补充内容[:：]", "")
                .replaceAll("@_user_\\d+", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "Agent 任务";
        }
        return cleaned.length() <= 32 ? cleaned : cleaned.substring(0, 32) + "...";
    }

    private Optional<String> extractSection(String text, String sectionTitle) {
        int start = text.indexOf(sectionTitle);
        if (start < 0) {
            return Optional.empty();
        }
        int contentStart = start + sectionTitle.length();
        String remaining = text.substring(contentStart);
        int nextSection = remaining.indexOf("【");
        String section = nextSection >= 0 ? remaining.substring(0, nextSection) : remaining;
        String cleaned = section.trim();
        return cleaned.isBlank() ? Optional.empty() : Optional.of(cleaned);
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
                .filter(this::isPublicDeliveryArtifact)
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

    private String displayWorkspaceStepStatus(PlanStep step) {
        if (step != null && step.getStatus() == StepStatus.WAIT_CONFIRM && hasPreviewData(step)) {
            return "预览待确认";
        }
        if (step != null && step.getStatus() == StepStatus.APPROVED && !hasPreviewData(step)) {
            return "需求已确认，生成预览中";
        }
        return displayStatus(step == null ? null : step.getStatus());
    }

    private String workspaceStepPhase(PlanStep step) {
        if (step == null) {
            return "unknown";
        }
        if (step.getStatus() == StepStatus.WAIT_CONFIRM && hasPreviewData(step)) {
            return "confirm2";
        }
        if (step.getStatus() == StepStatus.WAIT_CONFIRM) {
            return "confirm1";
        }
        if (step.getStatus() == StepStatus.APPROVED && !hasPreviewData(step)) {
            return "preview_generating";
        }
        if (step.getStatus() == StepStatus.APPROVED) {
            return "confirmed";
        }
        return step.getStatus() == null ? "pending" : step.getStatus().name().toLowerCase();
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

    private String displayTaskSource(String source) {
        if (source == null || source.isBlank()) {
            return "未知入口";
        }
        if (source.startsWith("IM:group:")) {
            return "飞书群聊";
        }
        if (source.startsWith("IM:p2p:")) {
            return "飞书单聊";
        }
        if (source.startsWith("IM:")) {
            return "飞书 IM";
        }
        if ("workspace".equalsIgnoreCase(source)) {
            return "工作台";
        }
        return source;
    }

    private String inferEventSource(String type) {
        if (type == null) {
            return "agent";
        }
        if (type.startsWith("TASK_") || type.startsWith("STEP_RUNNING") || type.startsWith("STEP_DONE")
                || type.startsWith("STEP_WAIT_CONFIRM") || type.startsWith("STEP_PREVIEW_READY")) {
            return "agent";
        }
        if (type.startsWith("STEP_PREVIEW_UPDATED") || type.startsWith("STEP_PREVIEW_REFINED")) {
            return "workspace";
        }
        if (type.startsWith("IM_")) {
            return "im";
        }
        return "user";
    }

    private String displayEventSource(String source) {
        if ("workspace".equalsIgnoreCase(source)) {
            return "工作台";
        }
        if ("lark_card".equalsIgnoreCase(source)) {
            return "飞书卡片";
        }
        if ("im".equalsIgnoreCase(source)) {
            return "飞书 IM";
        }
        if ("agent".equalsIgnoreCase(source)) {
            return "Agent";
        }
        if ("user".equalsIgnoreCase(source)) {
            return "用户";
        }
        return source == null || source.isBlank() ? "未知来源" : source;
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
        if ("doc".equalsIgnoreCase(artifactType) || "docs".equalsIgnoreCase(artifactType)
                || "document".equalsIgnoreCase(artifactType)) {
            return "doc";
        }
        if ("slides".equalsIgnoreCase(artifactType) || "ppt".equalsIgnoreCase(artifactType)
                || "presentation".equalsIgnoreCase(artifactType)) {
            return "slides";
        }
        if ("delivery".equalsIgnoreCase(artifactType)) {
            return "workspace";
        }
        return artifactType;
    }

    private boolean isPreviewArtifact(Artifact artifact) {
        return artifact.getType() != null && artifact.getType().endsWith("-preview");
    }

    private boolean isPublicDeliveryArtifact(Artifact artifact) {
        return artifact != null
                && artifact.getUrl() != null
                && !artifact.getUrl().startsWith("preview://")
                && !isPreviewArtifact(artifact)
                && !"delivery".equalsIgnoreCase(artifact.getType());
    }
}
