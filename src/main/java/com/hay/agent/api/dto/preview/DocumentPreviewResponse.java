package com.hay.agent.api.dto.preview;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class DocumentPreviewResponse {
    String artifactType;
    String title;
    String rawMarkdown;
    String generatedAt;
    List<OutlineItem> outline;
    List<Section> sections;
    List<String> warnings;

    @Value
    @Builder
    public static class OutlineItem {
        String id;
        int level;
        String title;
    }

    @Value
    @Builder
    public static class Section {
        String id;
        int level;
        String title;
        List<Block> blocks;
    }

    @Value
    @Builder
    public static class Block {
        String id;
        String type;
        String text;
        List<String> items;
    }
}

