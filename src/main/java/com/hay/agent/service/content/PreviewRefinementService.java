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
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Duration;
import java.util.Optional;

@Slf4j
@Service
public class PreviewRefinementService {
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;
    private final String apiKey;
    private final String modelName;
    private final double temperature;

    public PreviewRefinementService(RestClient.Builder restClientBuilder,
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
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("[,/\\\\]+$", "");
        this.apiKey = apiKey;
        this.modelName = modelName;
        this.temperature = temperature;
    }

    public Optional<JsonNode> refine(JsonNode currentPreviewData, String instruction, String expectedArtifactType) {
        if (!isConfigured()) {
            return Optional.empty();
        }
        try {
            JsonNode response = restClient.post()
                    .uri(baseUrl + "/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                    .body(buildRequest(currentPreviewData, instruction, expectedArtifactType))
                    .retrieve()
                    .body(JsonNode.class);

            String content = response == null ? "" : response.path("choices").path(0).path("message").path("content").asText("");
            if (!StringUtils.hasText(content)) {
                return Optional.empty();
            }

            JsonNode refined = objectMapper.readTree(extractJson(content));
            if (!refined.isObject()) {
                return Optional.empty();
            }
            return Optional.of(refined);
        } catch (RestClientException e) {
            log.warn("大模型预览精修请求失败，降级为规则精修：{}", e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("大模型预览精修输出无效，降级为规则精修：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private boolean isConfigured() {
        return StringUtils.hasText(baseUrl) && StringUtils.hasText(apiKey) && StringUtils.hasText(modelName);
    }

    private JsonNode buildRequest(JsonNode currentPreviewData, String instruction, String expectedArtifactType) {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", modelName);
        root.put("temperature", temperature);

        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        你负责精修办公 Agent 的结构化 previewData JSON。
                        必须只返回一个合法 JSON 对象，不要返回 Markdown 代码块、解释或额外文字。
                        除非用户明确要求修改，否则必须保持原有字段和结构不变。
                        返回结果中的 artifactType 必须等于期望的产物类型。

                        当 previewData 是 PRESENTATION 时：
                        - 可以修改 title、theme、pageCount、estimatedDurationMinutes、slides、rehearsalTips、reviewChecklist、每页标题、bodyMarkdown、bullets、speakerNotes 和 blocks。
                        - 支持的主题值只能是 business、tech、campaign、minimal。
                        - 优先做局部修改，不要无故重写整份演示文稿。
                        - 如果用户要求删除、调整顺序或新增页面，必须同步更新 slides 和 pageCount。
                        - 如果用户要求优化讲稿、排练、演讲口径或备注，优先修改 speakerNotes、rehearsalTips 和 reviewChecklist。

                        当 previewData 是 DOCUMENT 时：
                        - 可以修改 title、rawMarkdown、outline、sections 和 warnings。
                        - 除非用户要求大范围重写，否则应保持文档结构稳定。
                        """);
        messages.addObject()
                .put("role", "user")
                .put("content", "期望的 artifactType：" + expectedArtifactType
                        + "\n用户精修指令：" + instruction
                        + "\n当前 previewData JSON：\n" + currentPreviewData.toString());
        return root;
    }

    private String extractJson(String content) {
        String trimmed = content == null ? "" : content.trim();
        int objectStart = trimmed.indexOf('{');
        int objectEnd = trimmed.lastIndexOf('}');
        if (objectStart >= 0 && objectEnd > objectStart) {
            return trimmed.substring(objectStart, objectEnd + 1);
        }
        return trimmed;
    }
}
