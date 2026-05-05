package com.hay.agent.service.preview;

import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.preview.DocumentPreviewRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.service.content.ContentPreviewService;
import org.springframework.stereotype.Component;

@Component
public class DocumentStepPreviewGenerator implements StepPreviewGenerator {
    private final ContentPreviewService contentPreviewService;

    public DocumentStepPreviewGenerator(ContentPreviewService contentPreviewService) {
        this.contentPreviewService = contentPreviewService;
    }

    @Override
    public boolean supports(PlanStep step) {
        return step != null && "C_DOC".equals(step.getStepId());
    }

    @Override
    public Object generate(AgentTask task, PlanStep step, GenerateStepPreviewRequest request) {
        return contentPreviewService.previewDocument(DocumentPreviewRequest.builder()
                .userInput(task.getInputText())
                .docType(step.getAction())
                .build());
    }

    @Override
    public String previewArtifactType() {
        return "docs-preview";
    }

    @Override
    public String previewTitle() {
        return "文档预览";
    }

    @Override
    public String expectedPreviewDataType() {
        return "DOCUMENT";
    }
}
