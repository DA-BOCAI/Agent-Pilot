package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.TaskStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest
class AgentTaskServiceTest {

    @Autowired
    private AgentTaskService agentTaskService;

    @Test
    void shouldRunTaskLifecycleWithTwoConfirmations() {
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

