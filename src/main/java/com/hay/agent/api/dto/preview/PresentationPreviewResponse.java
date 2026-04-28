package com.hay.agent.api.dto.preview;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PresentationPreviewResponse {
    String artifactType;
    String title;
    String rawMarkdown;
    String generatedAt;
    int pageCount;
    List<Slide> slides;
    List<String> warnings;

    @Value
    @Builder
    public static class Slide {
        String id;
        int slideNo;
        String title;
        String bodyMarkdown;
        List<String> bullets;
    }
}

