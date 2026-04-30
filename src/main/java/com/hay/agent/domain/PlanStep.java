package com.hay.agent.domain;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {
    private String stepId;
    private String scene;
    private String action;
    private String tool;
    private boolean requiresConfirm;
    private StepStatus status;
    private JsonNode previewData;
}

