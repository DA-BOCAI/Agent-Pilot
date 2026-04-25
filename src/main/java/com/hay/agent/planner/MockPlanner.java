package com.hay.agent.planner;

import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 文件作用：任务规划器的 Mock 实现。
 * 项目角色：在接入真实大模型规划前，返回稳定可预测的步骤清单，保证联调和演示可控。
 */

@Component
public class MockPlanner implements Planner {

    @Override
    public List<PlanStep> plan(String inputText) {
        return List.of(
                PlanStep.builder()
                        .stepId("A_CAPTURE")
                        .scene("A")
                        .action("接收并标准化用户意图")
                        .tool("none")
                        .requiresConfirm(false)
                        .status(StepStatus.PENDING)
                        .build(),
                PlanStep.builder()
                        .stepId("B_PLAN")
                        .scene("B")
                        .action("生成任务执行计划")
                        .tool("planner")
                        .requiresConfirm(false)
                        .status(StepStatus.PENDING)
                        .build(),
                PlanStep.builder()
                        .stepId("C_DOC")
                        .scene("C")
                        .action("生成需求文档初稿")
                        .tool("lark-doc")
                        .requiresConfirm(true)
                        .status(StepStatus.PENDING)
                        .build(),
                PlanStep.builder()
                        .stepId("D_SLIDES")
                        .scene("D")
                        .action("生成演示文稿")
                        .tool("lark-slides")
                        .requiresConfirm(false)
                        .status(StepStatus.PENDING)
                        .build(),
                PlanStep.builder()
                        .stepId("F_DELIVER")
                        .scene("F")
                        .action("发布并输出交付结果")
                        .tool("lark-task")
                        .requiresConfirm(true)
                        .status(StepStatus.PENDING)
                        .build()
        );
    }
}

