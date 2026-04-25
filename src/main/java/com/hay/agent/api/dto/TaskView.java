package com.hay.agent.api.dto;

import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import lombok.Builder;
import lombok.Value;

import java.time.Instant;
import java.util.List;

@Value
@Builder
public class TaskView {
    String taskId;
    String requestId;
    String source;
    String userId;
    String inputText;
    TaskStatus status;
    String nextAction;
    Instant createdAt;
    Instant updatedAt;
    List<PlanStep> planSteps;
    List<Artifact> artifacts;
    List<TaskEvent> events;
}

