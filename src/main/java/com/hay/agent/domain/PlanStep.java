package com.hay.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlanStep {
    private String stepId;
    private String scene;
    private String action;
    private String tool;
    private String description;
    private Map<String, Object> arguments;
    private List<String> dependsOn;
    private boolean requiresConfirm;
    private StepStatus status;
}

