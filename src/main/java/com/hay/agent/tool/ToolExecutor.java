package com.hay.agent.tool;

import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;

import java.util.Optional;

/**
 * 工具执行接口
 */

public interface ToolExecutor {
    Optional<Artifact> execute(PlanStep step, String taskId, String inputText);
}

