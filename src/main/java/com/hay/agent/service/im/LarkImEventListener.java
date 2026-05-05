package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.mapper.TaskMapper;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.ModelFailureClassifier;
import com.hay.agent.api.dto.TaskWorkspaceView;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.im.listener.enabled", havingValue = "true")
public class LarkImEventListener {

    private static final AtomicBoolean LISTENER_RUNNING = new AtomicBoolean(false);
    private static final Duration MESSAGE_DEDUP_TTL = Duration.ofHours(2);
    private static final Duration TEXT_DEDUP_TTL = Duration.ofMinutes(10);
    private final AgentTaskService agentTaskService;
    private final AgentRunner agentRunner;
    private final LarkTaskCardService larkTaskCardService;
    private final ImIntentClassifier imIntentClassifier;
    private final TaskMapper taskMapper;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> processedMessageIds = new ConcurrentHashMap<>();
    private final Map<String, Instant> processedTextFingerprints = new ConcurrentHashMap<>();
    private final Map<String, Deque<ChatMessageContext>> recentChatMessages = new ConcurrentHashMap<>();
    private final Map<String, String> activeTaskIdsByChat = new ConcurrentHashMap<>();
    private final Map<String, String> latestTaskIdsByChat = new ConcurrentHashMap<>();
    private final Map<String, PendingClarification> pendingClarificationsByChat = new ConcurrentHashMap<>();

    @Value("${agent.im.lark-cli-command:lark-cli}")
    private String larkCliCommand;

    @Value("${agent.im.bot-open-id:}")
    private String botOpenId;

    @Value("${agent.im.bot-user-id:}")
    private String botUserId;

    @Value("${agent.im.group-requires-mention:true}")
    private boolean groupRequiresMention;

    @Value("${agent.im.reply.enabled:true}")
    private boolean replyEnabled;

    @Value("${agent.im.context.ttl:30m}")
    private Duration discussionContextTtl;

    @Value("${agent.im.context.max-messages:8}")
    private int maxDiscussionContextMessages;

    @Value("${agent.im.workspace-url:https://agent-pilot-nine.vercel.app}")
    private String workspaceUrl;

    private volatile Process subscribeProcess;

    @PostConstruct
    public void startListening() {
        if (!LISTENER_RUNNING.compareAndSet(false, true)) {
            log.warn("飞书 IM 消息监听器已经在当前 JVM 中启动，本次跳过，避免重复占用事件连接");
            return;
        }
        Thread listenerThread = new Thread(this::listen, "lark-im-listener");
        listenerThread.setDaemon(true);
        listenerThread.start();
        log.info("飞书 IM 消息监听服务已启动");
    }

    @PreDestroy
    public void stopListening() {
        destroySubscribeProcess();
        LISTENER_RUNNING.set(false);
        log.info("飞书 IM 消息监听服务已停止");
    }

    private void listen() {
        try {
            subscribeProcess = new ProcessBuilder(
                    larkCliCommand,
                    "event",
                    "+subscribe",
                    "--event-types", "im.message.receive_v1",
                    "--as", "bot",
                    "--force"
            ).redirectErrorStream(true).start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(subscribeProcess.getInputStream(), StandardCharsets.UTF_8))) {
                readEventStream(reader);
            }
        } catch (Exception e) {
            log.error("飞书消息监听服务启动失败", e);
        } finally {
            destroySubscribeProcess();
            LISTENER_RUNNING.set(false);
        }
    }

    private void readEventStream(BufferedReader reader) throws Exception {
        String line;
        StringBuilder rawBuffer = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            String normalizedLine = stripAnsi(line).trim();
            if (normalizedLine.isEmpty()) {
                continue;
            }

            if (isConnectionLimitLine(normalizedLine)) {
                log.warn("飞书事件监听失败：连接数已超限。请关闭其他 lark-cli event +subscribe 进程或其他后端实例后，再重新启动监听");
                return;
            }

            if (isNoiseLine(normalizedLine)) {
                continue;
            }

            rawBuffer.append(normalizedLine).append(System.lineSeparator());
            JsonNode eventJson = tryParseJson(rawBuffer.toString());
            if (eventJson == null) {
                continue;
            }
            rawBuffer.setLength(0);
            handleEvent(eventJson);
        }
    }

    void handleEvent(JsonNode eventJson) {
        try {
            String eventType = eventJson.at("/header/event_type").asText();
            if (!"im.message.receive_v1".equals(eventType)) {
                return;
            }

            JsonNode message = eventJson.at("/event/message");
            String messageId = message.path("message_id").asText("");
            if (messageId.isBlank() || isDuplicateMessage(messageId)) {
                log.debug("忽略重复或缺少 message_id 的飞书消息，messageId={}", messageId);
                return;
            }

            String messageType = message.path("message_type").asText("text");
            if (!"text".equals(messageType)) {
                log.info("忽略非文本飞书消息，messageId={}，messageType={}", messageId, messageType);
                return;
            }

            String chatId = message.path("chat_id").asText("");
            String chatType = message.path("chat_type").asText("");
            String userId = resolveSenderId(eventJson, chatId);
            JsonNode contentJson = objectMapper.readTree(message.path("content").asText("{}"));
            String rawText = contentJson.path("text").asText("");
            String text = normalizeUserText(rawText);
            recordDiscussionContext(chatId, messageId, userId, text);

            if (!shouldTriggerInChat(chatId, chatType, message, rawText, userId)) {
                log.debug("忽略未触发机器人的群聊消息，chatId={}，messageId={}", chatId, messageId);
                return;
            }

            if (text.isBlank()) {
                log.debug("忽略空文本飞书消息，messageId={}", messageId);
                return;
            }

            if (isDuplicateText(chatId, userId, text)) {
                log.info("忽略同一聊天内短时间重复的飞书文本消息，chatId={}，messageId={}", chatId, messageId);
                return;
            }

            ImIntentClassification classification = classifyIntent(chatId, text);
            if (classification.getType() == ImIntentType.IGNORE
                    && handlePendingClarificationIfNeeded(chatId, chatType, messageId, userId, text)) {
                return;
            }
            if (classification.getType() == ImIntentType.IGNORE) {
                log.info("忽略未识别为任务或补充的 IM 消息，chatId={}，messageId={}，reason={}，text={}",
                        chatId, messageId, classification.getReason(), abbreviate(text, 80));
                return;
            }

            if (classification.getType() == ImIntentType.CANCEL_TASK
                    && handleCancelTask(chatId, messageId, classification)) {
                return;
            }

            if (classification.getType() == ImIntentType.HELP_QUERY) {
                replyToMessage(chatId, messageId, buildHelpReply());
                log.info("已回复 IM 帮助说明，chatId={}，messageId={}，reason={}",
                        chatId, messageId, classification.getReason());
                return;
            }

            if (classification.getType() == ImIntentType.CLARIFICATION_NEEDED) {
                pendingClarificationsByChat.put(chatId, new PendingClarification(text, messageId, userId, Instant.now()));
                replyToMessage(chatId, messageId, buildClarificationReply(text));
                log.info("已回复 IM 主动澄清，chatId={}，messageId={}，reason={}，text={}",
                        chatId, messageId, classification.getReason(), abbreviate(text, 80));
                return;
            }

            if (classification.getType() == ImIntentType.PROGRESS_QUERY
                    && handleProgressQuery(chatId, messageId, classification)) {
                return;
            }

            if (classification.getType() == ImIntentType.RETRY_TASK
                    && handleRetryTask(chatId, chatType, messageId, userId, classification)) {
                return;
            }

            if (classification.getType() == ImIntentType.SUPPLEMENT
                    && handleSupplementIfNeeded(chatId, messageId, text, classification)) {
                return;
            }

            String taskText = mergePendingClarification(chatId, userId, text);
            String taskInput = buildStructuredTaskInput(chatId, chatType, messageId, userId, taskText);
            AgentTask task = createAndRunTask(chatId, chatType, messageId, userId, taskInput);
            replyToMessage(chatId, messageId, buildTaskCreatedReply(task, taskText));
        } catch (Exception e) {
            log.debug("忽略非事件 JSON 或解析不完整内容：{}", eventJson, e);
        }
    }

    private AgentTask createAndRunTask(String chatId, String chatType, String messageId, String userId, String text) {
        CreateTaskRequest request = new CreateTaskRequest();
        request.setInputText(text);
        request.setSource(buildTaskSource(chatId, chatType));
        request.setUserId(userId);
        request.setRequestId(messageId);

        AgentTask task = agentTaskService.createTask(request);
        CompletableFuture.runAsync(() -> {
            try {
                AgentTask advancedTask = agentRunner.runUntilBlocked(task.getTaskId());
                if (advancedTask.getStatus() == com.hay.agent.domain.TaskStatus.DELIVERED
                        || advancedTask.getStatus() == com.hay.agent.domain.TaskStatus.FAILED) {
                    activeTaskIdsByChat.remove(chatId, advancedTask.getTaskId());
                }
                larkTaskCardService.sendCardsForCurrentState(chatId, advancedTask);
            } catch (Exception e) {
                log.error("飞书 IM 任务自动推进失败，尝试发送最新失败卡片，taskId={}，chatId={}", task.getTaskId(), chatId, e);
                sendLatestCardsAfterRunFailure(chatId, task.getTaskId());
            }
        });
        activeTaskIdsByChat.put(chatId, task.getTaskId());
        latestTaskIdsByChat.put(chatId, task.getTaskId());
        log.info("飞书 IM 消息已创建任务，taskId={}，chatId={}，chatType={}", task.getTaskId(), chatId, chatType);
        return task;
    }

    private void sendLatestCardsAfterRunFailure(String chatId, String taskId) {
        try {
            AgentTask latestTask = agentTaskService.getTask(taskId);
            if (latestTask.getStatus() == TaskStatus.DELIVERED || latestTask.getStatus() == TaskStatus.FAILED) {
                activeTaskIdsByChat.remove(chatId, taskId);
            }
            latestTaskIdsByChat.put(chatId, taskId);
            larkTaskCardService.sendCardsForCurrentState(chatId, latestTask);
        } catch (Exception ignored) {
            log.warn("飞书 IM 任务推进异常后发送最新卡片失败，taskId={}，chatId={}", taskId, chatId);
        }
    }

    private ImIntentClassification classifyIntent(String chatId, String text) {
        cleanupPendingClarifications();
        String activeTaskId = activeTaskIdsByChat.get(chatId);
        boolean hasActiveTask = activeTaskId != null && !activeTaskId.isBlank();
        ImIntentClassification classification = imIntentClassifier.classify(text, hasActiveTask);
        log.info("IM 意图分类结果：chatId={}，activeTask={}，type={}，confidence={}，reason={}",
                chatId, hasActiveTask, classification.getType(), classification.getConfidence(), classification.getReason());
        return classification;
    }

    private boolean handlePendingClarificationIfNeeded(String chatId,
                                                       String chatType,
                                                       String messageId,
                                                       String userId,
                                                       String text) {
        PendingClarification pending = pendingClarificationsByChat.get(chatId);
        if (pending == null || pending.isExpired(discussionContextTtl) || !sameUser(pending.userId(), userId)) {
            pendingClarificationsByChat.remove(chatId);
            return false;
        }
        if (text == null || text.isBlank() || isAcknowledgementLike(text)) {
            return false;
        }
        String taskText = mergePendingClarification(chatId, userId, text);
        String taskInput = buildStructuredTaskInput(chatId, chatType, messageId, userId, taskText);
        AgentTask task = createAndRunTask(chatId, chatType, messageId, userId, taskInput);
        replyToMessage(chatId, messageId, buildTaskCreatedReply(task, taskText));
        log.info("已将 IM 澄清回复合并为新任务，chatId={}，taskId={}，originalMessageId={}，clarifyMessageId={}",
                chatId, task.getTaskId(), pending.messageId(), messageId);
        return true;
    }

    private String mergePendingClarification(String chatId, String userId, String text) {
        PendingClarification pending = pendingClarificationsByChat.get(chatId);
        if (pending == null || pending.isExpired(discussionContextTtl) || !sameUser(pending.userId(), userId)) {
            return text;
        }
        pendingClarificationsByChat.remove(chatId);
        return "【原始模糊需求】\n" + pending.originalText()
                + "\n\n【用户澄清补充】\n" + text;
    }

    private void cleanupPendingClarifications() {
        Iterator<Map.Entry<String, PendingClarification>> iterator = pendingClarificationsByChat.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, PendingClarification> entry = iterator.next();
            if (entry.getValue().isExpired(discussionContextTtl)) {
                iterator.remove();
            }
        }
    }

    private boolean sameUser(String pendingUserId, String currentUserId) {
        if (pendingUserId == null || pendingUserId.isBlank()) {
            return true;
        }
        return pendingUserId.equals(currentUserId);
    }

    private boolean isAcknowledgementLike(String text) {
        String value = text == null ? "" : text.replaceAll("\\s+", "").toLowerCase();
        return value.length() <= 8 && containsAny(value, "好", "好的", "收到", "明白", "谢谢", "ok");
    }

    private boolean handleCancelTask(String chatId,
                                     String messageId,
                                     ImIntentClassification classification) {
        String activeTaskId = activeTaskIdsByChat.get(chatId);
        if (activeTaskId == null || activeTaskId.isBlank()) {
            replyToMessage(chatId, messageId,
                    "当前聊天里没有正在进行的 Agent 任务，无需取消。\n"
                            + "如果需要开始新的任务，可以直接发送需求。");
            log.info("IM 取消任务请求无活跃任务，chatId={}，messageId={}，reason={}",
                    chatId, messageId, classification.getReason());
            return true;
        }

        try {
            AgentTask cancelledTask = agentTaskService.cancelTask(activeTaskId,
                    "用户通过 IM 消息取消任务",
                    "im",
                    messageId);
            activeTaskIdsByChat.remove(chatId, activeTaskId);
            latestTaskIdsByChat.put(chatId, activeTaskId);
            larkTaskCardService.sendCardsForCurrentState(chatId, cancelledTask);
            replyToMessage(chatId, messageId, buildCancelReply(cancelledTask));
            log.info("IM 已取消当前任务，chatId={}，taskId={}，messageId={}，reason={}",
                    chatId, activeTaskId, messageId, classification.getReason());
            return true;
        } catch (Exception e) {
            activeTaskIdsByChat.remove(chatId, activeTaskId);
            replyToMessage(chatId, messageId,
                    "取消任务时没有找到当前任务状态，可能任务已结束或缓存已过期。");
            log.warn("IM 取消任务失败，chatId={}，taskId={}", chatId, activeTaskId, e);
            return true;
        }
    }

    String buildCancelReply(AgentTask task) {
        String taskId = task == null ? "" : task.getTaskId();
        return "已停止当前 Agent 任务。\n"
                + "任务ID：" + taskId + "\n"
                + "后续如需重新生成，可以直接发送新的需求。";
    }

    String buildHelpReply() {
        return "我可以从 IM 里接收需求，并生成可确认、可预览、可交付的文档或 PPT。\n"
                + "常用说法：\n"
                + "1. 帮我生成一份项目复盘文档和汇报 PPT\n"
                + "2. 补充一下，重点强调风险和下一步计划\n"
                + "3. 现在进度到哪了\n"
                + "4. 取消当前任务\n"
                + "5. 如果任务因为大模型限流失败，可以说：重试\n"
                + "我会先给出确认卡片，预览生成后可以进入工作台精修，再确认交付。";
    }

    String buildClarificationReply(String text) {
        return "我可以做，但这个需求还缺少关键信息，先帮我补充一下：\n"
                + "1. 主题或项目名称是什么？\n"
                + "2. 面向谁汇报或使用？\n"
                + "3. 必须包含哪些内容，例如背景、目标、进展、风险、下一步？\n"
                + "你可以直接回复一句完整需求，例如：帮我生成一份面向管理层的项目复盘 PPT，包含背景、进展、风险和下一步计划。";
    }

    private boolean handleProgressQuery(String chatId,
                                        String messageId,
                                        ImIntentClassification classification) {
        String activeTaskId = activeTaskIdsByChat.get(chatId);
        String taskId = activeTaskId == null || activeTaskId.isBlank()
                ? latestTaskIdsByChat.get(chatId)
                : activeTaskId;
        if (taskId == null || taskId.isBlank()) {
            replyToMessage(chatId, messageId,
                    "当前聊天里没有可查询的 Agent 任务。\n"
                            + "你可以直接发起需求，例如：帮我生成一份项目汇报PPT。");
            log.info("IM 进度查询无活跃任务，chatId={}，messageId={}，reason={}",
                    chatId, messageId, classification.getReason());
            return true;
        }

        try {
            AgentTask task = agentTaskService.getTask(taskId);
            TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);
            replyToMessage(chatId, messageId, buildProgressReply(workspace));
            if (task.getStatus() == TaskStatus.DELIVERED || task.getStatus() == TaskStatus.FAILED) {
                activeTaskIdsByChat.remove(chatId, taskId);
                latestTaskIdsByChat.put(chatId, taskId);
            }
            log.info("已回复 IM 任务进度，chatId={}，taskId={}，phase={}，percent={}",
                    chatId, taskId, workspace.getProgress().getPhase(), workspace.getProgress().getPercent());
            return true;
        } catch (Exception e) {
            activeTaskIdsByChat.remove(chatId, taskId);
            replyToMessage(chatId, messageId,
                    "我暂时没有查到当前任务状态，可能任务已结束或缓存已过期。\n"
                            + "可以重新发送需求，我会重新创建任务。");
            log.warn("IM 进度查询读取任务失败，chatId={}，taskId={}", chatId, taskId, e);
            return true;
        }
    }

    private boolean handleRetryTask(String chatId,
                                    String chatType,
                                    String messageId,
                                    String userId,
                                    ImIntentClassification classification) {
        String taskId = latestTaskIdsByChat.get(chatId);
        if (taskId == null || taskId.isBlank()) {
            replyToMessage(chatId, messageId,
                    "我还没有找到可以重试的历史任务。\n"
                            + "你可以直接重新发送需求，我会创建新任务。");
            log.info("IM 重试请求无历史任务，chatId={}，messageId={}，reason={}",
                    chatId, messageId, classification.getReason());
            return true;
        }

        try {
            AgentTask previousTask = agentTaskService.getTask(taskId);
            if (previousTask.getStatus() != TaskStatus.FAILED) {
                replyToMessage(chatId, messageId,
                        "最近任务当前不是失败状态，不需要重试。\n"
                                + "你可以发送“现在进度到哪了”查看状态，或直接发起新的需求。");
                return true;
            }
            String retryInput = buildRetryTaskInput(previousTask, messageId);
            AgentTask retryTask = createAndRunTask(chatId, chatType, messageId, userId, retryInput);
            replyToMessage(chatId, messageId,
                    "已按最近失败任务重新发起。\n"
                            + "原任务ID：" + previousTask.getTaskId() + "\n"
                            + "新任务ID：" + retryTask.getTaskId() + "\n"
                            + "我会重新生成计划和确认卡片。");
            log.info("IM 已重试最近失败任务，chatId={}，previousTaskId={}，retryTaskId={}，messageId={}",
                    chatId, previousTask.getTaskId(), retryTask.getTaskId(), messageId);
            return true;
        } catch (Exception e) {
            latestTaskIdsByChat.remove(chatId, taskId);
            replyToMessage(chatId, messageId,
                    "重试时没有找到最近任务状态，可能缓存已过期。\n"
                            + "你可以直接重新发送需求，我会创建新任务。");
            log.warn("IM 重试最近任务失败，chatId={}，taskId={}", chatId, taskId, e);
            return true;
        }
    }

    String buildProgressReply(TaskWorkspaceView workspace) {
        if (workspace == null) {
            return "当前任务状态暂不可用，请稍后再试。";
        }
        TaskWorkspaceView.Progress progress = workspace.getProgress();
        StringBuilder builder = new StringBuilder();
        builder.append("当前任务进度：")
                .append(progress == null ? 0 : progress.getPercent())
                .append("%");
        if (progress != null && progress.getLabel() != null && !progress.getLabel().isBlank()) {
            builder.append("（").append(progress.getLabel()).append("）");
        }
        builder.append("\n任务：").append(workspace.getTitle() == null ? workspace.getTaskId() : workspace.getTitle());
        if (workspace.getConfirmation() != null && workspace.getConfirmation().isWaiting()) {
            builder.append("\n等待确认：")
                    .append(workspace.getConfirmation().getPhase())
                    .append(" / ")
                    .append(workspace.getConfirmation().getArtifactType() == null
                            ? workspace.getConfirmation().getStepId()
                            : workspace.getConfirmation().getArtifactType());
        }
        if (workspace.getPreview() != null && workspace.getPreview().isAvailable()) {
            builder.append("\n预览：已生成，可进入工作台查看和精修");
        }
        if (workspace.getOutputs() != null && !workspace.getOutputs().isEmpty()) {
            builder.append("\n交付：已生成 ")
                    .append(workspace.getOutputs().size())
                    .append(" 个正式产物");
        }
        if ("FAILED".equalsIgnoreCase(workspace.getStatus())) {
            ModelFailureClassifier.latestUserMessage(agentTaskServiceSafeTask(workspace.getTaskId()))
                    .ifPresent(message -> builder.append("\n失败原因：").append(message));
        }
        builder.append("\n工作台：").append(workspaceUrl).append("?taskId=").append(workspace.getTaskId());
        return builder.toString();
    }

    private AgentTask agentTaskServiceSafeTask(String taskId) {
        try {
            return agentTaskService.getTask(taskId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private boolean handleSupplementIfNeeded(String chatId,
                                             String messageId,
                                             String text,
                                             ImIntentClassification classification) {
        String activeTaskId = activeTaskIdsByChat.get(chatId);
        if (activeTaskId == null || activeTaskId.isBlank()) {
            return false;
        }

        try {
            AgentTask activeTask = agentTaskService.getTask(activeTaskId);
            if (activeTask.getStatus() == TaskStatus.DELIVERED
                    || activeTask.getStatus() == TaskStatus.FAILED) {
                activeTaskIdsByChat.remove(chatId, activeTaskId);
                return false;
            }
            if (!canAcceptImSupplement(activeTask)) {
                log.info("当前任务阶段不接收 IM 补充，chatId={}，taskId={}，messageId={}，status={}，reason={}，text={}",
                        chatId, activeTaskId, messageId, activeTask.getStatus(), classification.getReason(), abbreviate(text, 120));
                replyToMessage(chatId, messageId,
                        "当前阶段暂不接收新的补充信息。\n"
                                + "如果预览已经生成，请进入工作台进行精修；如果需要新增其他产物，请直接发起一个新任务。");
                return true;
            }
            agentTaskService.appendImSupplement(activeTaskId, text, messageId);
            log.info("识别为当前任务的补充信息，chatId={}，taskId={}，messageId={}，reason={}，text={}",
                    chatId, activeTaskId, messageId, classification.getReason(), abbreviate(text, 120));
            replyToMessage(chatId, messageId,
                    "已收到补充信息，我会将它作为当前任务的上下文参考。\n"
                            + "当前任务ID：" + activeTaskId + "\n"
                            + "补充摘要：" + abbreviate(text, 80));
            return true;
        } catch (Exception e) {
            activeTaskIdsByChat.remove(chatId, activeTaskId);
            log.warn("读取当前活跃任务失败，将按新消息继续判断，chatId={}，taskId={}", chatId, activeTaskId);
            return false;
        }
    }

    private boolean canAcceptImSupplement(AgentTask task) {
        if (task == null || task.getStatus() != TaskStatus.WAIT_CONFIRM) {
            return false;
        }
        return task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .filter(PlanStep::isRequiresConfirm)
                .anyMatch(step -> !hasPreviewData(step));
    }

    private boolean hasPreviewData(PlanStep step) {
        JsonNode previewData = step.getPreviewData();
        return previewData != null && !previewData.isNull() && !previewData.isMissingNode();
    }

    private String buildStructuredTaskInput(String chatId,
                                            String chatType,
                                            String messageId,
                                            String userId,
                                            String text) {
        List<ChatMessageContext> contexts = recentDiscussion(chatId, messageId);
        StringBuilder builder = new StringBuilder();
        builder.append("【IM任务输入】\n");
        builder.append("【触发方式】").append("group".equalsIgnoreCase(chatType) ? "群聊@机器人" : "单聊直接触发").append("\n");
        builder.append("【触发用户】").append(userId == null || userId.isBlank() ? "未知用户" : userId).append("\n");
        builder.append("【触发消息ID】").append(messageId == null ? "" : messageId).append("\n\n");

        builder.append("【用户明确需求】\n");
        builder.append(text == null || text.isBlank() ? "未识别到明确文本需求" : text).append("\n\n");

        if (contexts.isEmpty()) {
            builder.append("【最近讨论上下文】\n");
            builder.append("无可用上下文。\n\n");
        } else {
            builder.append("【讨论共识摘要】\n");
            builder.append(summarizeDiscussionConsensus(contexts, text)).append("\n\n");

            builder.append("【最近讨论上下文】\n");
            builder.append("以下内容只用于理解需求背景、约束和偏好，不代表都要原样写入最终产物：\n");
            for (int i = 0; i < contexts.size(); i++) {
                ChatMessageContext context = contexts.get(i);
                builder.append(i + 1)
                        .append(". ")
                        .append(context.senderId())
                        .append("：")
                        .append(context.text())
                        .append("\n");
            }
            builder.append("\n");
        }

        builder.append("【Agent处理要求】\n");
        builder.append("1. 以【用户明确需求】为主，结合【最近讨论上下文】补全背景、目标、约束和偏好。\n");
        builder.append("2. 如果上下文中存在多人讨论，请提炼共识，不要把闲聊或无关内容写入产物。\n");
        builder.append("3. 如果需求表达含糊，先规划可确认的大纲，后续通过 confirm1/confirm2 让用户确认。\n");
        return builder.toString().trim();
    }

    private String summarizeDiscussionConsensus(List<ChatMessageContext> contexts, String currentText) {
        List<String> candidates = new ArrayList<>();
        for (ChatMessageContext context : contexts) {
            String text = context.text();
            if (text == null || text.isBlank()) {
                continue;
            }
            if (containsAny(text, "重点", "目标", "风险", "时间", "面向", "对象", "包含", "需要", "方案", "计划", "亮点", "数据")) {
                candidates.add(abbreviate(text.replaceAll("\\s+", " ").trim(), 60));
            }
        }
        if (candidates.isEmpty()) {
            candidates = contexts.stream()
                    .map(ChatMessageContext::text)
                    .filter(value -> value != null && !value.isBlank())
                    .map(value -> abbreviate(value.replaceAll("\\s+", " ").trim(), 60))
                    .limit(3)
                    .toList();
        }
        if (candidates.isEmpty()) {
            return "当前没有可提炼的明确共识，以用户最新明确需求为准：" + abbreviate(currentText, 80);
        }
        return "结合群聊最近讨论，优先提炼这些共识：" + String.join("；", candidates.stream().limit(4).toList());
    }

    private List<ChatMessageContext> recentDiscussion(String chatId, String currentMessageId) {
        Deque<ChatMessageContext> contexts = recentChatMessages.get(chatId);
        if (contexts == null || contexts.isEmpty()) {
            return List.of();
        }

        Instant expiresBefore = Instant.now().minus(discussionContextTtl);
        List<ChatMessageContext> result = new ArrayList<>();
        for (ChatMessageContext context : contexts) {
            if (context.createdAt().isBefore(expiresBefore) || context.messageId().equals(currentMessageId)) {
                continue;
            }
            result.add(context);
        }
        int limit = Math.max(1, maxDiscussionContextMessages);
        int fromIndex = Math.max(0, result.size() - limit);
        return result.subList(fromIndex, result.size());
    }

    private void recordDiscussionContext(String chatId, String messageId, String userId, String text) {
        if (chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
            return;
        }
        Deque<ChatMessageContext> messages = recentChatMessages.computeIfAbsent(chatId, ignored -> new ArrayDeque<>());
        synchronized (messages) {
            Instant expiresBefore = Instant.now().minus(discussionContextTtl);
            while (!messages.isEmpty() && messages.peekFirst().createdAt().isBefore(expiresBefore)) {
                messages.removeFirst();
            }
            messages.addLast(new ChatMessageContext(messageId, userId, text, Instant.now()));
            int limit = Math.max(1, maxDiscussionContextMessages);
            while (messages.size() > limit + 1) {
                messages.removeFirst();
            }
        }
    }

    private boolean isDuplicateText(String chatId, String userId, String text) {
        cleanupTextFingerprints();
        String fingerprint = fingerprint(chatId + "|" + userId + "|" + normalizeForFingerprint(text));
        return processedTextFingerprints.putIfAbsent(fingerprint, Instant.now()) != null;
    }

    private void cleanupTextFingerprints() {
        Instant expiresBefore = Instant.now().minus(TEXT_DEDUP_TTL);
        Iterator<Map.Entry<String, Instant>> iterator = processedTextFingerprints.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(expiresBefore)) {
                iterator.remove();
            }
        }
    }

    private String normalizeForFingerprint(String text) {
        return text == null ? "" : text
                .replaceAll("\\s+", "")
                .replaceAll("[，。！？!?,.；;：:、]", "")
                .toLowerCase();
    }

    private String fingerprint(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(text.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(text.hashCode());
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private boolean shouldTriggerInChat(String chatId, String chatType, JsonNode message, String rawText, String userId) {
        if (!groupRequiresMention || !"group".equalsIgnoreCase(chatType)) {
            return true;
        }
        String text = normalizeUserText(rawText);
        return mentionsConfiguredBot(message)
                || containsMentionPlaceholder(rawText)
                || hasPendingClarification(chatId, userId)
                || isOperationalFollowUp(chatId, text);
    }

    private boolean isOperationalFollowUp(String chatId, String text) {
        if (chatId == null || chatId.isBlank() || text == null || text.isBlank()) {
            return false;
        }
        boolean hasKnownTask = activeTaskIdsByChat.containsKey(chatId) || latestTaskIdsByChat.containsKey(chatId);
        if (!hasKnownTask) {
            return false;
        }
        String normalized = normalizeForFingerprint(text);
        return containsAny(normalized,
                "进度", "状态", "做到哪", "到哪了", "完成了吗", "好了没", "生成了吗", "现在怎么样",
                "怎么样了", "做完了吗", "出来了吗", "卡住了吗", "还要多久", "多久能好", "有结果了吗",
                "取消任务", "停止任务", "重试", "再试", "重新跑", "progress", "status", "cancel", "stop", "retry");
    }

    private boolean hasPendingClarification(String chatId, String userId) {
        PendingClarification pending = pendingClarificationsByChat.get(chatId);
        return pending != null && !pending.isExpired(discussionContextTtl) && sameUser(pending.userId(), userId);
    }

    private boolean mentionsConfiguredBot(JsonNode message) {
        JsonNode mentions = message.path("mentions");
        if (!mentions.isArray()) {
            return false;
        }

        for (JsonNode mention : mentions) {
            String mentionedOpenId = mention.at("/id/open_id").asText("");
            String mentionedUserId = mention.at("/id/user_id").asText("");
            boolean openIdMatched = !botOpenId.isBlank() && botOpenId.equals(mentionedOpenId);
            boolean userIdMatched = !botUserId.isBlank() && botUserId.equals(mentionedUserId);
            if (openIdMatched || userIdMatched) {
                return true;
            }
        }
        return false;
    }

    private boolean containsMentionPlaceholder(String rawText) {
        return rawText != null && rawText.matches(".*@_user_\\d+.*");
    }

    private boolean isDuplicateMessage(String messageId) {
        cleanupProcessedMessages();
        return processedMessageIds.putIfAbsent(messageId, Instant.now()) != null;
    }

    private void cleanupProcessedMessages() {
        Instant expiresBefore = Instant.now().minus(MESSAGE_DEDUP_TTL);
        Iterator<Map.Entry<String, Instant>> iterator = processedMessageIds.entrySet().iterator();
        while (iterator.hasNext()) {
            if (iterator.next().getValue().isBefore(expiresBefore)) {
                iterator.remove();
            }
        }
    }

    private String buildTaskSource(String chatId, String chatType) {
        String normalizedChatType = chatType == null || chatType.isBlank() ? "unknown" : chatType;
        return "IM:" + normalizedChatType + ":" + chatId;
    }

    private String buildTaskCreatedReply(AgentTask task, String text) {
        return "已收到你的需求，任务已创建。\n"
                + "任务ID：" + task.getTaskId() + "\n"
                + "当前状态：正在生成执行计划\n"
                + "需求摘要：" + abbreviate(text, 80) + "\n"
                + "你可以随时回复“现在进度到哪了”查看状态。";
    }

    private String buildRetryTaskInput(AgentTask previousTask, String messageId) {
        String originalInput = previousTask == null || previousTask.getInputText() == null
                ? ""
                : previousTask.getInputText();
        return originalInput.strip()
                + "\n\n【IM重试信息】\n"
                + "原任务ID：" + (previousTask == null ? "" : previousTask.getTaskId()) + "\n"
                + "重试触发消息ID：" + (messageId == null ? "" : messageId) + "\n"
                + "处理要求：沿用原任务需求重新执行；如果上次失败原因是大模型限流或临时请求异常，本次正常重新生成。";
    }

    private String abbreviate(String text, int maxLength) {
        if (text == null || text.length() <= maxLength) {
            return text == null ? "" : text;
        }
        return text.substring(0, maxLength) + "...";
    }

    private String normalizeUserText(String rawText) {
        return rawText == null ? "" : rawText
                .replaceAll("@_user_\\d+\\s*", "")
                .replace('\u00A0', ' ')
                .trim();
    }

    private record ChatMessageContext(String messageId, String senderId, String text, Instant createdAt) {
    }

    private record PendingClarification(String originalText, String messageId, String userId, Instant createdAt) {
        private boolean isExpired(Duration ttl) {
            Duration effectiveTtl = ttl == null ? Duration.ofMinutes(30) : ttl;
            return createdAt == null || createdAt.isBefore(Instant.now().minus(effectiveTtl));
        }
    }

    private boolean isConnectionLimitLine(String line) {
        return line.contains("the number of connections exceeded the limit");
    }

    private boolean isNoiseLine(String line) {
        return line.startsWith("Active code page:")
                || line.startsWith("Connecting to")
                || line.startsWith("Listening for")
                || line.startsWith("Connected.")
                || line.startsWith("[SDK Info]")
                || line.startsWith("[SDK Error]")
                || line.contains("code page")
                || line.contains("lark-cli 1.0.19 available")
                || line.startsWith("\u001B[");
    }

    private JsonNode tryParseJson(String rawText) {
        String cleaned = stripAnsi(rawText).trim();
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        String candidate = cleaned.substring(start, end + 1);
        try {
            return objectMapper.readTree(candidate);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String stripAnsi(String text) {
        return text == null ? "" : text.replaceAll("\\x1B\\[[;\\d]*m", "");
    }

    private String resolveSenderId(JsonNode eventJson, String fallbackChatId) {
        String openId = eventJson.at("/event/sender/sender_id/open_id").asText("");
        if (!openId.isBlank()) {
            return openId;
        }
        String unionId = eventJson.at("/event/sender/sender_id/union_id").asText("");
        if (!unionId.isBlank()) {
            return unionId;
        }
        String userId = eventJson.at("/event/sender/sender_id/user_id").asText("");
        if (!userId.isBlank()) {
            return userId;
        }
        return fallbackChatId == null || fallbackChatId.isBlank() ? "lark-im-user" : fallbackChatId;
    }

    void replyToMessage(String chatId, String messageId, String content) {
        if (!replyEnabled) {
            return;
        }
        try {
            Process process = new ProcessBuilder(
                    larkCliCommand, "im", "+messages-reply",
                    "--message-id", messageId,
                    "--text", content,
                    "--as", "bot"
            ).redirectErrorStream(true).start();
            boolean finished = process.waitFor(30, TimeUnit.SECONDS);
            if (!finished) {
                destroyProcessTree(process);
                log.warn("回复飞书消息超时，已结束 lark-cli 进程，messageId={}", messageId);
                return;
            }
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            if (process.exitValue() != 0) {
                log.warn("回复飞书消息失败，messageId={}，输出={}", messageId, output);
            }
        } catch (Exception e) {
            log.error("回复消息失败", e);
        }
    }

    private void destroySubscribeProcess() {
        Process process = subscribeProcess;
        subscribeProcess = null;
        if (process != null && process.isAlive()) {
            destroyProcessTree(process);
        }
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
