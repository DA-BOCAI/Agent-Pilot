package com.hay.agent.api;

import com.hay.agent.api.dto.preview.DocumentPreviewRequest;
import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.service.ContentPreviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/previews")
@Tag(name = "文档与PPT预览接口", description = "将用户输入先转换为结构化 JSON 预览，便于前端精细化编辑")
public class ContentPreviewController {

    private final ContentPreviewService contentPreviewService;

    public ContentPreviewController(ContentPreviewService contentPreviewService) {
        this.contentPreviewService = contentPreviewService;
    }

    @PostMapping("/document")
    @Operation(summary = "预览文档", description = "根据用户输入生成文档预览 JSON，包含 rawMarkdown、目录和章节结构")
    public ResponseEntity<DocumentPreviewResponse> previewDocument(@Valid @RequestBody DocumentPreviewRequest request) {
        return ResponseEntity.ok(contentPreviewService.previewDocument(request));
    }

    @PostMapping("/presentation")
    @Operation(summary = "预览PPT", description = "根据用户输入生成PPT预览 JSON，按页返回 slide 结构")
    public ResponseEntity<PresentationPreviewResponse> previewPresentation(@Valid @RequestBody PresentationPreviewRequest request) {
        return ResponseEntity.ok(contentPreviewService.previewPresentation(request));
    }
}

