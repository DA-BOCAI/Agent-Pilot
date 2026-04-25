package com.hay.agent.tool;

import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 文件作用：工具执行器的 Mock 实现。
 * 项目角色：模拟文档、PPT、交付产物生成，方便在未接入真实 MCP 工具时先跑通流程。
 */

@Component
public class MockToolExecutor implements ToolExecutor {

    @Override
    public Optional<Artifact> execute(PlanStep step, String taskId, String inputText) {
        return switch (step.getStepId()) {
            case "C_DOC" -> Optional.of(Artifact.builder()
                    .type("doc")
                    .title("Requirement Draft")
                    .url("https://mock.lark/doc/" + taskId)
                    .build());
            case "D_SLIDES" -> Optional.of(Artifact.builder()
                    .type("slides")
                    .title("Pitch Deck")
                    .url("https://mock.lark/slides/" + taskId)
                    .build());
            case "F_DELIVER" -> Optional.of(Artifact.builder()
                    .type("delivery")
                    .title("Delivery Package")
                    .url("https://mock.lark/delivery/" + taskId)
                    .build());
            default -> Optional.empty();
        };
    }
}

