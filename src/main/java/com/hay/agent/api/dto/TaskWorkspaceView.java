package com.hay.agent.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Value
@Builder
public class TaskWorkspaceView {
    String taskId;
    String title;
    String status;
    String displayStatus;
    String nextAction;
    String source;
    String sourceDisplay;
    String userId;
    Instant createdAt;
    Instant updatedAt;
    String inputSummary;
    String contextText;
    List<StepItem> steps;
    Progress progress;
    Confirmation confirmation;
    Preview preview;
    List<Preview> previews;
    Adjustments adjustments;
    List<Output> outputs;
    List<TimelineEvent> timeline;
    Sync sync;
    Diagnostics diagnostics;

    @Value
    @Builder
    public static class StepItem {
        String stepId;
        String code;
        String name;
        String action;
        String status;
        String displayStatus;
        String phase;
        boolean requiresConfirm;
        boolean active;
    }

    @Value
    @Builder
    public static class Progress {
        int percent;
        int phaseIndex;
        int totalPhases;
        String phase;
        String label;
    }

    @Value
    @Builder
    public static class Confirmation {
        boolean waiting;
        String phase;
        String stepId;
        String title;
        String description;
        String action;
        String artifactType;
        String theme;
        boolean previewReady;
        Preview preview;
    }

    @Value
    @Builder
    public static class Preview {
        boolean available;
        String type;
        String title;
        String theme;
        String stepId;
        JsonNode data;
    }

    @Value
    @Builder
    public static class Adjustments {
        boolean available;
        String stepId;
        String phase;
        List<AdjustmentAction> actions;
    }

    @Value
    @Builder
    public static class AdjustmentAction {
        String key;
        String label;
        String type;
        String target;
        List<String> options;
        String currentValue;
        String endpoint;
        String method;
    }

    @Value
    @Builder
    public static class Output {
        String type;
        String title;
        String url;
        String stepId;
    }

    @Value
    @Builder
    public static class TimelineEvent {
        Instant timestamp;
        String type;
        String title;
        String message;
        String level;
        String stepId;
        String source;
        String sourceDisplay;
        Map<String, String> metadata;
    }

    @Value
    @Builder
    public static class Sync {
        boolean realtimeEnabled;
        String snapshotEndpoint;
        String streamEndpoint;
        String lastEventId;
        Instant serverTime;
        long reconnectAfterMillis;
    }

    @Value
    @Builder
    public static class Diagnostics {
        String progressSource;
        int progressPercent;
        String progressPhase;
        int publicOutputCount;
        int hiddenInternalArtifactCount;
        int previewWarningCount;
        List<String> previewWarnings;
        Integer slidePageCount;
        boolean shouldUseBackendProgress;
    }
}
