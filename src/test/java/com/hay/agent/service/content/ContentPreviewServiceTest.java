package com.hay.agent.service.content;

import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.service.presentation.PresentationDesignAdvisor;
import com.hay.agent.service.presentation.PresentationMarkdownParser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ContentPreviewServiceTest {

    @Test
    void previewPresentationShouldAllowMoreThanTenSlidesAndWarn() {
        ContentGeneratorService contentGeneratorService = mock(ContentGeneratorService.class);
        when(contentGeneratorService.generatePptContent("生成完整产品发布会PPT", "产品发布会"))
                .thenReturn(markdownSlides(12));
        ContentPreviewService service = new ContentPreviewService(
                contentGeneratorService,
                new MarkdownPreviewParser(),
                new PresentationMarkdownParser(),
                new PresentationDesignAdvisor()
        );

        PresentationPreviewResponse response = service.previewPresentation(PresentationPreviewRequest.builder()
                .userInput("生成完整产品发布会PPT")
                .topic("产品发布会")
                .theme("business")
                .build());

        assertEquals(15, response.getPageCount());
        assertEquals(15, response.getSlides().size());
        assertFalse(response.getWarnings().isEmpty());
        assertEquals(12, response.getEstimatedDurationMinutes());
        assertEquals("cover", response.getSlides().get(0).getLayout());
        assertEquals("目录", response.getSlides().get(1).getTitle());
        assertEquals("核心结论", response.getSlides().get(2).getTitle());
        assertEquals("closing", response.getSlides().get(14).getLayout());
        assertFalse(response.getRehearsalTips().isEmpty());
        assertFalse(response.getReviewChecklist().isEmpty());
        org.junit.jupiter.api.Assertions.assertTrue(response.getReviewChecklist().stream()
                .anyMatch(item -> item.contains("封面标题")));
        assertFalse(response.getSlides().get(0).getSpeakerNotes().isBlank());
        org.junit.jupiter.api.Assertions.assertTrue(response.getSlides().get(0).getSpeakerNotes().contains("第1页"));
    }

    private String markdownSlides(int count) {
        StringBuilder markdown = new StringBuilder("# 产品发布会\n\n");
        for (int i = 1; i <= count; i++) {
            markdown.append("## 第").append(i).append("页\n")
                    .append("- 要点A\n")
                    .append("- 要点B\n\n");
        }
        return markdown.toString();
    }
}
