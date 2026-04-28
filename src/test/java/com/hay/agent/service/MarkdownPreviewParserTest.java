package com.hay.agent.service;

import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class MarkdownPreviewParserTest {

    private final MarkdownPreviewParser parser = new MarkdownPreviewParser();

    @Test
    void parseDocumentShouldReturnStructuredSections() {
        String markdown = """
                # 产品方案
                ## 背景
                这是第一段
                - 要点A
                - 要点B

                ## 目标
                第二段
                """;

        DocumentPreviewResponse response = parser.parseDocument(markdown, "需求文档");

        assertEquals("DOCUMENT", response.getArtifactType());
        assertEquals("产品方案", response.getTitle());
        assertEquals(3, response.getOutline().size());
        assertEquals(3, response.getSections().size());
        assertEquals("产品方案", response.getSections().get(0).getTitle());
        assertEquals("背景", response.getSections().get(1).getTitle());
        assertFalse(response.getSections().get(1).getBlocks().isEmpty());
        assertNotNull(response.getRawMarkdown());
    }

    @Test
    void parsePresentationShouldReturnSlides() {
        String markdown = """
                ## 封面
                - 年度汇报
                - 2026

                ## 目录
                - 背景
                - 计划
                """;

        PresentationPreviewResponse response = parser.parsePresentation(markdown, "年度汇报PPT");

        assertEquals("PRESENTATION", response.getArtifactType());
        assertEquals("封面", response.getTitle());
        assertEquals(2, response.getPageCount());
        assertEquals(2, response.getSlides().size());
        assertEquals(2, response.getSlides().get(0).getBullets().size());
        assertNotNull(response.getRawMarkdown());
    }
}

