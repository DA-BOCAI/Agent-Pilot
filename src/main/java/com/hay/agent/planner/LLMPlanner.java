package com.hay.agent.planner;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.ResourceAccessException;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 文件作用：大模型任务规划器实现。
 * 项目角色：通过 OpenAI Compatible HTTP 接口请求豆包大模型，将自然语言稳定转换为步骤清单。
 */
@Slf4j
@Primary
@Component
public class LlmPlanner implements Planner {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final double temperature;
    private final Duration connectTimeout;
    private final Duration readTimeout;

    public LlmPlanner(RestClient.Builder restClientBuilder,
                      ObjectMapper objectMapper,
                      @Value("${langchain4j.open-ai.base-url:https://ark.cn-beijing.volces.com/api/v3}") String baseUrl,
                      @Value("${langchain4j.open-ai.api-key:}") String apiKey,
                      @Value("${langchain4j.open-ai.model-name:}") String modelName,
                      @Value("${langchain4j.open-ai.temperature:0.0}") double temperature,
                      @Value("${langchain4j.open-ai.connect-timeout:10s}") Duration connectTimeout,
                      @Value("${langchain4j.open-ai.read-timeout:180s}") Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);

        this.restClient = restClientBuilder.requestFactory(requestFactory).build();
        this.objectMapper = objectMapper;
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
        this.connectTimeout = connectTimeout;
        this.readTimeout = readTimeout;
    }

    @Override
    public List<PlanStep> plan(String inputText) {
        if (!StringUtils.hasText(inputText)) {
            throw new IllegalArgumentException("输入内容不能为空");
        }

        int maxRetries = 2;
        for (int attempt = 1; attempt <= maxRetries + 1; attempt++) {
            try {
                log.info("开始请求大模型进行计划拆解，尝试 {}/{}，baseUrl={}, modelName={}, connectTimeout={}, readTimeout={}, 输入长度：{}",
                        attempt, maxRetries + 1, baseUrl, modelName, connectTimeout, readTimeout, inputText.length());
                String content = invokeModel(inputText);
                log.info("大模型原始返回：{}", content);

                List<PlanStep> steps = parseSteps(content);
                normalizeSteps(steps);

                log.info("大模型计划拆解成功，包含步骤数：{}", steps.size());
                return steps;
            } catch (RestClientResponseException e) {
                log.error("大模型 HTTP 调用失败，状态码：{}，响应体：{}", e.getStatusCode(), e.getResponseBodyAsString(), e);
                if (attempt > maxRetries) {
                    throw new RuntimeException(buildFailureMessage("大模型接口返回错误", e), e);
                }
            } catch (ResourceAccessException e) {
                log.error("大模型网络访问失败，可能是超时或连接问题", e);
                if (attempt > maxRetries) {
                    throw new RuntimeException(buildFailureMessage("大模型网络访问失败", e), e);
                }
            } catch (Exception e) {
                log.error("大模型结果解析失败或其他异常", e);
                if (attempt > maxRetries) {
                    throw new RuntimeException(buildFailureMessage("大模型输出解析失败", e), e);
                }
            }
        }

        throw new RuntimeException("大模型规划过程发生未知错误");
    }

    private String invokeModel(String inputText) {
        validateConfig();
        JsonNode requestBody = buildRequestBody(inputText);
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
        if (contentNode.isMissingNode() || !StringUtils.hasText(contentNode.asText())) {
            throw new IllegalStateException("大模型返回中没有可用的 content 字段");
        }

        return contentNode.asText();
    }

    private void validateConfig() {
        if (!StringUtils.hasText(baseUrl)) {
            throw new IllegalStateException("大模型配置缺失：LLM_BASE_URL 未设置");
        }
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("大模型配置缺失：LLM_API_KEY 未设置");
        }
        if (!StringUtils.hasText(modelName)) {
            throw new IllegalStateException("大模型配置缺失：LLM_ENDPOINT_ID 未设置");
        }
    }

    private JsonNode buildRequestBody(String inputText) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);

        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        你是一个办公智能Agent的主规划师。请严格根据用户意图拆解步骤，必须只输出 JSON 数组，不要输出任何额外说明、markdown 或代码块。
                        【严格约束规则，必须100%遵守】：
                        1. stepId 只能从以下枚举值中选择，绝对禁止自定义其他值：
                           - C_DOC: 创建飞书文档，用于生成各类方案、报告、需求文档等文本内容
                           - D_SLIDES: 创建飞书演示文稿PPT，用于生成汇报用PPT
                           - SEND_IM: 发送飞书消息，用于给用户发送通知、交付结果等
                           - F_DELIVER: 最终结果交付，代表整个任务完成
                        2. tool 字段只能从以下值中选择，禁止其他值：
                           - lark-doc: 对应C_DOC步骤
                           - lark-slides: 对应D_SLIDES步骤
                           - lark-im: 对应SEND_IM步骤
                           - none: 其他不需要调用工具的步骤
                        3. 每个步骤对象必须包含 stepId、scene、action、tool、requiresConfirm 5个字段
                        4. 只有 C_DOC 和 D_SLIDES 允许 requiresConfirm=true，用于进入 confirm1/confirm2 双确认链路；A_CAPTURE、B_PLAN、SEND_IM、F_DELIVER 必须为 false
                        5. 拆解步骤要符合逻辑顺序，比如需要先生成文档内容，再生成PPT，最后通知用户
                        """)
                ;
        messages.addObject()
                .put("role", "user")
                .put("content", """
                        规划策略补充：
                        - 当任务不是极简单步操作时，优先把 A_CAPTURE 和 B_PLAN 作为前两个无外部副作用步骤。
                        - A_CAPTURE 表示理解并标准化用户意图，tool 必须为 none，requiresConfirm 必须为 false。
                        - B_PLAN 表示整理执行计划，tool 必须为 none，requiresConfirm 必须为 false。
                        - C_DOC 和 D_SLIDES 会创建飞书产物，必须进入 confirm1/confirm2 双确认链路，因此 requiresConfirm 必须为 true。
                        - SEND_IM 是 Agent 在产物创建后的自动通知动作，F_DELIVER 是内部交付收尾动作，二者必须 requiresConfirm=false，禁止产生第三次确认。
                        - 禁止把所有步骤都设为 requiresConfirm=true，确认闸门只用于文档/PPT等用户需要预览和最终确认的产物步骤。

                        用户请求：
                        """ + inputText);

        return root;
    }

    private List<PlanStep> parseSteps(String content) {
        String json = extractJson(content);
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (Exception e) {
            throw new IllegalStateException("无法将大模型输出解析为 JSON: " + json, e);
        }

        JsonNode stepArray = root;
        if (root.isObject() && root.has("steps") && root.get("steps").isArray()) {
            stepArray = root.get("steps");
        }

        if (!stepArray.isArray()) {
            throw new IllegalStateException("大模型输出不是 JSON 数组，也不包含 steps 数组");
        }

        List<PlanStep> steps = new ArrayList<>();
        for (int i = 0; i < stepArray.size(); i++) {
            JsonNode node = stepArray.get(i);
            PlanStep step = PlanStep.builder()
                    .stepId(text(node, "stepId"))
                    .scene(text(node, "scene"))
                    .action(text(node, "action"))
                    .tool(node.path("tool").asText("none"))
                    .requiresConfirm(node.path("requiresConfirm").asBoolean(false))
                    .status(StepStatus.PENDING)
                    .build();
            steps.add(step);
        }
        return steps;
    }

    private void normalizeSteps(List<PlanStep> steps) {
        if (steps == null || steps.isEmpty()) {
            throw new IllegalStateException("大模型没有返回任何规划步骤");
        }

        for (int i = 0; i < steps.size(); i++) {
            PlanStep step = steps.get(i);
            if (!StringUtils.hasText(step.getStepId())) {
                step.setStepId("STEP_" + i + "_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            }
            if (!StringUtils.hasText(step.getScene())) {
                step.setScene("AUTO");
            }
            if (!StringUtils.hasText(step.getAction())) {
                throw new IllegalStateException("大模型返回的步骤 action 不能为空");
            }
            if (!StringUtils.hasText(step.getTool())) {
                step.setTool("none");
            }
            if ("none".equals(step.getTool())
                    || "A_CAPTURE".equals(step.getStepId())
                    || "B_PLAN".equals(step.getStepId())) {
                step.setRequiresConfirm(false);
            }
            if ("SEND_IM".equals(step.getStepId()) || "F_DELIVER".equals(step.getStepId())) {
                step.setRequiresConfirm(false);
            }
            if ("C_DOC".equals(step.getStepId())
                    || "D_SLIDES".equals(step.getStepId())) {
                step.setRequiresConfirm(true);
            }
            step.setStatus(StepStatus.PENDING);
        }
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        if (!StringUtils.hasText(trimmed)) {
            throw new IllegalStateException("大模型返回内容为空");
        }

        int arrayStart = trimmed.indexOf('[');
        int arrayEnd = trimmed.lastIndexOf(']');
        if (arrayStart >= 0 && arrayEnd > arrayStart) {
            return trimmed.substring(arrayStart, arrayEnd + 1);
        }

        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }

        throw new IllegalStateException("大模型返回中找不到可解析的 JSON 片段");
    }

    private String text(JsonNode node, String field) {
        String value = node.path(field).asText(null);
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException("大模型返回步骤缺少字段：" + field);
        }
        return value;
    }


    private String buildFailureMessage(String prefix, Exception e) {
        Throwable root = e;
        while (root.getCause() != null && root.getCause() != root) {
            root = root.getCause();
        }
        return prefix + "，根因：" + root.getClass().getSimpleName() + " - " + root.getMessage();
    }
}

