package com.hay.agent.service.workflow;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.im.LarkTaskCardService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskActionCoordinatorTest {

    @Mock
    private AgentTaskService agentTaskService;

    @Mock
    private AgentRunner agentRunner;

    @Mock
    private LarkTaskCardService larkTaskCardService;

    private TaskActionCoordinator coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new TaskActionCoordinator(agentTaskService, agentRunner, larkTaskCardService);
    }

    @Test
    void shouldSendLatestImFailureCardWhenWorkspaceConfirmRunnerFails() {
        AgentTask waitingTask = task(TaskStatus.WAIT_CONFIRM, StepStatus.WAIT_CONFIRM);
        AgentTask confirmedTask = task(TaskStatus.PLANNED, StepStatus.APPROVED);
        AgentTask failedTask = task(TaskStatus.FAILED, StepStatus.FAILED);
        failedTask.setEvents(List.of(TaskEvent.builder()
                .type("STEP_PREVIEW_FAILED")
                .metadata(Map.of(
                        "failureKind", "LLM_RATE_LIMIT",
                        "userMessage", "大模型请求受限，可能触发 TPM/限流。请稍等片刻后重试，或减少一次生成的内容规模。"))
                .build()));

        when(agentTaskService.getTask("task-1")).thenReturn(waitingTask, failedTask);
        when(agentTaskService.confirmStep(eq("task-1"), any())).thenReturn(confirmedTask);
        when(agentRunner.runUntilBlocked("task-1"))
                .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR, "429"));

        ConfirmTaskRequest request = new ConfirmTaskRequest();
        request.setStepId("D_SLIDES");
        request.setApproved(true);

        assertThrows(ResponseStatusException.class, () -> coordinator.confirmFromWorkspace("task-1", request));

        verify(larkTaskCardService).updateConfirmCardResolved(eq("oc_1"), eq(waitingTask), any(), eq(true));
        verify(larkTaskCardService, timeout(1000)).sendFollowUpCardsForCurrentState("oc_1", failedTask);
    }

    private AgentTask task(TaskStatus taskStatus, StepStatus stepStatus) {
        return AgentTask.builder()
                .taskId("task-1")
                .source("IM:group:oc_1")
                .status(taskStatus)
                .planSteps(List.of(PlanStep.builder()
                        .stepId("D_SLIDES")
                        .action("创建项目复盘 PPT")
                        .requiresConfirm(true)
                        .status(stepStatus)
                        .build()))
                .build();
    }
}
