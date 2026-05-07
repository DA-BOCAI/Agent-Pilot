package com.hay.agent.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestClient;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.time.Duration;

/**
 * 内容生成服务，调用大模型生成各类文档、PPT内容
 */
@Slf4j
@Service
public class ContentGeneratorService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final double temperature;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public ContentGeneratorService(RestClient.Builder restClientBuilder,
                                   ObjectMapper objectMapper,
                                   @Value("${agent.llm.open-ai.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                                   @Value("${agent.llm.open-ai.api-key:}") String apiKey,
                                   @Value("${agent.llm.open-ai.model-name:}") String modelName,
                                   @Value("${agent.llm.open-ai.temperature:0.1}") double temperature,
                                   @Value("${agent.llm.open-ai.connect-timeout:10s}") Duration connectTimeout,
                                   @Value("${agent.llm.open-ai.read-timeout:180s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        // 清理baseUrl，去除末尾的逗号、斜杠等无效字符
        this.baseUrl = baseUrl.trim().replaceAll("[,/\\\\]+$", "");
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    /**
     * 生成文档内容（Markdown格式）
     * @param userInput 用户原始需求
     * @param docType 文档类型：需求文档、营销方案、技术方案、调研报告等
     * @return 完整的Markdown格式内容
     */
    public String generateDocContent(String userInput, String docType) {
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                validateConfiguration();
                log.info("开始生成{}内容，尝试 {}/{}，baseUrl={}, modelName={}, connectTimeout={}, readTimeout={}, 用户需求：{}",
                        docType, attempt, maxRetries + 1, baseUrl, modelName, connectTimeout, readTimeout, userInput);
                
                JsonNode requestBody = buildDocRequest(userInput, docType);
                JsonNode response = restClient.post()
                        .uri(baseUrl + "/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .body(requestBody)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) {
                    throw new IllegalStateException("大模型返回为空");
                }

                JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
                if (contentNode.isMissingNode() || contentNode.isNull()) {
                    log.error("大模型响应缺少 content 节点: {}", response);
                    throw new IllegalStateException("大模型响应格式异常，缺少 content");
                }

                String content = contentNode.asText();
                if (content == null || content.trim().isEmpty()) {
                    log.error("大模型生成的 content 内容为空: {}", response);
                    throw new IllegalStateException("大模型生成内容为空");
                }

                log.info("生成{}内容成功，内容长度：{}", docType, content.length());
                return content;

            } catch (RestClientResponseException e) {
                log.error("第{}次生成{}内容失败，HTTP状态码：{}，响应体：{}",
                        attempt, docType, e.getStatusCode().value(), truncate(e.getResponseBodyAsString()), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成" + docType + "失败，HTTP状态码=" + e.getStatusCode().value(), e);
                }
            } catch (ResourceAccessException e) {
                log.error("第{}次生成{}内容失败，疑似连接/超时问题：{}",
                        attempt, docType, classifyRootCause(e), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成" + docType + "失败，疑似连接/超时问题：" + classifyRootCause(e), e);
                }
            } catch (Exception e) {
                log.error("第{}次生成{}内容失败，异常类型：{}，原因：{}",
                        attempt, docType, e.getClass().getSimpleName(), e.getMessage(), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成" + docType + "失败，已重试" + (maxRetries + 1) + "次", e);
                }
            }

            try {
                // 等待1秒后重试
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试被中断", ie);
            }
        }
        throw new IllegalStateException("大模型生成" + docType + "失败，未能获得有效结果");
    }

    /**
     * 生成PPT内容（Markdown格式，适合直接导入PPT）
     * @param userInput 用户原始需求
     * @param topic PPT主题
     * @return 适合PPT的Markdown内容，每一个## 标题代表一页
     */
    public String generatePptContent(String userInput, String topic) {
        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                validateConfiguration();
                log.info("开始生成PPT内容，尝试 {}/{}，baseUrl={}, modelName={}, connectTimeout={}, readTimeout={}, 主题：{}，用户需求：{}",
                        attempt, maxRetries + 1, baseUrl, modelName, connectTimeout, readTimeout, topic, userInput);
                
                JsonNode requestBody = buildPptRequest(userInput, topic);
                JsonNode response = restClient.post()
                        .uri(baseUrl + "/chat/completions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                        .body(requestBody)
                        .retrieve()
                        .body(JsonNode.class);

                if (response == null) {
                    throw new IllegalStateException("大模型返回为空");
                }

                JsonNode contentNode = response.path("choices").path(0).path("message").path("content");
                if (contentNode.isMissingNode() || contentNode.isNull()) {
                    log.error("大模型响应缺少 content 节点: {}", response);
                    throw new IllegalStateException("大模型响应格式异常，缺少 content");
                }

                String content = contentNode.asText();
                if (content == null || content.trim().isEmpty()) {
                    log.error("大模型生成的 content 内容为空: {}", response);
                    throw new IllegalStateException("大模型生成内容为空");
                }

                log.info("生成PPT内容成功，内容长度：{}", content.length());
                return content;

            } catch (RestClientResponseException e) {
                log.error("第{}次生成PPT内容失败，HTTP状态码：{}，响应体：{}",
                        attempt, e.getStatusCode().value(), truncate(e.getResponseBodyAsString()), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成PPT失败，HTTP状态码=" + e.getStatusCode().value(), e);
                }
            } catch (ResourceAccessException e) {
                log.error("第{}次生成PPT内容失败，疑似连接/超时问题：{}", attempt, classifyRootCause(e), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成PPT失败，疑似连接/超时问题：" + classifyRootCause(e), e);
                }
            } catch (Exception e) {
                log.error("第{}次生成PPT内容失败，异常类型：{}，原因：{}",
                        attempt, e.getClass().getSimpleName(), e.getMessage(), e);
                if (attempt > maxRetries) {
                    throw new IllegalStateException("大模型生成PPT失败，已重试" + (maxRetries + 1) + "次", e);
                }
            }

            try {
                // 等待1秒后重试
                Thread.sleep(1000);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("重试被中断", ie);
            }
        }
        throw new IllegalStateException("大模型生成PPT失败，未能获得有效结果");
    }

    private void validateConfiguration() {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("大模型 baseUrl 未配置，请检查 LLM_BASE_URL");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("大模型 apiKey 未配置，请检查 LLM_API_KEY");
        }
        if (modelName == null || modelName.isBlank()) {
            throw new IllegalStateException("大模型 modelName 未配置，请检查 LLM_ENDPOINT_ID");
        }
    }

    private String classifyRootCause(Throwable throwable) {
        Throwable current = throwable;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        if (current instanceof SocketTimeoutException) {
            return "读取超时";
        }
        if (current instanceof ConnectException) {
            return "连接失败";
        }
        String message = current.getMessage();
        if (message != null && message.toLowerCase().contains("timed out")) {
            return "请求超时";
        }
        return current.getClass().getSimpleName() + (message == null ? "" : (": " + message));
    }

    private String truncate(String value) {
        if (value == null) {
            return "null";
        }
        return value.length() > 1000 ? value.substring(0, 1000) + "..." : value;
    }

    private JsonNode buildDocRequest(String userInput, String docType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);

        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", "你是一个专业的文档撰写专家。请根据用户需求生成完整的" + docType + "，要求结构清晰、内容专业、逻辑通顺。\n" +
                        "【输出要求】：\n" +
                        "1. 必须纯Markdown格式，不要输出任何额外说明、解释或代码块标记\n" +
                        "2. 必须包含标题、目录、各章节详细内容\n" +
                        "3. 专业术语准确，符合办公文档规范\n" +
                        "4. 内容要充实，不少于500字");
        messages.addObject()
                .put("role", "user")
                .put("content", userInput);

        return root;
    }

    private JsonNode buildPptRequest(String userInput, String topic) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);

        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        你是一个专业的商业演示稿策划与PPT制作专家。请根据用户需求生成适合飞书PPT的结构化内容。
                        【输出要求】：
                        1. 纯Markdown格式，不要任何额外说明
                        2. 每一个## 二级标题代表一页PPT
                        3. 每页标题必须像观点或结论，不要只写“背景”“内容”“总结”这类空泛标题
                        4. 每页正文只承载一个核心信息，使用3-5条短要点，不要大段文字
                        5. 页数根据内容复杂度决定，不要为了压缩而遗漏必要信息；超过10页也可以输出
                        6. 结构建议包含：封面、目录、核心结论、背景/目标、关键方案、执行计划、风险与应对、总结与下一步
                        7. 如果需求中包含【Doc → PPT 编排任务】或前序文档信息，必须基于文档内容重组为演示叙事，不要逐段复制文档原文
                        8. 优先把阶段、风险、对比、指标、行动项分别组织成时间线页、风险表页、对比页、指标卡页、行动计划页
                        9. 可以在每页末尾追加“讲稿提示：...”一行，用一句话提示演示者如何讲这一页
                        10. 末页请包含“排练建议”和“交付前检查”两个小节，方便用户确认演示完整度
                        """);
        messages.addObject()
                .put("role", "user")
                .put("content", "主题：" + topic + "\n需求：" + userInput);

        return root;
    }
}
