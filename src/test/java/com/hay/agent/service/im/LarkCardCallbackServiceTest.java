package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LarkCardCallbackServiceTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    private AgentTaskService agentTaskService;

    @Mock
    private AgentRunner agentRunner;

    @Mock
    private LarkTaskCardService larkTaskCardService;

    private LarkCardCallbackService callbackService;

    @BeforeEach
    void setUp() {
        callbackService = new LarkCardCallbackService(agentTaskService, agentRunner, larkTaskCardService, objectMapper);
    }

    @Test
    void shouldConfirmStepAndContinueRunning() {
        AgentTask waitingTask = waitingTask();
        AgentTask advancedTask = AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.DELIVERED)
                .source("IM:group:oc_1")
                .build();
        when(agentTaskService.getTask("task-1")).thenReturn(waitingTask);
        when(agentTaskService.confirmStep(org.mockito.Mockito.eq("task-1"), org.mockito.Mockito.any())).thenReturn(waitingTask);
        when(agentRunner.runUntilBlocked("task-1")).thenReturn(advancedTask);

        ObjectNode response = callbackService.handleCallback(callbackPayload("confirm", "task-1", "D_SLIDES"));

        assertEquals("success", response.at("/toast/type").asText());
        ArgumentCaptor<ConfirmTaskRequest> captor = ArgumentCaptor.forClass(ConfirmTaskRequest.class);
        verify(agentTaskService, timeout(1000)).confirmStep(org.mockito.Mockito.eq("task-1"), captor.capture());
        assertEquals("D_SLIDES", captor.getValue().getStepId());
        assertEquals(true, captor.getValue().getApproved());
        verify(larkTaskCardService, timeout(1000)).updateConfirmCardResolved(org.mockito.Mockito.eq("oc_1"), org.mockito.Mockito.eq(waitingTask), org.mockito.Mockito.any(), org.mockito.Mockito.eq(true));
        verify(larkTaskCardService, timeout(1000)).sendFollowUpCardsForCurrentState("oc_1", waitingTask);
        verify(agentRunner, timeout(1000)).runUntilBlocked("task-1");
        verify(larkTaskCardService, timeout(1000)).sendFollowUpCardsForCurrentState("oc_1", advancedTask);
    }

    @Test
    void shouldCancelCurrentWaitingStepWhenStepIdMissing() {
        AgentTask waitingTask = waitingTask();
        when(agentTaskService.getTask("task-1")).thenReturn(waitingTask);
        when(agentTaskService.cancelTask(org.mockito.Mockito.eq("task-1"), org.mockito.Mockito.anyString())).thenReturn(AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.FAILED)
                .source("IM:group:oc_1")
                .build());

        ObjectNode response = callbackService.handleCallback(callbackPayload("cancel", "task-1", ""));

        assertEquals("success", response.at("/toast/type").asText());
        verify(agentTaskService, timeout(1000)).cancelTask(org.mockito.Mockito.eq("task-1"), org.mockito.Mockito.contains("飞书卡片取消"));
        verify(larkTaskCardService, timeout(1000)).updateConfirmCardResolved(org.mockito.Mockito.eq("oc_1"), org.mockito.Mockito.eq(waitingTask), org.mockito.Mockito.any(), org.mockito.Mockito.eq(false));
    }

    @Test
    void shouldReturnChallengeForLarkUrlVerification() {
        ObjectNode payload = objectMapper.createObjectNode().put("challenge", "abc");

        ObjectNode response = callbackService.handleCallback(payload);

        assertEquals("abc", response.path("challenge").asText());
    }

    @Test
    void shouldIgnoreStaleConfirm1CallbackWhenCurrentStageIsConfirm2() {
        AgentTask confirm2Task = waitingTask();
        confirm2Task.getPlanSteps().get(0).setPreviewData(objectMapper.createObjectNode()
                .put("artifactType", "PRESENTATION")
                .put("title", "预览标题"));
        when(agentTaskService.getTask("task-1")).thenReturn(confirm2Task);

        ObjectNode response = callbackService.handleCallback(callbackPayload("confirm", "task-1", "D_SLIDES", "confirm1"));

        assertEquals("success", response.at("/toast/type").asText());
        verify(agentTaskService, after(300).never()).confirmStep(org.mockito.Mockito.eq("task-1"), org.mockito.Mockito.any());
        verify(agentRunner, after(300).never()).runUntilBlocked("task-1");
        verify(larkTaskCardService, timeout(1000)).sendFollowUpCardsForCurrentState("oc_1", confirm2Task);
    }

    @Test
    void shouldResolveConfirmCardEvenWhenCallbackHasNoChatId() {
        AgentTask waitingTask = waitingTask();
        waitingTask.setTaskId("task-no-chat");
        waitingTask.setSource("im_text");
        waitingTask.getPlanSteps().get(0).setPreviewData(objectMapper.createObjectNode()
                .put("artifactType", "PRESENTATION")
                .put("title", "预览标题"));
        AgentTask advancedTask = AgentTask.builder()
                .taskId("task-no-chat")
                .status(TaskStatus.DELIVERED)
                .source("im_text")
                .build();
        when(agentTaskService.getTask("task-no-chat")).thenReturn(waitingTask);
        when(agentTaskService.confirmStep(org.mockito.Mockito.eq("task-no-chat"), org.mockito.Mockito.any())).thenReturn(waitingTask);
        when(agentRunner.runUntilBlocked("task-no-chat")).thenReturn(advancedTask);

        ObjectNode response = callbackService.handleCallback(callbackPayloadWithoutChat("confirm", "task-no-chat", "D_SLIDES", "confirm2"));

        assertEquals("success", response.at("/toast/type").asText());
        verify(agentTaskService, timeout(5000)).confirmStep(org.mockito.Mockito.eq("task-no-chat"), org.mockito.Mockito.any());
        verify(larkTaskCardService, timeout(1000)).updateConfirmCardResolved(
                org.mockito.Mockito.eq(""),
                org.mockito.Mockito.eq(waitingTask),
                org.mockito.Mockito.any(),
                org.mockito.Mockito.eq(true));
        verify(agentRunner, timeout(5000)).runUntilBlocked("task-no-chat");
    }

    private AgentTask waitingTask() {
        return AgentTask.builder()
                .taskId("task-1")
                .status(TaskStatus.WAIT_CONFIRM)
                .source("IM:group:oc_1")
                .planSteps(List.of(PlanStep.builder()
                        .stepId("D_SLIDES")
                        .action("创建宣讲PPT")
                        .requiresConfirm(true)
                        .status(StepStatus.WAIT_CONFIRM)
                        .build()))
                .build();
    }

    private ObjectNode callbackPayload(String action, String taskId, String stepId) {
        return callbackPayload(action, taskId, stepId, "");
    }

    private ObjectNode callbackPayload(String action, String taskId, String stepId, String confirmStage) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode event = root.putObject("event");
        event.putObject("context").put("open_chat_id", "oc_1");
        ObjectNode value = event.putObject("action").putObject("value");
        value.put("action", action);
        value.put("taskId", taskId);
        if (stepId != null && !stepId.isBlank()) {
            value.put("stepId", stepId);
        }
        if (confirmStage != null && !confirmStage.isBlank()) {
            value.put("confirmStage", confirmStage);
        }
        return root;
    }

    private ObjectNode callbackPayloadWithoutChat(String action, String taskId, String stepId, String confirmStage) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode event = root.putObject("event");
        ObjectNode value = event.putObject("action").putObject("value");
        value.put("action", action);
        value.put("taskId", taskId);
        if (stepId != null && !stepId.isBlank()) {
            value.put("stepId", stepId);
        }
        if (confirmStage != null && !confirmStage.isBlank()) {
            value.put("confirmStage", confirmStage);
        }
        return root;
    }
}
