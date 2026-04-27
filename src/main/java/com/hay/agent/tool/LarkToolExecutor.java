package com.hay.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.Optional;

/**
 * 飞书工具执行器【与飞书生态进行交互】
 * 用于执行飞书相关的任务，如创建文档、演示文稿等。
 */


@Slf4j
@Primary        //MOCK过渡期~
@Component
@RequiredArgsConstructor
public class LarkToolExecutor implements ToolExecutor {

    private final ObjectMapper objectMapper;
    private static final String LARK_CLI_PATH = "C:\\Users\\Swiftie\\AppData\\Roaming\\npm\\lark-cli.cmd";

    @Override
    public Optional<Artifact> execute(PlanStep step, String taskId, String inputText) {
        log.info("开始执行任务：{}，输入：{}", step.getStepId(), inputText);
        return switch (step.getStepId()) {
            case "C_DOC" -> createLarkDoc(taskId, inputText); // 创建飞书文档
            case "D_SLIDES" -> createLarkSlides(taskId, inputText); // 创建飞书演示文稿
            case "SEND_IM" -> sendLarkIm(taskId, inputText); // 发送飞书消息
            case "F_DELIVER" -> deliverResult(taskId); // 交付最终结果
            default -> Optional.empty();
        };
    }

    /**
     * 创建飞书文档
     */
    private Optional<Artifact> createLarkDoc(String taskId, String inputText) {
        try {
            String title = "【" + taskId + "】" + inputText.substring(0, Math.min(20, inputText.length())) + "需求文档";
            String content = "# " + title + "\n\n## 需求背景\n" + inputText +
                    "\n\n## 功能说明\n待补充\n\n## 技术方案\n待补充";

            log.info("开始创建飞书文档，标题：{}", title);

            // 调用lark-cli创建文档
            Process process = new ProcessBuilder(
                    LARK_CLI_PATH,
                    "docs", "+create",
                    "--title", title,
                    "--markdown", content,
                    "--as", "bot"
            ).redirectErrorStream(true).start();

            // 读取输出解析JSON拿到文档链接
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            process.waitFor();
            
            // 打印lark-cli的输出，排查问题
            String output = result.toString();
            log.info("lark-cli创建文档输出：{}", output);
            
            // 过滤掉Windows控制台输出的无关行，只保留JSON部分
            String jsonStr = output.substring(output.indexOf("{"));
            
            // 解析返回的JSON拿到文档链接
            JsonNode resultJson = objectMapper.readTree(jsonStr);
            String docUrl = resultJson.at("/data/doc_url").asText();
            if (docUrl.isEmpty()) {
                throw new RuntimeException("创建文档失败，未返回链接");
            }
            log.info("创建飞书文档成功: {}", docUrl);

            return Optional.of(Artifact.builder()
                    .type("docs")
                    .title(title)
                    .url(docUrl)
                    .build());

        } catch (Exception e) {
            log.error("创建飞书文档失败，降级使用Mock", e);
            // 失败降级返回mock链接，不影响流程
            return Optional.of(Artifact.builder()
                    .type("docs")
                    .title("【降级】" + inputText.substring(0, Math.min(20, inputText.length())) + "需求文档")
                    .url("https://mock.lark/docs/" + taskId)
                    .build());
        }
    }

    /**
     * 创建飞书演示文稿（PPT）
     */
    private Optional<Artifact> createLarkSlides(String taskId, String inputText) {
        try {
            String title = "【" + taskId + "】" + inputText.substring(0, Math.min(20, inputText.length())) + "演示文稿";

            log.info("开始创建飞书演示文稿，标题：{}", title);

            // 调用lark-cli创建空白幻灯片
            Process process = new ProcessBuilder(
                    LARK_CLI_PATH,
                    "slides", "+create",
                    "--title", title,
                    "--as", "bot"
            ).redirectErrorStream(true).start();

            // 读取输出解析JSON拿到幻灯片链接
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            process.waitFor();
            
            // 打印lark-cli的输出，排查问题
            String output = result.toString();
            log.info("lark-cli创建幻灯片输出：{}", output);
            
            // 过滤掉Windows控制台输出的无关行，只保留JSON部分
            String jsonStr = output.substring(output.indexOf("{"));
            
            // 解析返回的JSON拿到幻灯片链接
            JsonNode resultJson = objectMapper.readTree(jsonStr);
            String slidesUrl = resultJson.at("/data/url").asText();
            if (slidesUrl.isEmpty()) {
                throw new RuntimeException("创建幻灯片失败，未返回链接");
            }

            // 给PPT写入第一页内容
            new ProcessBuilder(
                    LARK_CLI_PATH,
                    "slides", "+page-create",
                    "--url", slidesUrl,
                    "--title", "方案概述",
                    "--content", inputText,
                    "--as", "bot"
            ).start().waitFor();

            log.info("创建飞书演示文稿成功: {}", slidesUrl);
            return Optional.of(Artifact.builder()
                    .type("slides")
                    .title(title)
                    .url(slidesUrl)
                    .build());

        } catch (Exception e) {
            log.error("创建飞书演示文稿失败，降级使用Mock", e);
            return Optional.of(Artifact.builder()
                    .type("slides")
                    .title("【降级】" + inputText.substring(0, Math.min(20, inputText.length())) + "演示文稿")
                    .url("https://mock.lark/slides/" + taskId)
                    .build());
        }
    }

    /**
     * 发送飞书消息
     */
    private Optional<Artifact> sendLarkIm(String taskId, String inputText) {
        try {
            log.info("开始发送飞书消息，内容：{}", inputText);

            // 调用lark-cli发送消息（默认发送给当前用户，也可扩展支持指定群/用户）
            Process process = new ProcessBuilder(
                    LARK_CLI_PATH,
                    "im", "+send",
                    "--content", inputText,
                    "--as", "bot"
            ).redirectErrorStream(true).start();

            // 读取输出
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), "UTF-8"));
            StringBuilder result = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                result.append(line);
            }
            process.waitFor();
            
            // 打印输出，排查问题
            String output = result.toString();
            log.info("lark-cli发送消息输出：{}", output);
            
            // 过滤JSON部分
            String jsonStr = output.substring(output.indexOf("{"));
            JsonNode resultJson = objectMapper.readTree(jsonStr);
            
            if (resultJson.get("ok").asBoolean()) {
                String messageId = resultJson.at("/data/message_id").asText();
                log.info("发送飞书消息成功，消息ID：{}", messageId);

                return Optional.of(Artifact.builder()
                        .type("im")
                        .title("飞书消息")
                        .url("lark://message/" + messageId)
                        .build());
            } else {
                throw new RuntimeException("发送消息失败：" + resultJson.at("/error/message").asText());
            }

        } catch (Exception e) {
            log.error("发送飞书消息失败，降级使用Mock", e);
            return Optional.of(Artifact.builder()
                    .type("im")
                    .title("【降级】飞书消息")
                    .url("https://mock.lark/im/" + taskId)
                    .build());
        }
    }

    /**
     * 交付结果
     */
    private Optional<Artifact> deliverResult(String taskId) {
        return Optional.of(Artifact.builder()
                .type("delivery")
                .title("交付包-" + taskId)
                .url("http://localhost:8080/api/tasks/" + taskId)
                .build());
    }


}
