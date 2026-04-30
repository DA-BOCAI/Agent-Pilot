package com.hay.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.runner.AgentRunner;
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
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.im.listener.enabled", havingValue = "true")
public class LarkImEventListener {

    private static final AtomicBoolean LISTENER_RUNNING = new AtomicBoolean(false);
    private static final Duration MESSAGE_DEDUP_TTL = Duration.ofHours(2);

    private final AgentTaskService agentTaskService;
    private final AgentRunner agentRunner;
    private final LarkTaskCardService larkTaskCardService;
    private final ObjectMapper objectMapper;
    private final Map<String, Instant> processedMessageIds = new ConcurrentHashMap<>();

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

            if (!shouldTriggerInChat(chatType, message, rawText)) {
                log.debug("忽略未触发机器人的群聊消息，chatId={}，messageId={}", chatId, messageId);
                return;
            }

            if (text.isBlank()) {
                log.debug("忽略空文本飞书消息，messageId={}", messageId);
                return;
            }

            AgentTask task = createAndRunTask(chatId, chatType, messageId, userId, text);
            replyToMessage(chatId, messageId, buildTaskCreatedReply(task, text));
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
            AgentTask advancedTask = agentRunner.runUntilBlocked(task.getTaskId());
            larkTaskCardService.sendCardsForCurrentState(chatId, advancedTask);
        });
        log.info("飞书 IM 消息已创建任务，taskId={}，chatId={}，chatType={}", task.getTaskId(), chatId, chatType);
        return task;
    }

    private boolean shouldTriggerInChat(String chatType, JsonNode message, String rawText) {
        if (!groupRequiresMention || !"group".equalsIgnoreCase(chatType)) {
            return true;
        }
        return mentionsConfiguredBot(message) || containsMentionPlaceholder(rawText);
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
                + "需求摘要：" + abbreviate(text, 80);
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
            new ProcessBuilder(
                    larkCliCommand, "im", "+messages-reply",
                    "--chat-id", chatId,
                    "--message-id", messageId,
                    "--content", content,
                    "--as", "bot"
            ).start();
        } catch (Exception e) {
            log.error("回复消息失败", e);
        }
    }

    private void destroySubscribeProcess() {
        Process process = subscribeProcess;
        subscribeProcess = null;
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }
}
