package com.hay.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.api.dto.CreateTaskRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "agent.task.auto-run", havingValue = "true", matchIfMissing = true)
public class LarkImEventListener {

    private final AgentTaskService agentTaskService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void startListening() {
        // 启动子进程运行lark-cli监听命令
        new Thread(() -> {
            try {
                AtomicBoolean connectionLimitReported = new AtomicBoolean(false);
                Process process = new ProcessBuilder(
                    "C:\\Users\\Swiftie\\AppData\\Roaming\\npm\\lark-cli.cmd",
                    "event",
                    "+subscribe",
                    "--event-types", "im.message.receive_v1",
                    "--as", "bot",
                    "--force"
                ).redirectErrorStream(true).start();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8)
                );

                String line;
                StringBuilder rawBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    String normalizedLine = stripAnsi(line).trim();
                    if (normalizedLine.isEmpty()) {
                        continue;
                    }

                    if (isNoiseLine(normalizedLine)) {
                        if (normalizedLine.contains("the number of connections exceeded the limit")
                                && connectionLimitReported.compareAndSet(false, true)) {
                            log.warn("飞书事件监听失败：连接数已超限，监听器本次停止继续解析，避免日志噪音");
                        }
                        continue;
                    }

                    rawBuffer.append(normalizedLine).append(System.lineSeparator());

                    JsonNode eventJson = tryParseJson(rawBuffer.toString());
                    if (eventJson == null) {
                        continue;
                    }
                    rawBuffer.setLength(0);

                    try {
                        String eventType = eventJson.at("/header/event_type").asText();
                        if (!"im.message.receive_v1".equals(eventType)) {
                            continue;
                        }

                        String chatId = eventJson.at("/event/message/chat_id").asText();
                        String content = eventJson.at("/event/message/content").asText();
                        String messageId = eventJson.at("/event/message/message_id").asText();

                        JsonNode contentJson = objectMapper.readTree(content);
                        String text = contentJson.get("text").asText()
                                .replaceAll("@_user_\\d+\\s*", "").trim();

                        log.info("收到用户消息：{}", text);

                        if (!text.isEmpty()) {
                            CreateTaskRequest request = new CreateTaskRequest();
                            request.setInputText(text);
                            request.setSource("IM:" + chatId);
                            String taskId = agentTaskService.createTask(request).getTaskId();
                            replyToMessage(chatId, messageId, "任务已创建，ID: " + taskId + "\n正在处理：" + text);
                        }

                    } catch (Exception e) {
                        log.debug("忽略非事件JSON或解析不完整内容：{}", rawBuffer, e);
                        rawBuffer.setLength(0);
                    }
                }
            } catch (Exception e) {
                log.error("飞书消息监听服务启动失败", e);
            }
        }, "lark-im-listener").start();

        log.info("飞书IM消息监听服务已启动");
    }

    private boolean isNoiseLine(String line) {
        return line.startsWith("Active code page:")
                || line.startsWith("Connecting to")
                || line.startsWith("Listening for")
                || line.startsWith("Connected.")
                || line.startsWith("[SDK Info]")
                || line.startsWith("[SDK Error]")
                || line.contains("code page")
                || line.contains("the number of connections exceeded the limit")
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

    // 回复消息方法
    private void replyToMessage(String chatId, String messageId, String content) {
        try {
            new ProcessBuilder(
                "lark-cli", "im", "+messages-reply",
                "--chat-id", chatId,
                "--message-id", messageId,
                "--content", content,
                "--as", "bot"
            ).start();
        } catch (Exception e) {
            log.error("回复消息失败", e);
        }
    }
}