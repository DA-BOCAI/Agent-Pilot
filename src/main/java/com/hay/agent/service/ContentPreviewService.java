package com.hay.agent.service;

import com.hay.agent.api.dto.preview.DocumentPreviewRequest;
import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import org.springframework.stereotype.Service;

@Service
public class ContentPreviewService {

    private final ContentGeneratorService contentGeneratorService;
    private final MarkdownPreviewParser markdownPreviewParser;

    public ContentPreviewService(ContentGeneratorService contentGeneratorService,
                                 MarkdownPreviewParser markdownPreviewParser) {
        this.contentGeneratorService = contentGeneratorService;
        this.markdownPreviewParser = markdownPreviewParser;
    }

    public DocumentPreviewResponse previewDocument(DocumentPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generateDocContent(request.getUserInput(), request.getDocType());
        return markdownPreviewParser.parseDocument(rawMarkdown, request.getDocType());
    }

    public PresentationPreviewResponse previewPresentation(PresentationPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generatePptContent(request.getUserInput(), request.getTopic());
        return markdownPreviewParser.parsePresentation(rawMarkdown, request.getTopic());
    }
}

