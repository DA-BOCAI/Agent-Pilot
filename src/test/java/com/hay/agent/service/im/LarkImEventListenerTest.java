package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.mapper.TaskMapper;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class LarkImEventListenerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AgentTaskService agentTaskService;

    @Mock
    private AgentRunner agentRunner;

    @Mock
    private LarkTaskCardService larkTaskCardService;

    private ImIntentClassifier imIntentClassifier;
    private TaskMapper taskMapper;

    private LarkImEventListener listener;

    @BeforeEach
    void setUp() {
        imIntentClassifier = new ImIntentClassifier();
        taskMapper = new TaskMapper();
        listener = new LarkImEventListener(agentTaskService, agentRunner, larkTaskCardService, imIntentClassifier, taskMapper, objectMapper);
        ReflectionTestUtils.setField(listener, "replyEnabled", false);
        ReflectionTestUtils.setField(listener, "groupRequiresMention", true);
        ReflectionTestUtils.setField(listener, "botOpenId", "ou_bot");
        ReflectionTestUtils.setField(listener, "botUserId", "");
        ReflectionTestUtils.setField(listener, "discussionContextTtl", Duration.ofMinutes(30));
        ReflectionTestUtils.setField(listener, "maxDiscussionContextMessages", 8);
        ReflectionTestUtils.setField(listener, "workspaceUrl", "https://agent-pilot-nine.vercel.app");
        lenient().when(agentTaskService.createTask(any())).thenReturn(AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.CREATED)
                .build());
        lenient().when(agentRunner.runUntilBlocked("task-1")).thenReturn(AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.WAIT_CONFIRM)
                .build());
        lenient().when(agentTaskService.getTask("task-1")).thenReturn(waitingConfirm1Task());
    }

    @Test
    void shouldCreateTaskForSingleChatMessage() {
        listener.handleEvent(event("om_1", "p2p", "msg_1", "请帮我生成一份项目推进PPT", false));

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agentTaskService).createTask(captor.capture());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【IM任务输入】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【用户明确需求】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("请帮我生成一份项目推进PPT"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("单聊直接触发"));
        assertEquals("IM:p2p:oc_1", captor.getValue().getSource());
        assertEquals("msg_1", captor.getValue().getRequestId());
        assertEquals("ou_sender", captor.getValue().getUserId());
    }

    @Test
    void shouldIgnoreGroupMessageWithoutMention() {
        listener.handleEvent(event("om_2", "group", "msg_2", "大家看一下这个方案", false));

        verify(agentTaskService, never()).createTask(any());
        verify(agentRunner, never()).runUntilBlocked(any());
    }

    @Test
    void shouldCreateTaskForGroupMessageWithBotMention() {
        listener.handleEvent(event("om_3", "group", "msg_3", "@_user_1 帮我生成会议纪要和PPT", true));

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agentTaskService).createTask(captor.capture());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【用户明确需求】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("帮我生成会议纪要和PPT"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("群聊@机器人"));
        assertEquals("IM:group:oc_1", captor.getValue().getSource());
    }

    @Test
    void shouldIgnoreDuplicatedMessage() {
        listener.handleEvent(event("om_4", "p2p", "msg_4", "生成一份日报", false));
        listener.handleEvent(event("om_4", "p2p", "msg_4", "生成一份日报", false));

        verify(agentTaskService).createTask(any());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
    }

    @Test
    void shouldIgnoreDuplicatedTextEvenWhenMessageIdIsDifferent() {
        listener.handleEvent(event("om_5", "p2p", "msg_5", "生成一份日报", false));
        listener.handleEvent(event("om_6", "p2p", "msg_6", "生成一份日报", false));

        verify(agentTaskService).createTask(any());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
    }

    @Test
    void shouldUseRecentGroupDiscussionAsTaskContextWhenBotIsMentioned() {
        listener.handleEvent(event("om_7", "group", "msg_7", "我们要给校招宣讲准备一份材料，重点讲岗位亮点", false));
        listener.handleEvent(event("om_8", "group", "msg_8", "@_user_1 帮我们生成一份宣讲文档", true));

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agentTaskService).createTask(captor.capture());
        assertEquals("IM:group:oc_1", captor.getValue().getSource());
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【用户明确需求】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("帮我们生成一份宣讲文档"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【最近讨论上下文】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【讨论共识摘要】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("岗位亮点"));
    }

    @Test
    void shouldTreatNonTaskMessageAsSupplementWhenTaskIsActive() {
        listener.handleEvent(event("om_9", "p2p", "msg_9", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_10", "p2p", "msg_10", "补充一下，里面要强调时间风险", false));

        verify(agentTaskService).createTask(any());
        verify(agentTaskService, timeout(1000)).getTask("task-1");
        verify(agentTaskService, timeout(1000)).appendImSupplement("task-1", "补充一下，里面要强调时间风险", "msg_10");
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
    }

    @Test
    void shouldNotAcceptSupplementAfterPreviewIsReady() {
        lenient().when(agentTaskService.getTask("task-1")).thenReturn(waitingConfirm2Task());

        listener.handleEvent(event("om_14", "p2p", "msg_14", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_15", "p2p", "msg_15", "补充一下，里面要强调时间风险", false));

        verify(agentTaskService, timeout(1000)).getTask("task-1");
        verify(agentTaskService, never()).appendImSupplement("task-1", "补充一下，里面要强调时间风险", "msg_15");
    }

    @Test
    void shouldStartNewTaskWhenSupplementAddsAnotherArtifact() {
        listener.handleEvent(event("om_16", "p2p", "msg_16", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_17", "p2p", "msg_17", "补充一下，再加一份汇报PPT", false));

        verify(agentTaskService, timeout(1000).times(2)).createTask(any());
        verify(agentTaskService, never()).appendImSupplement("task-1", "补充一下，再加一份汇报PPT", "msg_17");
    }

    @Test
    void shouldIgnoreAcknowledgementWhenBotIsMentioned() {
        listener.handleEvent(event("om_11", "group", "msg_11", "@_user_1 收到，谢谢", true));

        verify(agentTaskService, never()).createTask(any());
        verify(agentTaskService, never()).appendImSupplement(any(), any(), any());
        verify(agentRunner, never()).runUntilBlocked(any());
    }

    @Test
    void shouldReplyProgressQueryWithoutCreatingNewTask() {
        listener.handleEvent(event("om_18", "p2p", "msg_18", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_19", "p2p", "msg_19", "现在进度到哪了？", false));

        verify(agentTaskService).createTask(any());
        verify(agentTaskService, timeout(1000)).getTask("task-1");
        verify(agentTaskService, never()).appendImSupplement("task-1", "现在进度到哪了？", "msg_19");
    }

    @Test
    void shouldAllowGroupProgressQueryWithoutMentionWhenTaskExists() {
        listener.handleEvent(event("om_28", "group", "msg_28", "@_user_1 请帮我生成一份项目推进文档", true));
        listener.handleEvent(event("om_29", "group", "msg_29", "怎么样了？", false));

        verify(agentTaskService).createTask(any());
        verify(agentTaskService, timeout(1000)).getTask("task-1");
        verify(agentTaskService, never()).appendImSupplement("task-1", "怎么样了？", "msg_29");
    }

    @Test
    void shouldReplyHelpQueryWithoutCreatingTask() {
        listener.handleEvent(event("om_22", "p2p", "msg_22", "你能做什么，怎么用？", false));

        verify(agentTaskService, never()).createTask(any());
        verify(agentTaskService, never()).appendImSupplement(any(), any(), any());
        verify(agentRunner, never()).runUntilBlocked(any());
        org.junit.jupiter.api.Assertions.assertTrue(listener.buildHelpReply().contains("可确认、可预览、可交付"));
    }

    @Test
    void shouldReplyCapabilityQuestionWithoutCreatingTask() {
        listener.handleEvent(event("om_22b", "p2p", "msg_22b", "你能帮我做个PPT吗？", false));

        verify(agentTaskService, never()).createTask(any());
        verify(agentTaskService, never()).appendImSupplement(any(), any(), any());
        verify(agentRunner, never()).runUntilBlocked(any());
    }

    @Test
    void shouldAskClarificationForUnderspecifiedPptRequest() {
        listener.handleEvent(event("om_23", "p2p", "msg_23", "帮我做个PPT", false));

        verify(agentTaskService, never()).createTask(any());
        verify(agentRunner, never()).runUntilBlocked(any());
        org.junit.jupiter.api.Assertions.assertTrue(listener.buildClarificationReply("帮我做个PPT").contains("主题或项目名称"));
    }

    @Test
    void shouldCreateTaskFromClarificationReply() {
        listener.handleEvent(event("om_24", "p2p", "msg_24", "帮我做个PPT", false));
        listener.handleEvent(event("om_25", "p2p", "msg_25", "主题是AI Agent项目复盘，面向管理层，包含进展、风险和下一步", false));

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agentTaskService, timeout(1000)).createTask(captor.capture());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("【原始模糊需求】"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("帮我做个PPT"));
        org.junit.jupiter.api.Assertions.assertTrue(captor.getValue().getInputText().contains("主题是AI Agent项目复盘"));
        assertEquals("msg_25", captor.getValue().getRequestId());
    }

    @Test
    void shouldCancelActiveTaskFromImMessage() {
        AgentTask cancelledTask = AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.FAILED)
                .build();
        lenient().when(agentTaskService.cancelTask(
                        org.mockito.Mockito.eq("task-1"),
                        org.mockito.Mockito.contains("IM 消息取消"),
                        org.mockito.Mockito.eq("im"),
                        org.mockito.Mockito.eq("msg_20")))
                .thenReturn(cancelledTask);

        listener.handleEvent(event("om_20", "p2p", "msg_20a", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_21", "p2p", "msg_20", "取消当前任务", false));

        verify(agentTaskService).createTask(any());
        verify(agentTaskService, timeout(1000)).cancelTask(
                org.mockito.Mockito.eq("task-1"),
                org.mockito.Mockito.contains("IM 消息取消"),
                org.mockito.Mockito.eq("im"),
                org.mockito.Mockito.eq("msg_20"));
        verify(larkTaskCardService, timeout(1000)).sendCardsForCurrentState("oc_1", cancelledTask);
        verify(agentTaskService, never()).appendImSupplement("task-1", "取消当前任务", "msg_20");
    }

    @Test
    void shouldRetryLatestFailedTaskFromImMessage() {
        AgentTask failedTask = failedTask();
        lenient().when(agentTaskService.getTask("task-1")).thenReturn(failedTask);
        lenient().when(agentTaskService.createTask(any()))
                .thenReturn(AgentTask.builder().taskId("task-1").status(TaskStatus.CREATED).build())
                .thenReturn(AgentTask.builder().taskId("task-2").status(TaskStatus.CREATED).build());
        lenient().when(agentRunner.runUntilBlocked("task-1")).thenReturn(failedTask);
        lenient().when(agentRunner.runUntilBlocked("task-2")).thenReturn(waitingConfirm1Task());

        listener.handleEvent(event("om_26", "p2p", "msg_26", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_27", "p2p", "msg_27", "重试", false));

        ArgumentCaptor<CreateTaskRequest> captor = ArgumentCaptor.forClass(CreateTaskRequest.class);
        verify(agentTaskService, timeout(1000).times(2)).createTask(captor.capture());
        CreateTaskRequest retryRequest = captor.getAllValues().get(1);
        org.junit.jupiter.api.Assertions.assertTrue(retryRequest.getInputText().contains("【IM重试信息】"));
        org.junit.jupiter.api.Assertions.assertTrue(retryRequest.getInputText().contains("原任务ID：task-1"));
        assertEquals("msg_27", retryRequest.getRequestId());
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-2");
    }

    @Test
    void shouldSendFailureCardWhenInitialPlanningFails() {
        AgentTask failedTask = failedTask();
        lenient().when(agentRunner.runUntilBlocked("task-1"))
                .thenThrow(new RuntimeException("429 Too Many Requests"));
        lenient().when(agentTaskService.getTask("task-1")).thenReturn(failedTask);

        listener.handleEvent(event("om_28", "p2p", "msg_28", "请生成项目汇报PPT", false));

        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
        verify(agentTaskService, timeout(1000)).getTask("task-1");
        verify(larkTaskCardService, timeout(1000)).sendCardsForCurrentState("oc_1", failedTask);
    }

    @Test
    void progressReplyShouldSummarizeWorkspaceState() {
        var workspace = taskMapper.toWorkspaceView(waitingConfirm2Task());

        String reply = listener.buildProgressReply(workspace);

        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("当前任务进度：60%"));
        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("等待确认：confirm2"));
        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("预览：已生成"));
        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("工作台：https://agent-pilot-nine.vercel.app?taskId=task-1"));
    }

    @Test
    void progressReplyShouldExplainFailureReason() {
        AgentTask failedTask = failedTask();
        lenient().when(agentTaskService.getTask("task-1")).thenReturn(failedTask);
        var workspace = taskMapper.toWorkspaceView(failedTask);

        String reply = listener.buildProgressReply(workspace);

        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("失败原因：大模型请求受限"));
        org.junit.jupiter.api.Assertions.assertTrue(reply.contains("工作台：https://agent-pilot-nine.vercel.app?taskId=task-1"));
    }

    @Test
    void shouldStartNewTaskWhenActiveTaskExistsButMessageClearlyRequestsAnotherArtifact() {
        listener.handleEvent(event("om_12", "p2p", "msg_12", "请帮我生成一份项目推进文档", false));
        listener.handleEvent(event("om_13", "p2p", "msg_13", "再帮我生成一份汇报PPT", false));

        verify(agentTaskService, timeout(1000).times(2)).createTask(any());
        verify(agentTaskService, never()).appendImSupplement("task-1", "再帮我生成一份汇报PPT", "msg_13");
    }

    private ObjectNode event(String openMessageId,
                             String chatType,
                             String messageId,
                             String text,
                             boolean mentionBot) {
        ObjectNode root = objectMapper.createObjectNode();
        root.putObject("header").put("event_type", "im.message.receive_v1");
        ObjectNode event = root.putObject("event");
        event.putObject("sender").putObject("sender_id").put("open_id", "ou_sender");

        ObjectNode message = event.putObject("message");
        message.put("open_message_id", openMessageId);
        message.put("message_id", messageId);
        message.put("message_type", "text");
        message.put("chat_id", "oc_1");
        message.put("chat_type", chatType);
        message.put("content", objectMapper.createObjectNode().put("text", text).toString());
        if (mentionBot) {
            message.putArray("mentions")
                    .addObject()
                    .putObject("id")
                    .put("open_id", "ou_bot");
        }
        return root;
    }

    private AgentTask waitingConfirm1Task() {
        return AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.WAIT_CONFIRM)
                .planSteps(List.of(PlanStep.builder()
                        .stepId("C_DOC")
                        .action("创建项目推进文档")
                        .status(StepStatus.WAIT_CONFIRM)
                        .requiresConfirm(true)
                        .build()))
                .build();
    }

    private AgentTask waitingConfirm2Task() {
        PlanStep step = PlanStep.builder()
                .stepId("C_DOC")
                .action("创建项目推进文档")
                .status(StepStatus.WAIT_CONFIRM)
                .requiresConfirm(true)
                .build();
        step.setPreviewData(objectMapper.createObjectNode()
                .put("artifactType", "DOCUMENT")
                .put("title", "项目推进文档预览"));
        return AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.WAIT_CONFIRM)
                .planSteps(List.of(step))
                .build();
    }

    private AgentTask failedTask() {
        return AgentTask.builder()
                .taskId("task-1")
                .requestId("req-1")
                .userId("ou_sender")
                .source("IM:p2p:oc_1")
                .inputText("【IM任务输入】\n【用户明确需求】\n请帮我生成一份项目推进文档")
                .status(TaskStatus.FAILED)
                .nextAction("none")
                .updatedAt(Instant.now())
                .events(List.of(TaskEvent.builder()
                        .timestamp(Instant.now())
                        .type("STEP_PREVIEW_FAILED")
                        .message("步骤预览生成失败：大模型请求受限")
                        .metadata(Map.of(
                                "failureKind", "LLM_RATE_LIMIT",
                                "userMessage", "大模型请求受限，可能触发 TPM/限流。请稍等片刻后重试，或减少一次生成的内容规模。"
                        ))
                        .build()))
                .build();
    }
}
