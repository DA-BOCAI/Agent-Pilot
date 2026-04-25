package com.hay.agent.planner;

import com.hay.agent.domain.PlanStep;

import java.util.List;

/**
 * 规划接口
 */

public interface Planner {
    List<PlanStep> plan(String inputText);
}

