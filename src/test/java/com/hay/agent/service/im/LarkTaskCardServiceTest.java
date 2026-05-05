package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.service.AgentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LarkTaskCardServiceTest {

    private LarkTaskCardService cardService;

    @BeforeEach
    void setUp() {
        cardService = new LarkTaskCardService(new ObjectMapper(), Mockito.mock(AgentTaskService.class));
        ReflectionTestUtils.setField(cardService, "workspaceUrl", "https://agent-pilot-nine.vercel.app");
    }

    @Test
    void shouldBuildDynamicPlanCardFromTaskSteps() {
        ObjectNode card = cardService.buildPlanCard(task(TaskStatus.WAIT_CONFIRM));

        assertTrue(card.path("_template_id").isMissingNode());
        assertEquals("Agent 任务编排中...", card.at("/header/title/content").asText());
        assertTrue(card.toString().contains("当前阶段"));
        assertTrue(card.toString().contains("Agent"));
        assertTrue(card.toString().contains("下一步"));
        assertTrue(card.toString().contains("你可以做"));
        assertTrue(card.toString().contains("执行路径"));
        assertTrue(card.at("/body/elements/5/content").asText().contains("生成任务大纲"));
        assertTrue(card.at("/body/elements/6/content").asText().contains("等待确认"));
        assertTrue(card.toString().contains("可以先在"));
        assertTrue(card.toString().contains("补充要求"));
        assertTrue(card.toString().contains("task-1"));
        assertNoVisibleEscapes(card);
    }

    @Test
    void shouldBuildConfirmCardWithConfirmActionPayload() {
        ObjectNode card = cardService.buildConfirmCard(task(TaskStatus.WAIT_CONFIRM));

        assertTrue(card.path("_template_id").isMissingNode());
        assertEquals("等待确认", card.at("/header/title/content").asText());
        assertTrue(card.at("/body/elements/0/content").asText().contains("confirm1"));
        assertTrue(card.at("/body/elements/1/content").asText().contains("宣讲PPT"));
        assertTrue(card.at("/body/elements/2/content").asText().contains("生成一份"));
        assertTrue(card.at("/body/elements/2/content").asText().contains("PPT"));
        assertTrue(card.at("/body/elements/3/content").asText().contains("请重点核对"));
        assertTrue(card.at("/body/elements/4/content").asText().contains("如果理解有误"));
        assertTrue(card.at("/body/elements/5/content").asText().contains("结构化预览"));
        assertTrue(card.toString().contains("当前阶段"));
        assertTrue(card.toString().contains("下一步"));
        assertTrue(card.toString().contains("你可以做"));
        assertEquals("confirm", card.at("/body/elements/9/columns/0/elements/0/behaviors/0/value/action").asText());
        assertEquals("D_SLIDES", card.at("/body/elements/9/columns/0/elements/0/behaviors/0/value/stepId").asText());
        assertTrue(card.toString().contains("task-1"));
        assertFalse(card.toString().contains("拟执行动作"));
        assertFalse(card.toString().contains("可补充说明"));
        assertFalse(card.at("/body").toString().contains("任务ID"));
        assertFalse(card.at("/body").toString().contains("ou_"));
        assertNoVisibleEscapes(card);
    }

    @Test
    void shouldCleanInternalIdsFromFallbackRequirementSummary() {
        AgentTask task = task(TaskStatus.WAIT_CONFIRM);
        task.setInputText("""
                【触发用户】ou_97b48672e290086bd17e8ead29b9647e
                【触发消息ID】msg_123456abcdef
                请帮我生成一份项目复盘 PPT，面向管理层。
                """);

        ObjectNode card = cardService.buildConfirmCard(task);

        String body = card.at("/body").toString();
        assertTrue(body.contains("项目复盘"));
        assertFalse(body.contains("ou_97b48672e290086bd17e8ead29b9647e"));
        assertFalse(body.contains("msg_123456abcdef"));
    }

    @Test
    void shouldDistinguishResolvedConfirm1AndConfirm2Cards() {
        AgentTask task = task(TaskStatus.WAIT_CONFIRM);
        PlanStep step = task.getPlanSteps().get(1);

        ObjectNode confirm1Card = cardService.buildResolvedConfirmCard(task, step, true);
        step.setPreviewData(new ObjectMapper().createObjectNode()
                .put("artifactType", "PRESENTATION")
                .put("title", "预览标题"));
        ObjectNode confirm2Card = cardService.buildResolvedConfirmCard(task, step, true);

        assertTrue(confirm1Card.at("/header/title/content").asText().contains("需求已确认"));
        assertTrue(confirm1Card.toString().contains("confirm1"));
        assertTrue(confirm1Card.toString().contains("生成结构化预览"));
        assertTrue(confirm2Card.at("/header/title/content").asText().contains("预览已确认"));
        assertTrue(confirm2Card.toString().contains("confirm2"));
        assertTrue(confirm2Card.toString().contains("正式创建飞书产物"));
    }

    @Test
    void shouldBuildCompletionCardWithArtifactLink() {
        AgentTask task = task(TaskStatus.DELIVERED);
        task.setArtifacts(List.of(Artifact.builder()
                .type("slides")
                .title("项目方案PPT")
                .url("https://example.feishu.cn/slides/abc")
                .build()));

        ObjectNode card = cardService.buildCompletionCard(task);

        assertTrue(card.path("_template_id").isMissingNode());
        assertEquals("任务已完成~", card.at("/header/title/content").asText());
        assertTrue(card.toString().contains("https://example.feishu.cn/slides/abc"));
        assertNoVisibleEscapes(card);
    }

    @Test
    void shouldPreferSlidesLinkOnCompletionCardWhenDocAndPptBothExist() {
        AgentTask task = task(TaskStatus.DELIVERED);
        task.setArtifacts(List.of(
                Artifact.builder()
                        .type("docs")
                        .title("项目方案文档")
                        .url("https://example.feishu.cn/docx/doc-abc")
                        .build(),
                Artifact.builder()
                        .type("slides")
                        .title("项目方案PPT")
                        .url("https://example.feishu.cn/slides/ppt-abc")
                        .build()
        ));

        ObjectNode card = cardService.buildCompletionCard(task);

        assertTrue(card.toString().contains("https://example.feishu.cn/slides/ppt-abc"));
        assertFalse(card.toString().contains("https://example.feishu.cn/docx/doc-abc"));
        assertTrue(card.toString().contains("查看"));
        assertTrue(card.toString().contains("PPT"));
        assertNoVisibleEscapes(card);
    }

    @Test
    void shouldHideCancelActionAndShowCancelledCompletionAfterUserCancellation() {
        AgentTask task = task(TaskStatus.FAILED);
        task.setEvents(List.of(TaskEvent.builder()
                .type("TASK_CANCELLED")
                .message("用户取消")
                .metadata(Map.of("source", "lark_card"))
                .build()));

        ObjectNode planCard = cardService.buildPlanCard(task);
        ObjectNode completionCard = cardService.buildCompletionCard(task);

        assertFalse(planCard.toString().contains("\"action\":\"cancel\""));
        assertTrue(completionCard.at("/header/title/content").asText().contains("任务已取消"));
        assertTrue(completionCard.at("/body/elements/0/content").asText().contains("不会继续创建"));
        assertFalse(completionCard.toString().contains("任务失败"));
        assertNoVisibleEscapes(planCard);
        assertNoVisibleEscapes(completionCard);
    }

    @Test
    void shouldShowModelRateLimitFailureOnCompletionCard() {
        AgentTask task = task(TaskStatus.FAILED);
        task.setEvents(List.of(TaskEvent.builder()
                .type("STEP_PREVIEW_FAILED")
                .message("步骤预览生成失败：大模型请求受限，可能触发 TPM/限流。")
                .metadata(Map.of(
                        "failureKind", "LLM_RATE_LIMIT",
                        "userMessage", "大模型请求受限，可能触发 TPM/限流。请稍等片刻后重试，或减少一次生成的内容规模。",
                        "retryAdvice", "建议等待 1-3 分钟后重试；如果连续出现，请减少页数/篇幅或切换可用模型。"
                ))
                .build()));

        ObjectNode completionCard = cardService.buildCompletionCard(task);

        assertEquals("任务失败", completionCard.at("/header/title/content").asText());
        assertTrue(completionCard.toString().contains("大模型请求受限"));
        assertTrue(completionCard.toString().contains("建议等待"));
        assertTrue(completionCard.toString().contains("重试"));
        assertNoVisibleEscapes(completionCard);
    }

    private void assertNoVisibleEscapes(ObjectNode card) {
        String json = card.toString();
        assertFalse(json.contains("\\n"));
        assertFalse(json.contains("\\u00A0"));
    }

    private AgentTask task(TaskStatus status) {
        return AgentTask.builder()
                .taskId("task-1")
                .inputText("【触发用户】ou_1\n【用户明确需求】\n帮我生成一份产品发布会宣讲PPT，面向销售团队。\n\n【执行约束】\n只生成一个主要办公产物。")
                .status(status)
                .nextAction("confirm:D_SLIDES")
                .planSteps(List.of(
                        PlanStep.builder()
                                .stepId("B_PLAN")
                                .action("生成任务大纲")
                                .status(StepStatus.DONE)
                                .requiresConfirm(false)
                                .build(),
                        PlanStep.builder()
                                .stepId("D_SLIDES")
                                .action("创建宣讲PPT初稿")
                                .status(status == TaskStatus.DELIVERED ? StepStatus.DONE : StepStatus.WAIT_CONFIRM)
                                .requiresConfirm(true)
                                .build()
                ))
                .build();
    }
}
