package com.hay.agent.api;

import com.hay.agent.api.dto.preview.DocumentPreviewResponse;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.hay.agent.service.ContentPreviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ContentPreviewController.class)
class ContentPreviewControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContentPreviewService contentPreviewService;

    @Test
    void previewDocumentShouldReturnJsonPreview() throws Exception {
        when(contentPreviewService.previewDocument(any())).thenReturn(DocumentPreviewResponse.builder()
                .artifactType("DOCUMENT")
                .title("产品方案")
                .rawMarkdown("# 产品方案")
                .generatedAt("2026-04-27T00:00:00Z")
                .outline(List.of())
                .sections(List.of())
                .warnings(List.of())
                .build());

        mockMvc.perform(post("/api/v1/previews/document")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userInput": "请生成一份产品方案",
                                  "docType": "需求文档"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactType").value("DOCUMENT"))
                .andExpect(jsonPath("$.title").value("产品方案"))
                .andExpect(jsonPath("$.rawMarkdown").value("# 产品方案"));
    }

    @Test
    void previewPresentationShouldReturnJsonPreview() throws Exception {
        when(contentPreviewService.previewPresentation(any())).thenReturn(PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title("年度汇报")
                .rawMarkdown("## 封面")
                .generatedAt("2026-04-27T00:00:00Z")
                .pageCount(1)
                .slides(List.of())
                .warnings(List.of())
                .build());

        mockMvc.perform(post("/api/v1/previews/presentation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "userInput": "请生成一份年度汇报",
                                  "topic": "年度汇报"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.artifactType").value("PRESENTATION"))
                .andExpect(jsonPath("$.title").value("年度汇报"))
                .andExpect(jsonPath("$.rawMarkdown").value("## 封面"));
    }
}

