package com.hay.agent.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.lang.reflect.Method;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ContentGeneratorServiceTest {

    @Test
    void shouldAskModelToGeneratePresentationStyleMarkdown() throws Exception {
        ContentGeneratorService service = new ContentGeneratorService(
                RestClient.builder(),
                new ObjectMapper(),
                "https://example.com/api/v3",
                "test-key",
                "test-model",
                0.1,
                Duration.ofSeconds(1),
                Duration.ofSeconds(1)
        );

        Method method = ContentGeneratorService.class.getDeclaredMethod("buildPptRequest", String.class, String.class);
        method.setAccessible(true);
        JsonNode request = (JsonNode) method.invoke(service, "【Doc → PPT 编排任务】基于前序文档生成汇报稿", "校招宣讲");

        String systemPrompt = request.path("messages").path(0).path("content").asText();
        String userPrompt = request.path("messages").path(1).path("content").asText();
        assertTrue(systemPrompt.contains("每页标题必须像观点或结论"));
        assertTrue(systemPrompt.contains("Doc → PPT 编排任务"));
        assertTrue(systemPrompt.contains("讲稿提示"));
        assertTrue(systemPrompt.contains("排练建议"));
        assertTrue(systemPrompt.contains("交付前检查"));
        assertTrue(userPrompt.contains("主题：校招宣讲"));
    }
}
