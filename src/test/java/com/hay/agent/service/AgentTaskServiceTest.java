package com.hay.agent.service;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.RefineStepPreviewRequest;
import com.hay.agent.api.dto.UpdateStepPreviewRequest;
import com.hay.agent.api.dto.preview.PresentationPreviewResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.planner.Planner;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.tool.ToolExecutor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

@SpringBootTest(properties = "agent.task.auto-run=false")
class AgentTaskServiceTest {

    @Autowired
    private AgentTaskService agentTaskService;

    @Autowired
    private AgentRunner agentRunner;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private Planner planner;

    @MockBean
    private ToolExecutor toolExecutor;

    @MockBean
    private ContentPreviewService contentPreviewService;

    @MockBean
    private PreviewRefinementService previewRefinementService;

    @Test
    void shouldRunTaskLifecycleWithoutConfirmingNotificationAndDeliverySteps() {
        when(planner.plan(anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("A_CAPTURE").scene("A").action("接收并标准化用户意图").tool("none").requiresConfirm(false).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("C_DOC").scene("C").action("生成需求文档初稿").tool("lark-doc").requiresConfirm(true).status(StepStatus.PENDING).build(),
                PlanStep.builder().stepId("SEND_IM").scene("F").action("发送飞书消息告知用户文档已生成").tool("lark-im").requiresConfirm(true).status(StepStatus.PENDING).build(),
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
        assertEquals(false, task.getPlanSteps().get(2).isRequiresConfirm());
        assertEquals(false, task.getPlanSteps().get(3).isRequiresConfirm());

        task = agentTaskService.executeTask(task.getTaskId());
        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());

        ConfirmTaskRequest confirmDoc = new ConfirmTaskRequest();
        confirmDoc.setStepId("C_DOC");
        confirmDoc.setApproved(true);
        task = agentTaskService.confirmStep(task.getTaskId(), confirmDoc);
        assertEquals(TaskStatus.PLANNED, task.getStatus());

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
        assertEquals("campaign", task.getPlanSteps().get(0).getPreviewData().path("theme").asText());
        assertEquals(1, task.getArtifacts().size());
        assertEquals("slides-preview", task.getArtifacts().get(0).getType());
        assertEquals("campaign", task.getArtifacts().get(0).getPreviewData().path("theme").asText());
    }

    @Test
    void shouldUpdateSlidesPreviewFromCard2Edits() {
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
        create.setUserId("u-test-03");
        create.setRequestId("req-test-03");

        AgentTask task = agentTaskService.createTask(create);
        task = agentTaskService.planTask(task.getTaskId());
        task = agentTaskService.generateStepPreview(task.getTaskId(), "D_SLIDES", new GenerateStepPreviewRequest());

        ObjectNode previewData = objectMapper.createObjectNode();
        previewData.put("artifactType", "PRESENTATION");
        previewData.put("title", "用户改过的PPT");
        previewData.put("theme", "business");
        previewData.putArray("slides");

        UpdateStepPreviewRequest updateRequest = new UpdateStepPreviewRequest();
        updateRequest.setPreviewData(previewData);
        task = agentTaskService.updateStepPreview(task.getTaskId(), "D_SLIDES", updateRequest);

        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());
        assertEquals("business", task.getPlanSteps().get(0).getPreviewData().path("theme").asText());
        assertEquals("用户改过的PPT", task.getArtifacts().get(0).getTitle());
        assertEquals("business", task.getArtifacts().get(0).getPreviewData().path("theme").asText());
    }

    @Test
    void shouldRejectPreviewUpdateWhenArtifactTypeMismatchesStep() {
        when(planner.plan(anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("D_SLIDES").scene("D").action("生成演示文稿").tool("lark-slides").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));

        CreateTaskRequest create = new CreateTaskRequest();
        create.setInputText("生成双十一大促PPT");
        create.setUserId("u-test-04");
        create.setRequestId("req-test-04");

        AgentTask task = agentTaskService.createTask(create);
        task = agentTaskService.planTask(task.getTaskId());

        ObjectNode previewData = objectMapper.createObjectNode();
        previewData.put("artifactType", "DOCUMENT");
        UpdateStepPreviewRequest updateRequest = new UpdateStepPreviewRequest();
        updateRequest.setPreviewData(previewData);

        String taskId = task.getTaskId();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
                () -> agentTaskService.updateStepPreview(taskId, "D_SLIDES", updateRequest));
    }

    @Test
    void shouldRefineSlidesPreviewByNaturalLanguageInstruction() {
        when(previewRefinementService.refine(any(), anyString(), anyString())).thenReturn(Optional.empty());
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
        create.setUserId("u-test-05");
        create.setRequestId("req-test-05");

        AgentTask task = agentTaskService.createTask(create);
        task = agentTaskService.planTask(task.getTaskId());
        task = agentTaskService.generateStepPreview(task.getTaskId(), "D_SLIDES", new GenerateStepPreviewRequest());

        RefineStepPreviewRequest refineRequest = new RefineStepPreviewRequest();
        refineRequest.setInstruction("改成商务稳重风格，标题为双十一增长作战方案");
        task = agentTaskService.refineStepPreview(task.getTaskId(), "D_SLIDES", refineRequest);

        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());
        assertEquals("business", task.getPlanSteps().get(0).getPreviewData().path("theme").asText());
        assertEquals("双十一增长作战方案", task.getPlanSteps().get(0).getPreviewData().path("title").asText());
        assertEquals("双十一增长作战方案", task.getArtifacts().get(0).getTitle());
        assertEquals("改成商务稳重风格，标题为双十一增长作战方案",
                task.getArtifacts().get(0).getPreviewData().path("lastRefineInstruction").asText());
    }

    @Test
    void shouldUseLlmRefinedPreviewDataWhenAvailable() {
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

        ObjectNode llmPreviewData = objectMapper.createObjectNode();
        llmPreviewData.put("artifactType", "PRESENTATION");
        llmPreviewData.put("title", "LLM精修后的方案");
        llmPreviewData.put("theme", "tech");
        llmPreviewData.putArray("slides");
        when(previewRefinementService.refine(any(), anyString(), anyString())).thenReturn(Optional.of(llmPreviewData));

        CreateTaskRequest create = new CreateTaskRequest();
        create.setInputText("生成双十一大促PPT");
        create.setUserId("u-test-06");
        create.setRequestId("req-test-06");

        AgentTask task = agentTaskService.createTask(create);
        task = agentTaskService.planTask(task.getTaskId());
        task = agentTaskService.generateStepPreview(task.getTaskId(), "D_SLIDES", new GenerateStepPreviewRequest());

        RefineStepPreviewRequest refineRequest = new RefineStepPreviewRequest();
        refineRequest.setInstruction("改成科技风，并补一页数据看板");
        task = agentTaskService.refineStepPreview(task.getTaskId(), "D_SLIDES", refineRequest);

        assertEquals("tech", task.getPlanSteps().get(0).getPreviewData().path("theme").asText());
        assertEquals("LLM精修后的方案", task.getArtifacts().get(0).getTitle());
    }

    @Test
    void runnerShouldPlanExecuteAndStopAtConfirmThenGeneratePreviewAfterApproval() {
        when(planner.plan(anyString())).thenReturn(List.of(
                PlanStep.builder().stepId("D_SLIDES").scene("D").action("生成演示文稿").tool("lark-slides").requiresConfirm(true).status(StepStatus.PENDING).build()
        ));
        when(contentPreviewService.previewPresentation(any())).thenReturn(PresentationPreviewResponse.builder()
                .artifactType("PRESENTATION")
                .title("项目方案")
                .theme("business")
                .pageCount(1)
                .slides(List.of())
                .warnings(List.of())
                .build());

        CreateTaskRequest create = new CreateTaskRequest();
        create.setInputText("生成项目方案PPT");
        create.setUserId("u-test-07");
        create.setRequestId("req-test-07");

        AgentTask task = agentTaskService.createTask(create);
        task = agentRunner.runUntilBlocked(task.getTaskId());

        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());
        assertEquals(StepStatus.WAIT_CONFIRM, task.getPlanSteps().get(0).getStatus());
        assertEquals("confirm:D_SLIDES", task.getNextAction());

        ConfirmTaskRequest confirm = new ConfirmTaskRequest();
        confirm.setStepId("D_SLIDES");
        confirm.setApproved(true);
        agentTaskService.confirmStep(task.getTaskId(), confirm);

        task = agentRunner.runUntilBlocked(task.getTaskId());
        assertEquals(TaskStatus.WAIT_CONFIRM, task.getStatus());
        assertEquals(StepStatus.WAIT_CONFIRM, task.getPlanSteps().get(0).getStatus());
        assertEquals("business", task.getPlanSteps().get(0).getPreviewData().path("theme").asText());
        assertEquals("slides-preview", task.getArtifacts().get(0).getType());
    }
}

