package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
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

    @MockBean
    private ContentPreviewService contentPreviewService;

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

    @Test
    void shouldGenerateSlidesPreviewForConfirm2() {
        when(planner.plan(anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("D_SLIDES").scene("D").action("生成演示文稿").tool("lark-slides").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));
        when(contentPreviewService.previewPresentation(any())).thenReturn(PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title("双十一方案")
                .theme("campaign")
                .pageCount(1)
                .slides(List.of())
                .warnings(List.of())
                .build());

        CreateTaskRequest create = new CreateTaskRequest();
        create.setInputText("生成双十一大促PPT");
        create.setUserId("u-test-02");
        create.setRequestId("req-test-02");

        AgentTask task = agentTaskService.createTask(create);
        task = agentTaskService.planTask(task.getTaskId());
        task = agentTaskService.executeTask(task.getTaskId());
        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());

        ConfirmTaskRequest confirmIntent = new ConfirmTaskRequest();
        confirmIntent.setStepId("D_SLIDES");
        confirmIntent.setApproved(true);
        task = agentTaskService.confirmStep(task.getTaskId(), confirmIntent);

        GenerateStepPreviewRequest previewRequest = new GenerateStepPreviewRequest();
        previewRequest.setTheme("campaign");
        task = agentTaskService.generateStepPreview(task.getTaskId(), "D_SLIDES", previewRequest);

        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());
        assertEquals(StepStatus.WAIT_CONFIRM, task.getPlanSteps().get(0).getStatus());
        assertEquals(1, task.getArtifacts().size());
        assertEquals("slides-preview", task.getArtifacts().get(0).getType());
        assertEquals("campaign", task.getArtifacts().get(0).getPreviewData().path("theme").asText());
    }
}

