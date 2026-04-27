package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.planner.Planner;
import com.hay.agent.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

@SpringBootTest(properties = "agent.task.auto-run=false")
class AgentTaskServiceTest {

    @Autowired
    private AgentTaskService agentTaskService;

    @MockBean
    private Planner planner;

    @MockBean
    private ToolExecutor toolExecutor;

    @Test
    void shouldRunTaskLifecycleWithTwoConfirmations() {
        when(planner.plan(anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("A_CAPTURE").scene("A").action("接收并标准化用户意图").tool("none").requiresConfirm(false).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("C_DOC").scene("C").action("生成需求文档初稿").tool("lark-doc").requiresConfirm(true).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("D_SLIDES").scene("D").action("生成演示文稿").tool("lark-slides").requiresConfirm(false).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("F_DELIVER").scene("F").action("发布并输出交付结果").tool("lark-task").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));
        when(toolExecutor.execute(any(), anyString(), anyString())).thenReturn(Optional.empty());

        CreateTaskRequest create = new CreateTaskRequest();
        create.setInputText("Generate release meeting materials");
        create.setUserId("u-test-01");
        create.setRequestId("req-test-01");

        AgentTask task = agentTaskService.createTask(create);
        assertEquals(TaskStatus.CREATED, task.getStatus());

        task = agentTaskService.planTask(task.getTaskId());
        assertEquals(TaskStatus.PLANNED, task.getStatus());

        task = agentTaskService.executeTask(task.getTaskId());
        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());

        ConfirmTaskRequest confirmDoc = new ConfirmTaskRequest();
        confirmDoc.setStepId("C_DOC");
        confirmDoc.setApproved(true);
        task = agentTaskService.confirmStep(task.getTaskId(), confirmDoc);
        assertEquals(TaskStatus.PLANNED, task.getStatus());

        task = agentTaskService.executeTask(task.getTaskId());
        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());

        ConfirmTaskRequest confirmDeliver = new ConfirmTaskRequest();
        confirmDeliver.setStepId("F_DELIVER");
        confirmDeliver.setApproved(true);
        task = agentTaskService.confirmStep(task.getTaskId(), confirmDeliver);

        task = agentTaskService.executeTask(task.getTaskId());
        assertEquals(TaskStatus.DELIVERED, task.getStatus());
        assertNotNull(task.getArtifacts());
    }
}

