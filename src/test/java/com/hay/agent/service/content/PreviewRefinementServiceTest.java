package com.hay.agent.service.content;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PreviewRefinementServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final PreviewRefinementService service = new PreviewRefinementService(
            RestClient.builder(),
            objectMapper,
            "http://localhost",
            "test-key",
            "test-model",
            0.1,
            Duration.ofSeconds(1),
            Duration.ofSeconds(1)
    );

    @Test
    void parseAndApplyRefinementShouldMergeAddressedSlidePatch() throws Exception {
        JsonNode current = objectMapper.readTree("""
                {
                  "artifactType": "PRESENTATION",
                  "title": "原演示稿",
                  "theme": "business",
                  "slides": [
                    {"slideNo": 1, "title": "封面", "bullets": ["A"]},
                    {"slideNo": 2, "title": "旧标题", "bullets": ["旧要点"], "speakerNotes": "旧讲稿"}
                  ]
                }
                """);

        JsonNode refined = service.parseAndApplyRefinement(current, """
                ```json
                {
                  "artifactType": "PRESENTATION",
                  "theme": "tech",
                  "slides": [
                    {"slideNo": 2, "title": "新标题", "bullets": ["新要点"]}
                  ]
                }
                ```
                """, "PRESENTATION").orElseThrow();

        assertEquals("tech", refined.path("theme").asText());
        assertEquals("封面", refined.at("/slides/0/title").asText());
        assertEquals("新标题", refined.at("/slides/1/title").asText());
        assertEquals("新要点", refined.at("/slides/1/bullets/0").asText());
        assertEquals("旧讲稿", refined.at("/slides/1/speakerNotes").asText());
    }

    @Test
    void parseAndApplyRefinementShouldReturnEmptyForTruncatedJson() throws Exception {
        JsonNode current = objectMapper.readTree("""
                {"artifactType":"PRESENTATION","slides":[{"slideNo":1,"title":"封面"}]}
                """);

        assertTrue(service.parseAndApplyRefinement(current,
                "{\"artifactType\":\"PRESENTATION\",\"slides\":[{\"slideNo\":1,\"title\":\"新标题\"}",
                "PRESENTATION").isEmpty());
    }

    @Test
    void parseAndApplyRefinementShouldMergeDocumentPatch() throws Exception {
        JsonNode current = objectMapper.readTree("""
                {"artifactType":"DOCUMENT","title":"旧文档","rawMarkdown":"# 旧文档\\n正文"}
                """);

        JsonNode refined = service.parseAndApplyRefinement(current,
                "{\"artifactType\":\"DOCUMENT\",\"title\":\"新文档\"}",
                "DOCUMENT").orElseThrow();

        assertEquals("新文档", refined.path("title").asText());
        assertEquals("# 旧文档\n正文", refined.path("rawMarkdown").asText());
    }
}
