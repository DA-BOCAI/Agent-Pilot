package com.hay.agent.planner;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LlmPlannerTest {

    @Test
    void shouldInsertDocStepBeforeSlidesForComplexPresentationRequest() {
        LlmPlanner planner = new LlmPlanner(RestClient.builder(), new ObjectMapper(),
                "https://example.com", "key", "model", 0.0, Duration.ofSeconds(1), Duration.ofSeconds(1));
        List<PlanStep> steps = new ArrayList<>(List.of(
                PlanStep.builder()
                        .stepId("D_SLIDES")
                        .scene("D")
                        .action("生成校招宣讲PPT")
                        .tool("lark-slides")
                        .requiresConfirm(true)
                        .status(StepStatus.PENDING)
                        .build(),
                PlanStep.builder()
                        .stepId("F_DELIVER")
                        .scene("F")
                        .action("交付结果")
                        .tool("none")
                        .requiresConfirm(false)
                        .status(StepStatus.PENDING)
                        .build()
        ));

        ReflectionTestUtils.invokeMethod(planner, "normalizeSteps", steps, "帮我们生成一份校招宣讲PPT，面向销售团队，包含岗位亮点和培养机制");

        assertEquals("C_DOC", steps.get(0).getStepId());
        assertEquals("D_SLIDES", steps.get(1).getStepId());
        assertEquals("基于前序方案文档生成演示文稿", steps.get(1).getAction());
        assertEquals(true, steps.get(0).isRequiresConfirm());
        assertEquals(true, steps.get(1).isRequiresConfirm());
    }

    @Test
    void shouldMoveExistingDocStepBeforeSlides() {
        LlmPlanner planner = new LlmPlanner(RestClient.builder(), new ObjectMapper(),
                "https://example.com", "key", "model", 0.0, Duration.ofSeconds(1), Duration.ofSeconds(1));
        List<PlanStep> steps = new ArrayList<>(List.of(
                PlanStep.builder().stepId("D_SLIDES").scene("D").action("生成发布会PPT").tool("lark-slides").build(),
                PlanStep.builder().stepId("C_DOC").scene("C").action("生成发布会方案文档").tool("lark-doc").build()
        ));

        ReflectionTestUtils.invokeMethod(planner, "normalizeSteps", steps, "生成新品发布会方案PPT");

        assertEquals("C_DOC", steps.get(0).getStepId());
        assertEquals("D_SLIDES", steps.get(1).getStepId());
    }
}
