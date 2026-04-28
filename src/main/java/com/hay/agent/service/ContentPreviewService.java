package com.hay.agent.service;

import com.hay.agent.api.dto.preview.DocumentPreviewRequest;
import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.service.presentation.PresentationMarkdownParser;
import com.hay.agent.service.presentation.PresentationSlide;
import com.hay.agent.service.presentation.PresentationTheme;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class ContentPreviewService {

    private final ContentGeneratorService contentGeneratorService;
    private final MarkdownPreviewParser markdownPreviewParser;
    private final PresentationMarkdownParser presentationMarkdownParser;

    public ContentPreviewService(ContentGeneratorService contentGeneratorService,
                                 MarkdownPreviewParser markdownPreviewParser,
                                 PresentationMarkdownParser presentationMarkdownParser) {
        this.contentGeneratorService = contentGeneratorService;
        this.markdownPreviewParser = markdownPreviewParser;
        this.presentationMarkdownParser = presentationMarkdownParser;
    }

    public DocumentPreviewResponse previewDocument(DocumentPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generateDocContent(request.getUserInput(), request.getDocType());
        return markdownPreviewParser.parseDocument(rawMarkdown, request.getDocType());
    }

    public PresentationPreviewResponse previewPresentation(PresentationPreviewRequest request) {
        String rawMarkdown = contentGeneratorService.generatePptContent(request.getUserInput(), request.getTopic());
        PresentationTheme theme = request.getTheme() == null || request.getTheme().isBlank()
                ? PresentationTheme.fromText(request.getUserInput() + "\n" + request.getTopic())
                : PresentationTheme.fromText(request.getTheme());
        List<PresentationSlide> slides = presentationMarkdownParser.parse(rawMarkdown, request.getTopic());
        return PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title(resolvePreviewTitle(slides, request.getTopic()))
                .rawMarkdown(rawMarkdown)
                .generatedAt(Instant.now().toString())
                .theme(theme.getCode())
                .pageCount(slides.size())
                .slides(slides.stream().map(this::toPreviewSlide).toList())
                .warnings(List.of())
                .build();
    }

    private String resolvePreviewTitle(List<PresentationSlide> slides, String fallbackTitle) {
        if (slides == null || slides.isEmpty() || slides.get(0).getTitle() == null || slides.get(0).getTitle().isBlank()) {
            return fallbackTitle;
        }
        return slides.get(0).getTitle();
    }

    private PresentationPreviewResponse.Slide toPreviewSlide(PresentationSlide slide) {
        List<String> bullets = slide.getBlocks().stream()
                .filter(block -> "bullets".equals(block.getType()))
                .flatMap(block -> block.getItems().stream())
                .toList();
        String bodyMarkdown = slide.getBlocks().stream()
                .map(block -> {
                    if ("paragraph".equals(block.getType())) {
                        return block.getText();
                    }
                    if ("bullets".equals(block.getType())) {
                        return String.join("\n", block.getItems().stream().map(item -> "- " + item).toList());
                    }
                    if ("table".equals(block.getType())) {
                        return String.join("\n", block.getRows().stream().map(row -> "| " + String.join(" | ", row) + " |").toList());
                    }
                    return "";
                })
                .filter(text -> text != null && !text.isBlank())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");

        return PresentationPreviewResponse.Slide.builder()
                .id("slide-" + slide.getSlideNo())
                .slideNo(slide.getSlideNo())
                .title(slide.getTitle())
                .bodyMarkdown(bodyMarkdown)
                .bullets(bullets)
                .blocks(slide.getBlocks().stream().map(this::toPreviewBlock).toList())
                .build();
    }

    private PresentationPreviewResponse.Block toPreviewBlock(PresentationSlide.SlideBlock block) {
        return PresentationPreviewResponse.Block.builder()
                .type(block.getType())
                .text(block.getText())
                .items(block.getItems())
                .rows(block.getRows())
                .build();
    }
}

