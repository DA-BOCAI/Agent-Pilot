package com.hay.agent.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class TaskCardView {
    String taskId;
    String status;
    String nextAction;
    List<StepProgress> steps;
    ConfirmCard confirm;
    CompletionCard completion;

    @Value
    @Builder
    public static class StepProgress {
        String stepId;
        String action;
        String status;
        String displayStatus;
        boolean requiresConfirm;
    }

    @Value
    @Builder
    public static class ConfirmCard {
        boolean waiting;
        String stepId;
        String phase;
        String action;
        String artifactType;
        String recommendedTheme;
        JsonNode previewData;
    }

    @Value
    @Builder
    public static class CompletionCard {
        boolean finished;
        boolean failed;
        List<LinkItem> links;
        String message;
    }

    @Value
    @Builder
    public static class LinkItem {
        String type;
        String title;
        String url;
    }
}
