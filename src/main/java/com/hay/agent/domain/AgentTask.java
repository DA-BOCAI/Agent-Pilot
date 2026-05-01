package com.hay.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * 任务
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentTask {
    private String taskId;
    private String requestId;
    private String userId;
    private String source;
    private String inputText;
    private TaskStatus status;
    private String nextAction;
    private Instant createdAt;
    private Instant updatedAt;

    @Builder.Default
    private List<PlanStep> planSteps = new ArrayList<>();

    @Builder.Default
    private List<Artifact> artifacts = new ArrayList<>();

    @Builder.Default
    private List<TaskEvent> events = new ArrayList<>();

    @Builder.Default
    private Map<String, String> cardMessageIds = new HashMap<>();
}

