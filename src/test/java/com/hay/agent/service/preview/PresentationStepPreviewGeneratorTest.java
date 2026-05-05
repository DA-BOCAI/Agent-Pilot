package com.hay.agent.service.preview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.service.content.ContentPreviewService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PresentationStepPreviewGeneratorTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void shouldUseConfirmedDocumentPreviewAsSlidesSourceInput() {
        ContentPreviewService contentPreviewService = mock(ContentPreviewService.class);
        when(contentPreviewService.previewPresentation(any())).thenReturn(PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title("校招宣讲")
                .theme("business")
                .pageCount(1)
                .slides(List.of())
                .warnings(List.of())
                .build());
        PresentationStepPreviewGenerator generator = new PresentationStepPreviewGenerator(contentPreviewService);

        ObjectNode docPreviewData = objectMapper.createObjectNode();
        docPreviewData.put("artifactType", "DOCUMENT");
        docPreviewData.put("title", "校招方案文档");
        docPreviewData.put("rawMarkdown", """
                # 校招方案
                ## 岗位亮点
                强调培养机制和跨岗协作。
                ## 风险与应对
                - 候选人认知不足，需要补充 FAQ
                - 宣讲节奏不清晰，需要明确时间线
                """);
        AgentTask task = AgentTask.builder()
                .taskId("task-1")
                .inputText("帮我们生成一份校招宣讲PPT")
                .artifacts(List.of(Artifact.builder()
                        .type("docs-preview")
                        .stepId("C_DOC")
                        .title("校招方案文档")
                        .previewData(docPreviewData)
                        .build()))
                .build();

        generator.generate(task, PlanStep.builder()
                .stepId("D_SLIDES")
                .action("基于前序方案文档生成演示文稿")
                .build(), null);

        ArgumentCaptor<PresentationPreviewRequest> captor = ArgumentCaptor.forClass(PresentationPreviewRequest.class);
        verify(contentPreviewService).previewPresentation(captor.capture());
        String userInput = captor.getValue().getUserInput();
        assertTrue(userInput.contains("Doc → PPT 编排任务"));
        assertTrue(userInput.contains("文档标题：校招方案文档"));
        assertTrue(userInput.contains("文档章节：校招方案 / 岗位亮点 / 风险与应对"));
        assertTrue(userInput.contains("岗位亮点"));
        assertTrue(userInput.contains("候选人认知不足"));
        assertFalse(userInput.contains("```"));
    }
}
