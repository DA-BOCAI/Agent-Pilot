package com.hay.agent.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
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

            return parseAndApplyRefinement(currentPreviewData, content, expectedArtifactType);
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
        root.put("max_tokens", 2048);

        var messages = root.putArray("messages");
        messages.addObject()
                .put("role", "system")
                .put("content", """
                        你负责精修办公 Agent 的结构化 previewData。
                        只返回一个合法 JSON 对象，不要返回 Markdown 代码块、解释或额外文字。

                        重要：不要返回完整 previewData。只返回局部 patch，后端会把 patch 合并回当前 previewData。
                        返回对象必须包含 artifactType，且 artifactType 必须等于期望的产物类型。

                        PRESENTATION patch 规则：
                        - 可以修改 title、theme、pageCount、estimatedDurationMinutes、rehearsalTips、reviewChecklist。
                        - 修改页面时，slides 只放发生变化的页面对象，不要返回全部 slides。
                        - slides 中每个对象必须带 slideNo，例如：
                          {"artifactType":"PRESENTATION","slides":[{"slideNo":5,"title":"新标题","bullets":["新的要点"]}]}
                        - 支持的 theme 只能是 business、tech、campaign、minimal。
                        - 优先做局部修改，不要无故重写整份演示稿。
                        - 如果用户要求优化讲稿、排练或备注，优先修改 speakerNotes、rehearsalTips 和 reviewChecklist。

                        DOCUMENT patch 规则：
                        - 可以修改 title、rawMarkdown、outline、sections 和 warnings。
                        - 除非用户要求大范围重写，否则保持文档结构稳定。
                        """);
        messages.addObject()
                .put("role", "user")
                .put("content", "期望的 artifactType：" + expectedArtifactType
                        + "\n用户精修指令：" + instruction
                        + "\n当前 previewData JSON：\n" + currentPreviewData.toString());
        return root;
    }

    Optional<JsonNode> parseAndApplyRefinement(JsonNode currentPreviewData, String content, String expectedArtifactType) {
        try {
            JsonNode patch = objectMapper.readTree(extractJson(content));
            if (!patch.isObject()) {
                return Optional.empty();
            }
            ObjectNode merged = currentPreviewData != null && currentPreviewData.isObject()
                    ? currentPreviewData.deepCopy()
                    : objectMapper.createObjectNode();
            applyPatchObject(merged, (ObjectNode) patch);
            merged.put("artifactType", expectedArtifactType);
            return Optional.of(merged);
        } catch (Exception e) {
            log.warn("大模型预览精修 JSON patch 解析失败，降级为规则精修：{}", e.getMessage());
            return Optional.empty();
        }
    }

    private void applyPatchObject(ObjectNode target, ObjectNode patch) {
        patch.fields().forEachRemaining(entry -> {
            String field = entry.getKey();
            JsonNode value = entry.getValue();
            if ("artifactType".equals(field)) {
                return;
            }
            if ("slides".equals(field) && value.isArray() && target.path("slides").isArray()) {
                applySlidesPatch((ArrayNode) target.path("slides"), (ArrayNode) value);
                return;
            }
            mergeField(target, field, value);
        });
    }

    private void applySlidesPatch(ArrayNode currentSlides, ArrayNode patchSlides) {
        boolean allAddressed = true;
        for (JsonNode slidePatch : patchSlides) {
            if (!slidePatch.isObject() || resolveSlideIndex(slidePatch) < 0) {
                allAddressed = false;
                break;
            }
        }
        if (!allAddressed) {
            currentSlides.removeAll();
            patchSlides.forEach(node -> currentSlides.add(node.deepCopy()));
            return;
        }

        for (JsonNode slidePatchNode : patchSlides) {
            ObjectNode slidePatch = (ObjectNode) slidePatchNode;
            int slideIndex = resolveSlideIndex(slidePatch);
            while (slideIndex >= currentSlides.size()) {
                ObjectNode created = objectMapper.createObjectNode();
                created.put("slideNo", currentSlides.size() + 1);
                currentSlides.add(created);
            }
            JsonNode currentSlide = currentSlides.get(slideIndex);
            if (currentSlide instanceof ObjectNode currentSlideObject) {
                slidePatch.fields().forEachRemaining(entry -> {
                    String field = entry.getKey();
                    if ("slideNo".equals(field) || "pageIndex".equals(field) || "index".equals(field)) {
                        return;
                    }
                    mergeField(currentSlideObject, field, entry.getValue());
                });
            } else {
                currentSlides.set(slideIndex, slidePatch.deepCopy());
            }
        }
    }

    private int resolveSlideIndex(JsonNode slidePatch) {
        int slideNo = slidePatch.path("slideNo").asInt(0);
        if (slideNo <= 0) {
            slideNo = slidePatch.path("pageIndex").asInt(0);
        }
        if (slideNo <= 0 && slidePatch.has("index")) {
            slideNo = slidePatch.path("index").asInt(-1) + 1;
        }
        return slideNo <= 0 ? -1 : slideNo - 1;
    }

    private void mergeField(ObjectNode target, String field, JsonNode value) {
        if (value == null || value.isNull()) {
            target.remove(field);
            return;
        }
        JsonNode current = target.get(field);
        if (current instanceof ObjectNode currentObject && value instanceof ObjectNode patchObject) {
            applyPatchObject(currentObject, patchObject);
            return;
        }
        target.set(field, value.deepCopy());
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
