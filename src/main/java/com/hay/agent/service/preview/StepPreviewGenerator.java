package com.hay.agent.service.preview;

import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;

public interface StepPreviewGenerator {
    boolean supports(PlanStep step);

    Object generate(AgentTask task, PlanStep step, GenerateStepPreviewRequest request);

    String previewArtifactType();

    String previewTitle();

    String expectedPreviewDataType();
}
