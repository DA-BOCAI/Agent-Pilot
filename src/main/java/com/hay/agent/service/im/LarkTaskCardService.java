package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.ModelFailureClassifier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkTaskCardService {

    public static final String PLAN_CARD_ID = "AAqeNySKUje95";
    public static final String CONFIRM_CARD_ID = "AAqepmaEOpkaI";
    public static final String COMPLETION_CARD_ID = "AAqeT5Z887bUh";
    static final String PLAN_CARD_KEY = "plan";
    static final String CONFIRM_CARD_KEY = "confirm";
    static final String COMPLETION_CARD_KEY = "completion";

    private final ObjectMapper objectMapper;
    private final AgentTaskService agentTaskService;

    @Value("${agent.im.lark-cli-command:lark-cli}")
    private String larkCliCommand;

    @Value("${agent.im.lark-cli-run-js:C:\\Users\\Swiftie\\AppData\\Roaming\\npm\\node_modules\\@larksuite\\cli\\scripts\\run.js}")
    private String larkCliRunJs;

    @Value("${agent.im.cards.enabled:true}")
    private boolean cardsEnabled;

    @Value("${agent.im.workspace-url:https://agent-pilot-nine.vercel.app}")
    private String workspaceUrl;

    @Value("${agent.tool.lark-cli-timeout:180s}")
    private Duration larkCliTimeout;

    public void sendCardsForCurrentState(String chatId, AgentTask task) {
        if (!cardsEnabled || chatId == null || chatId.isBlank() || task == null) {
            return;
        }

        if (!task.getPlanSteps().isEmpty()) {
            sendOrUpdateCard(chatId, task, PLAN_CARD_KEY, buildPlanCard(task), "任务计划卡片");
        }

        if (task.getStatus() == TaskStatus.WAIT_CONFIRM) {
            sendNewTrackedCard(chatId, task, CONFIRM_CARD_KEY, buildConfirmCard(task), "发送任务确认卡片");
        }

        if (task.getStatus() == TaskStatus.DELIVERED || task.getStatus() == TaskStatus.FAILED) {
            sendOrUpdateCard(chatId, task, COMPLETION_CARD_KEY, buildCompletionCard(task), "任务完成卡片");
        }
    }

    public void sendFollowUpCardsForCurrentState(String chatId, AgentTask task) {
        if (!cardsEnabled || chatId == null || chatId.isBlank() || task == null) {
            return;
        }

        if (!task.getPlanSteps().isEmpty()) {
            sendOrUpdateCard(chatId, task, PLAN_CARD_KEY, buildPlanCard(task), "任务计划卡片");
        }

        if (task.getStatus() == TaskStatus.WAIT_CONFIRM) {
            sendNewTrackedCard(chatId, task, CONFIRM_CARD_KEY, buildConfirmCard(task), "发送任务确认卡片");
            return;
        }

        if (task.getStatus() == TaskStatus.DELIVERED || task.getStatus() == TaskStatus.FAILED) {
            sendOrUpdateCard(chatId, task, COMPLETION_CARD_KEY, buildCompletionCard(task), "任务完成卡片");
        }
    }

    public void updateConfirmCardResolved(String chatId, AgentTask task, PlanStep step, boolean approved) {
        if (!cardsEnabled || task == null) {
            return;
        }
        String messageId = task.getCardMessageIds().get(CONFIRM_CARD_KEY);
        if (messageId == null || messageId.isBlank()) {
            if (chatId != null && !chatId.isBlank()) {
                sendNewTrackedCard(chatId, task, CONFIRM_CARD_KEY, buildResolvedConfirmCard(task, step, approved),
                        approved ? "发送确认结果卡片" : "发送取消结果卡片");
            }
            return;
        }
        boolean updated = updateInteractiveCard(messageId, buildResolvedConfirmCard(task, step, approved),
                approved ? "更新确认卡片为已确认" : "更新确认卡片为已取消");
        if (!updated && chatId != null && !chatId.isBlank()) {
            sendNewTrackedCard(chatId, task, CONFIRM_CARD_KEY, buildResolvedConfirmCard(task, step, approved),
                    approved ? "发送确认结果卡片" : "发送取消结果卡片");
        }
    }

    ObjectNode buildPlanCard(AgentTask task) {
        ObjectNode card = baseCard("blue", "Agent 任务编排中...", true);
        ArrayNode elements = objectMapper.createArrayNode();
        elements.add(markdown("**📋 执行计划**"));
        PlanStep currentStep = currentWaitingStep(task).orElse(null);
        elements.add(markdown("**当前阶段**：" + stageLabel(task, currentStep)));
        elements.add(markdown("**Agent 下一步**：" + agentNextAction(task, currentStep)));
        elements.add(markdown("**你可以做**：" + userActionHint(task, currentStep)));
        elements.add(markdown("**执行路径**：" + planPathSummary(task)));
        List<PlanStep> steps = task.getPlanSteps();
        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            elements.add(markdown("**" + (i + 1) + ". " + statusIcon(step.getStatus()) + " "
                    + displayStatus(step.getStatus()) + "**｜" + step.getAction()));
        }
        if (hasWaitingConfirm1(task)) {
            elements.add(markdown("**提示**：当前处于需求确认阶段，可以先在 IM 里补充要求；确认后会生成结构化预览，再进入工作台精修。"));
        }
        if (task.getStatus() == TaskStatus.DELIVERED || task.getStatus() == TaskStatus.FAILED) {
            elements.add(buttonRow(button("前往工作台", "primary_filled", "open_workspace", task)));
        } else {
            elements.add(buttonRow(
                    button("🚀 前往工作台", "primary_filled", "open_workspace", task),
                    button("❌ 取消任务", "default", "cancel", task)
            ));
        }

        card.set("body", objectMapper.createObjectNode()
                .put("direction", "vertical")
                .set("elements", elements));
        return card;
    }

    ObjectNode buildConfirmCard(AgentTask task) {
        PlanStep step = task.getPlanSteps().stream()
                .filter(item -> item.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst()
                .orElse(null);

        ObjectNode card = baseCard("orange", "等待确认", false);
        card.set("body", objectMapper.createObjectNode()
                .put("direction", "vertical")
                .set("elements", confirmElements(task, step)));
        return card;
    }

    ObjectNode buildCompletionCard(AgentTask task) {
        boolean cancelled = isCancelled(task);
        boolean failed = task.getStatus() == TaskStatus.FAILED && !cancelled;
        ObjectNode card = baseCard(failed ? "red" : cancelled ? "grey" : "green",
                failed ? "任务失败" : cancelled ? "任务已取消" : "任务已完成~",
                true);

        ArrayNode elements = objectMapper.createArrayNode();
        addCompletionElements(elements, task);

        Optional<Artifact> primaryOutput = primaryOutputArtifact(task);
        if (primaryOutput.isPresent()) {
            elements.add(buttonRow(
                    button("前往工作台", "primary_filled", "open_workspace", task),
                    urlButton(outputButtonLabel(primaryOutput.get()), "default", primaryOutput.get().getUrl())
            ));
        } else {
            elements.add(buttonRow(button("前往工作台", "primary_filled", "open_workspace", task)));
        }

        card.set("body", objectMapper.createObjectNode()
                .put("direction", "vertical")
                .set("elements", elements));
        return card;
    }

    ObjectNode buildResolvedConfirmCard(AgentTask task, PlanStep step, boolean approved) {
        boolean confirm2 = hasPreviewData(step);
        ObjectNode card = baseCard(approved ? "green" : "grey",
                approved ? confirm2 ? "预览已确认" : "需求已确认" : "已取消",
                false);
        ArrayNode elements = objectMapper.createArrayNode();
        if (approved) {
            elements.add(markdown(confirm2 ? "**confirm2 已确认：预览通过**" : "**confirm1 已确认：需求理解通过**"));
        } else {
            elements.add(markdown(confirm2 ? "**confirm2 已取消：停止创建飞书产物**" : "**confirm1 已取消：停止生成预览**"));
        }
        if (step != null) {
            elements.add(markdown("**步骤**：" + step.getAction()));
        }
        if (approved) {
            elements.add(markdown(confirm2
                    ? "Agent 将正式创建飞书产物，请关注计划卡片和完成卡片。"
                    : "Agent 将先生成结构化预览，随后进入第二次确认。"));
        } else {
            elements.add(markdown("任务已停止推进，可进入工作台查看详情。"));
        }
        elements.add(buttonRow(button("查看详情", "default", "open_workspace", task, step)));
        card.set("body", objectMapper.createObjectNode()
                .put("direction", "vertical")
                .set("elements", elements));
        return card;
    }

    private ObjectNode baseCard(String template, String title, boolean withIcon) {
        ObjectNode card = objectMapper.createObjectNode();
        card.put("schema", "2.0");
        card.set("config", objectMapper.createObjectNode().put("update_multi", true));

        ObjectNode header = objectMapper.createObjectNode();
        header.set("title", objectMapper.createObjectNode()
                .put("tag", "plain_text")
                .put("content", title));
        header.set("subtitle", objectMapper.createObjectNode()
                .put("tag", "plain_text")
                .put("content", ""));
        header.put("template", template);
        header.put("padding", "12px");
        if (withIcon) {
            header.set("icon", objectMapper.createObjectNode()
                    .put("tag", "standard_icon")
                    .put("token", "robot_outlined"));
        }
        card.set("header", header);
        return card;
    }

    private ObjectNode markdown(String content) {
        return objectMapper.createObjectNode()
                .put("tag", "markdown")
                .put("content", normalizeCardText(content))
                .put("margin", "0px");
    }

    private ObjectNode buttonRow(ObjectNode... buttons) {
        ArrayNode columns = objectMapper.createArrayNode();
        for (ObjectNode button : buttons) {
            columns.add(objectMapper.createObjectNode()
                    .put("tag", "column")
                    .put("width", "auto")
                    .put("vertical_spacing", "8px")
                    .put("horizontal_align", "left")
                    .put("vertical_align", "top")
                    .set("elements", objectMapper.createArrayNode().add(button)));
        }

        return objectMapper.createObjectNode()
                .put("tag", "column_set")
                .put("flex_mode", "stretch")
                .put("horizontal_spacing", "8px")
                .put("horizontal_align", "left")
                .put("margin", "0px")
                .set("columns", columns);
    }

    private ObjectNode button(String text, String type, String action, AgentTask task) {
        return button(text, type, action, task, null);
    }

    private ObjectNode button(String text, String type, String action, AgentTask task, PlanStep step) {
        ObjectNode button = objectMapper.createObjectNode()
                .put("tag", "button")
                .put("type", type)
                .put("width", "fill")
                .put("margin", "4px");
        button.set("text", objectMapper.createObjectNode()
                .put("tag", "plain_text")
                .put("content", normalizeCardText(text)));

        ArrayNode behaviors = objectMapper.createArrayNode();
        behaviors.add(objectMapper.createObjectNode()
                .put("type", "callback")
                .set("value", callbackValue(action, task, step)));
        if ("open_workspace".equals(action)) {
            behaviors.add(openWorkspaceBehavior(task));
        }
        button.set("behaviors", behaviors);
        return button;
    }

    private ObjectNode urlButton(String text, String type, String url) {
        ObjectNode button = objectMapper.createObjectNode()
                .put("tag", "button")
                .put("type", type)
                .put("width", "default")
                .put("size", "medium")
                .put("margin", "4px");
        button.set("text", objectMapper.createObjectNode()
                .put("tag", "plain_text")
                .put("content", normalizeCardText(text)));
        button.set("behaviors", objectMapper.createArrayNode().add(openUrlBehavior(url)));
        return button;
    }

    private ObjectNode callbackValue(String action, AgentTask task, PlanStep step) {
        ObjectNode value = objectMapper.createObjectNode()
                .put("action", action)
                .put("taskId", task.getTaskId());
        if (step != null) {
            value.put("stepId", step.getStepId());
            value.put("confirmStage", hasPreviewData(step) ? "confirm2" : "confirm1");
        }
        return value;
    }

    private ObjectNode openWorkspaceBehavior(AgentTask task) {
        return openUrlBehavior(workspaceUrl + "?taskId=" + task.getTaskId());
    }

    private ObjectNode openUrlBehavior(String url) {
        return objectMapper.createObjectNode()
                .put("type", "open_url")
                .put("default_url", url == null ? workspaceUrl : url)
                .put("pc_url", "")
                .put("ios_url", "")
                .put("android_url", "");
    }

    private ArrayNode confirmElements(AgentTask task, PlanStep step) {
        ArrayNode elements = objectMapper.createArrayNode();
        if (step == null) {
            elements.add(markdown("当前任务正在等待确认，请前往工作台查看详情。"));
            elements.add(buttonRow(
                    button("确认", "primary_filled", "confirm", task),
                    button("取消", "primary", "reject", task),
                    button("查看详情", "default", "open_workspace", task)
            ));
            return elements;
        }

        if (hasPreviewData(step)) {
            addConfirm2Elements(elements, step);
        } else {
            addConfirm1Elements(elements, task, step);
        }
        addPilotGuidance(elements, task, step);
        elements.add(buttonRow(
                button("确认", "primary_filled", "confirm", task, step),
                button("取消", "primary", "reject", task, step),
                button("查看详情", "default", "open_workspace", task, step)
        ));
        return elements;
    }

    private void addConfirm1Elements(ArrayNode elements, AgentTask task, PlanStep step) {
        elements.add(markdown("**confirm1：请核对 Agent 的需求理解是否正确**"));
        elements.add(markdown("**你的需求**：" + summarizeUserRequirement(task)));
        elements.add(markdown("**Agent 理解**：生成一份 **" + artifactDisplayName(step) + "**，内容方向是「" + summarizePlannedContent(step) + "」。"));
        elements.add(markdown("**请重点核对**：产物类型、主题/对象、使用场景、必须包含的内容是否正确。"));
        elements.add(markdown("如果理解有误或信息不完整，请先在当前会话补充说明，再点击确认；如果产物类型不对，请取消后重新发起任务。"));
        elements.add(markdown("确认后，Agent 只会先生成结构化预览，下一步仍需要你确认后才会正式创建到飞书。"));
    }

    private void addConfirm2Elements(ArrayNode elements, PlanStep step) {
        JsonNode previewData = step.getPreviewData();
        String theme = previewData == null ? "" : previewData.path("theme").asText("");
        String title = previewData == null ? "" : previewData.path("title").asText("");

        elements.add(markdown("**confirm2：预览已生成，请确认是否正式创建飞书产物**"));
        if (!title.isBlank()) {
            elements.add(markdown("**预览标题**：" + title));
        }
        elements.add(markdown("**产物类型**：" + artifactDisplayName(step)));
        if (!theme.isBlank()) {
            elements.add(markdown("**当前主题**：" + theme));
        }
        elements.add(markdown("你可以进入工作台查看预览并精修；确认后，Agent 将正式创建飞书产物。"));
    }

    private void addCompletionElements(ArrayNode elements, AgentTask task) {
        if (isCancelled(task)) {
            elements.add(markdown("任务已按你的选择取消，Agent 不会继续创建或交付飞书产物。"));
            return;
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            String failureMessage = ModelFailureClassifier.latestUserMessage(task)
                    .orElse("任务执行失败，请进入工作台查看失败原因和事件时间线。");
            elements.add(markdown(failureMessage));
            latestRetryAdvice(task).ifPresent(advice -> elements.add(markdown(advice)));
            return;
        }

        elements.add(markdown("**你的计划已完成**，可以进入工作台查看正式产物。"));
        elements.add(markdown("**闭环结果**：" + deliveryCoverageSummary(task)));
        elements.add(markdown("**演示亮点**：自然语言触发、双确认、工作台精修、多端状态同步和正式交付已串成完整 Agent 流程。"));
        List<Artifact> outputs = task.getArtifacts().stream()
                .filter(artifact -> artifact.getUrl() != null && !artifact.getUrl().startsWith("preview://"))
                .filter(artifact -> !"delivery".equalsIgnoreCase(artifact.getType()))
                .toList();
        if (!outputs.isEmpty()) {
            elements.add(markdown("**产出内容**"));
            for (Artifact artifact : outputs) {
                elements.add(markdown("- **" + artifact.getTitle() + "**"));
            }
        }
    }

    private String deliveryCoverageSummary(AgentTask task) {
        boolean hasDoc = hasOutputType(task, "doc", "docs", "document")
                || hasPlannedStep(task, "C_DOC");
        boolean hasSlides = hasOutputType(task, "slides", "ppt", "presentation")
                || hasPlannedStep(task, "D_SLIDES");
        if (hasDoc && hasSlides) {
            return "已完成 IM 触发、文档沉淀、PPT 生成和飞书链接交付。";
        }
        if (hasSlides) {
            return "已完成 IM 触发、PPT 生成和飞书链接交付。";
        }
        if (hasDoc) {
            return "已完成 IM 触发、文档生成和飞书链接交付。";
        }
        return "已完成 IM 触发、Agent 执行和飞书交付。";
    }

    private boolean hasOutputType(AgentTask task, String... types) {
        if (task == null || task.getArtifacts() == null) {
            return false;
        }
        return task.getArtifacts().stream()
                .filter(artifact -> artifact.getUrl() != null && !artifact.getUrl().startsWith("preview://"))
                .anyMatch(artifact -> containsAnyIgnoreCase(artifact.getType(), types));
    }

    private boolean hasPlannedStep(AgentTask task, String stepId) {
        return task != null
                && task.getPlanSteps() != null
                && task.getPlanSteps().stream().anyMatch(step -> stepId.equals(step.getStepId()));
    }

    private void addPilotGuidance(ArrayNode elements, AgentTask task, PlanStep step) {
        elements.add(markdown("**当前阶段**：" + stageLabel(task, step)));
        elements.add(markdown("**Agent 下一步**：" + agentNextAction(task, step)));
        elements.add(markdown("**你可以做**：" + userActionHint(task, step)));
    }

    private String stageLabel(AgentTask task, PlanStep step) {
        if (task == null) {
            return "等待任务状态同步";
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            return isCancelled(task) ? "任务已取消" : "任务失败，等待处理";
        }
        if (task.getStatus() == TaskStatus.DELIVERED) {
            return "已交付";
        }
        if (task.getStatus() == TaskStatus.RUNNING) {
            return "Agent 正在执行";
        }
        if (task.getStatus() == TaskStatus.CREATED) {
            return "任务已创建，等待规划";
        }
        if (task.getStatus() == TaskStatus.PLANNED) {
            return "计划已生成，等待推进";
        }
        if (task.getStatus() == TaskStatus.WAIT_CONFIRM && step != null) {
            return hasPreviewData(step) ? "预览确认（confirm2）" : "需求理解确认（confirm1）";
        }
        if (task.getStatus() == TaskStatus.WAIT_CONFIRM) {
            return "等待确认";
        }
        return task.getStatus().name();
    }

    private String agentNextAction(AgentTask task, PlanStep step) {
        if (task == null) {
            return "等待任务信息同步后继续判断。";
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            return isCancelled(task) ? "任务已停止，不会继续创建产物。" : "保留失败原因，并等待用户重试或调整输入。";
        }
        if (task.getStatus() == TaskStatus.DELIVERED) {
            return "展示正式交付链接，供用户进入工作台或飞书查看。";
        }
        if (task.getStatus() == TaskStatus.RUNNING || task.getStatus() == TaskStatus.PLANNED) {
            return "继续执行下一个可自动完成的步骤，遇到风险动作会再次请求确认。";
        }
        if (task.getStatus() == TaskStatus.CREATED) {
            return "理解需求并拆解为可执行的文档/PPT编排计划。";
        }
        if (task.getStatus() == TaskStatus.WAIT_CONFIRM && step != null) {
            if (hasPreviewData(step)) {
                return "在确认通过后，使用当前预览内容正式创建飞书产物。";
            }
            return "在确认通过后，先生成结构化预览，再进入工作台精修。";
        }
        return "根据当前任务状态继续推进。";
    }

    private String userActionHint(AgentTask task, PlanStep step) {
        if (task == null) {
            return "稍后刷新工作台或在 IM 中询问进度。";
        }
        if (task.getStatus() == TaskStatus.FAILED) {
            return isCancelled(task) ? "可以重新发起任务。" : "可以在 IM 中发送“重试”，或减少内容规模后重新发起。";
        }
        if (task.getStatus() == TaskStatus.DELIVERED) {
            return "打开工作台或正式飞书链接查看交付物。";
        }
        if (task.getStatus() == TaskStatus.WAIT_CONFIRM && step != null) {
            if (hasPreviewData(step)) {
                return "进入工作台检查预览、精修内容，确认后再正式交付。";
            }
            return "核对 Agent 理解是否正确；如需调整，可先在 IM 里补充说明。";
        }
        return "可以进入工作台查看实时进度，也可以在 IM 中询问“进度怎么样”。";
    }

    private String planPathSummary(AgentTask task) {
        if (task == null || task.getPlanSteps() == null || task.getPlanSteps().isEmpty()) {
            return "等待 Agent 生成执行计划。";
        }
        List<String> actions = task.getPlanSteps().stream()
                .map(PlanStep::getAction)
                .filter(action -> action != null && !action.isBlank())
                .map(this::compactAction)
                .limit(4)
                .toList();
        if (actions.isEmpty()) {
            return "按任务规划逐步推进。";
        }
        String suffix = task.getPlanSteps().size() > actions.size() ? " → ..." : "";
        return String.join(" → ", actions) + suffix;
    }

    private String compactAction(String action) {
        String cleaned = action.replace('\n', ' ').replace('\r', ' ').trim();
        return cleaned.length() <= 18 ? cleaned : cleaned.substring(0, 18) + "...";
    }

    private String statusIcon(StepStatus status) {
        if (status == StepStatus.DONE) {
            return "✅";
        }
        if (status == StepStatus.RUNNING) {
            return "🔄";
        }
        if (status == StepStatus.WAIT_CONFIRM) {
            return "⏸";
        }
        if (status == StepStatus.FAILED) {
            return "❌";
        }
        return "⬜";
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
        if (status == StepStatus.APPROVED) {
            return "已确认";
        }
        if (status == StepStatus.SKIPPED) {
            return "已取消";
        }
        if (status == StepStatus.FAILED) {
            return "失败";
        }
        return status.name();
    }

    private String readArtifactType(PlanStep step) {
        JsonNode previewData = step.getPreviewData();
        if (previewData != null && previewData.hasNonNull("artifactType")) {
            return displayArtifactType(previewData.path("artifactType").asText());
        }
        if ("D_SLIDES".equals(step.getStepId())) {
            return "PPT";
        }
        if ("C_DOC".equals(step.getStepId())) {
            return "文档";
        }
        return null;
    }

    private boolean hasPreviewData(PlanStep step) {
        return step.getPreviewData() != null && !step.getPreviewData().isNull() && !step.getPreviewData().isMissingNode();
    }

    private Optional<PlanStep> currentWaitingStep(AgentTask task) {
        if (task == null || task.getPlanSteps() == null) {
            return Optional.empty();
        }
        return task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst();
    }

    private boolean hasWaitingConfirm1(AgentTask task) {
        return task != null
                && task.getStatus() == TaskStatus.WAIT_CONFIRM
                && task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .filter(PlanStep::isRequiresConfirm)
                .anyMatch(step -> !hasPreviewData(step));
    }

    private String artifactDisplayName(PlanStep step) {
        String artifactType = readArtifactType(step);
        if (artifactType != null) {
            return artifactType;
        }
        return "待生成内容";
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

    private String summarizeUserRequirement(AgentTask task) {
        String inputText = task == null ? "" : task.getInputText();
        String source = extractSection(inputText, "【用户明确需求】")
                .or(() -> extractSection(inputText, "【IM后续补充信息】"))
                .orElse(inputText == null ? "" : inputText);
        String cleaned = source
                .replaceFirst("^用户明确需求[:：]", "")
                .replaceFirst("^IM 补充信息[:：]", "")
                .replaceFirst("^补充内容[:：]", "")
                .replaceAll("@_user_\\d+", "")
                .replaceAll("(?m)^【触发用户】.*$", "")
                .replaceAll("(?m)^【触发消息ID】.*$", "")
                .replaceAll("(?m)^消息ID[:：].*$", "")
                .replaceAll("(?m)^原任务ID[:：].*$", "")
                .replaceAll("\\b(?:ou|oc|om|msg|req)_[A-Za-z0-9_-]{6,}\\b", "")
                .replaceAll("\\s+", " ")
                .trim();
        if (cleaned.isBlank()) {
            return "未识别到明确文本需求，请在当前会话补充目标、对象和期望产物。";
        }
        return cleaned.length() <= 100 ? cleaned : cleaned.substring(0, 100) + "...";
    }

    private Optional<String> extractSection(String text, String sectionTitle) {
        if (text == null || text.isBlank()) {
            return Optional.empty();
        }
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

    private String summarizePlannedContent(PlanStep step) {
        String action = step == null ? "" : step.getAction();
        if (action == null || action.isBlank()) {
            return "根据当前对话上下文生成办公产物";
        }
        String normalized = action
                .replaceFirst("^(创建|生成|撰写|制作|输出|整理)", "")
                .replace('\n', ' ')
                .replace('\r', ' ')
                .trim();
        if (normalized.isBlank()) {
            return action;
        }
        return normalized.length() <= 80 ? normalized : normalized.substring(0, 80) + "...";
    }

    private Optional<Artifact> primaryOutputArtifact(AgentTask task) {
        List<Artifact> outputs = task.getArtifacts().stream()
                .filter(artifact -> artifact.getUrl() != null && !artifact.getUrl().startsWith("preview://"))
                .filter(artifact -> !"delivery".equals(artifact.getType()))
                .toList();
        return outputs.stream()
                .filter(this::isSlidesArtifact)
                .findFirst()
                .or(() -> outputs.stream().findFirst());
    }

    private String outputButtonLabel(Artifact artifact) {
        if (isSlidesArtifact(artifact)) {
            return "查看 PPT";
        }
        if (artifact != null && artifact.getType() != null
                && ("docs".equalsIgnoreCase(artifact.getType()) || "doc".equalsIgnoreCase(artifact.getType()))) {
            return "查看云文档";
        }
        return "查看交付物";
    }

    private boolean isSlidesArtifact(Artifact artifact) {
        return artifact != null
                && artifact.getType() != null
                && ("slides".equalsIgnoreCase(artifact.getType())
                || "ppt".equalsIgnoreCase(artifact.getType())
                || "presentation".equalsIgnoreCase(artifact.getType()));
    }

    private boolean containsAnyIgnoreCase(String value, String... candidates) {
        if (value == null || candidates == null) {
            return false;
        }
        for (String candidate : candidates) {
            if (candidate != null && value.equalsIgnoreCase(candidate)) {
                return true;
            }
        }
        return false;
    }

    private boolean isCancelled(AgentTask task) {
        return task != null
                && task.getEvents() != null
                && task.getEvents().stream()
                .map(TaskEvent::getType)
                .anyMatch("TASK_CANCELLED"::equals);
    }

    private Optional<String> latestRetryAdvice(AgentTask task) {
        if (task == null || task.getEvents() == null) {
            return Optional.empty();
        }
        for (int i = task.getEvents().size() - 1; i >= 0; i--) {
            TaskEvent event = task.getEvents().get(i);
            if (event == null || event.getMetadata() == null) {
                continue;
            }
            String advice = event.getMetadata().get("retryAdvice");
            if (advice != null && !advice.isBlank()) {
                return Optional.of(advice);
            }
        }
        return Optional.empty();
    }

    private void sendOrUpdateCard(String chatId, AgentTask task, String cardKey, ObjectNode card, String actionName) {
        String messageId = task.getCardMessageIds().get(cardKey);
        if (messageId != null && !messageId.isBlank()
                && updateInteractiveCard(messageId, card, "更新" + actionName)) {
            return;
        }
        sendNewTrackedCard(chatId, task, cardKey, card, "发送" + actionName);
    }

    private void sendNewTrackedCard(String chatId, AgentTask task, String cardKey, ObjectNode card, String actionName) {
        sendInteractiveCard(chatId, card, actionName)
                .ifPresent(messageId -> {
                    task.getCardMessageIds().put(cardKey, messageId);
                    agentTaskService.recordCardMessageId(task.getTaskId(), cardKey, messageId);
                });
    }

    private Optional<String> sendInteractiveCard(String chatId, ObjectNode card, String actionName) {
        try {
            String cardJson = objectMapper.writeValueAsString(card);
            String paramsJson = escapeCliJsonArgument(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("receive_id_type", "chat_id")));
            String dataJson = escapeCliJsonArgument(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("receive_id", chatId)
                    .put("msg_type", "interactive")
                    .put("content", cardJson)));
            Process process = new ProcessBuilder(
                    "node",
                    larkCliRunJs,
                    "api",
                    "POST",
                    "/open-apis/im/v1/messages",
                    "--params", paramsJson,
                    "--data", dataJson,
                    "--as", "bot"
            ).redirectErrorStream(true).start();
            process.getOutputStream().close();

            boolean finished = process.waitFor(larkCliTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                throw new IllegalStateException(actionName + "超时");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(actionName + "失败：" + output);
            }
            log.info("{}成功，chatId={}，输出={}", actionName, chatId, output);
            return readMessageId(output);
        } catch (Exception e) {
            log.error("{}失败，chatId={}", actionName, chatId, e);
            return Optional.empty();
        }
    }

    private boolean updateInteractiveCard(String messageId, ObjectNode card, String actionName) {
        try {
            String cardJson = objectMapper.writeValueAsString(card);
            String dataJson = escapeCliJsonArgument(objectMapper.writeValueAsString(objectMapper.createObjectNode()
                    .put("content", cardJson)));
            Process process = new ProcessBuilder(
                    "node",
                    larkCliRunJs,
                    "api",
                    "PATCH",
                    "/open-apis/im/v1/messages/" + messageId,
                    "--data", dataJson,
                    "--as", "bot"
            ).redirectErrorStream(true).start();
            process.getOutputStream().close();

            boolean finished = process.waitFor(larkCliTimeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                destroyProcessTree(process);
                throw new IllegalStateException(actionName + "超时");
            }

            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                throw new IllegalStateException(actionName + "失败：" + output);
            }
            log.info("{}成功，messageId={}，输出={}", actionName, messageId, output);
            return true;
        } catch (Exception e) {
            log.warn("{}失败，将降级为发送新卡片，messageId={}", actionName, messageId, e);
            return false;
        }
    }

    private Optional<String> readMessageId(String output) {
        try {
            JsonNode root = objectMapper.readTree(output);
            String messageId = root.at("/data/message_id").asText("");
            if (messageId.isBlank()) {
                messageId = root.at("/data/message_id_list/0").asText("");
            }
            return messageId.isBlank() ? Optional.empty() : Optional.of(messageId);
        } catch (Exception e) {
            log.warn("解析飞书消息ID失败，输出={}", output);
            return Optional.empty();
        }
    }

    private String escapeCliJsonArgument(String value) {
        return value == null ? "" : value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"");
    }

    private String normalizeCardText(String value) {
        if (value == null) {
            return "";
        }
        return value.replace(' ', '\u00A0')
                .replace('\t', '\u00A0')
                .replace("\r\n", " ")
                .replace('\n', ' ')
                .replace('\r', ' ');
    }

    private void destroyProcessTree(Process process) {
        process.descendants().forEach(child -> {
            if (child.isAlive()) {
                child.destroyForcibly();
            }
        });
        process.destroyForcibly();
    }
}
