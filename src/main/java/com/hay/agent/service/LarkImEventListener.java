package com.hay.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.api.dto.CreateTaskRequest;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkImEventListener {

    private final AgentTaskService agentTaskService;
    private final ObjectMapper objectMapper;

    @PostConstruct
    public void startListening() {
        // 启动子进程运行lark-cli监听命令
        new Thread(() -> {
            try {
                Process process = new ProcessBuilder(
                    "C:\\Users\\Swiftie\\AppData\\Roaming\\npm\\lark-cli.cmd",
                    "event",
                    "+subscribe",
                    "--event-types", "im.message.receive_v1",
                    "--as", "bot",
                    "--force"
                ).redirectErrorStream(true).start();

                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), "GBK")
                );

                String line;
                StringBuilder jsonBuffer = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    log.info("【原始输出】{}", line);
                    line = line.trim();
                    if (line.isEmpty()) continue;

                    // 过滤系统输出
                    if (line.startsWith("Active code page:")
                        || line.startsWith("Connecting to")
                        || line.startsWith("Listening for")
                        || line.startsWith("Connected.")
                        || line.startsWith("[SDK Info]")
                        || line.contains("code page")
                        || line.startsWith("\u001B[")) {
                        continue;
                    }

                    // 去掉ANSI颜色编码
                    line = line.replaceAll("\\x1B\\[[;\\d]*m", "");
                    jsonBuffer.append(line);

                    // 只解析完整的JSON对象
                    if (line.endsWith("}") && jsonBuffer.length() > 2) {
                        try {
                            JsonNode eventJson = objectMapper.readTree(jsonBuffer.toString());
                            jsonBuffer.setLength(0);

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
                            // JSON不完整就继续拼接下一行
                            if (e.getMessage().contains("Unexpected end-of-input")) {
                                continue;
                            }
                            log.error("解析失败: {}", jsonBuffer, e);
                            jsonBuffer.setLength(0);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("飞书消息监听服务启动失败", e);
            }
        }, "lark-im-listener").start();

        log.info("飞书IM消息监听服务已启动");
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