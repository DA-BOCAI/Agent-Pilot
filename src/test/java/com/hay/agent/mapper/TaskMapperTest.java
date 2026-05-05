package com.hay.agent.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.api.dto.TaskWorkspaceView;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.domain.TaskStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskMapperTest {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final TaskMapper taskMapper = new TaskMapper();

    @Test
    void shouldExposeTimelineAndDeterministicAdjustmentsForWorkspace() {
        var previewData = objectMapper.createObjectNode();
        previewData.put("artifactType", "PRESENTATION");
        previewData.put("title", "项目汇报");
        previewData.put("theme", "business");
        previewData.put("pageCount", 12);
        previewData.putArray("warnings").add("当前预览包含 12 页，正式创建会逐页写入飞书演示文稿，耗时会随页数增加。");
        previewData.set("slides", objectMapper.createArrayNode());
        AgentTask task = AgentTask.builder()
                .taskId("task-1")
                .inputText("生成项目汇报 PPT")
                .status(TaskStatus.WAIT_CONFIRM)
                .nextAction("confirm:D_SLIDES")
                .updatedAt(Instant.now())
                .planSteps(List.of(PlanStep.builder()
                        .stepId("D_SLIDES")
                        .action("创建项目汇报 PPT")
                        .status(StepStatus.WAIT_CONFIRM)
                        .requiresConfirm(true)
                        .previewData(previewData)
                        .build()))
                .artifacts(List.of(Artifact.builder()
                        .type("slides-preview")
                        .stepId("D_SLIDES")
                        .title("项目汇报")
                        .url("preview://task-1/D_SLIDES")
                        .previewData(previewData)
                        .build()))
                .events(List.of(TaskEvent.builder()
                        .timestamp(Instant.now())
                        .type("STEP_PREVIEW_READY")
                        .message("步骤预览已生成")
                        .metadata(Map.of("stepId", "D_SLIDES"))
                        .build()))
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertTrue(workspace.getPreview().isAvailable());
        assertEquals("slides", workspace.getPreview().getType());
        assertEquals(60, workspace.getProgress().getPercent());
        assertEquals("confirm2", workspace.getProgress().getPhase());
        assertEquals("confirm2", workspace.getSteps().get(0).getPhase());
        assertEquals("预览待确认", workspace.getSteps().get(0).getDisplayStatus());
        assertTrue(workspace.getAdjustments().isAvailable());
        assertEquals("confirm2", workspace.getAdjustments().getPhase());
        assertTrue(workspace.getAdjustments().getActions().stream().anyMatch(action -> "theme".equals(action.getKey())));
        assertTrue(workspace.getAdjustments().getActions().stream().anyMatch(action -> "naturalLanguageRefine".equals(action.getKey())));
        assertTrue(workspace.getAdjustments().getActions().stream()
                .allMatch(action -> action.getEndpoint().contains("/workspace/steps/D_SLIDES/preview")));
        assertEquals("预览已生成", workspace.getTimeline().get(0).getTitle());
        assertEquals("warning", workspace.getTimeline().get(0).getLevel());
        assertEquals("D_SLIDES", workspace.getTimeline().get(0).getStepId());
        assertEquals("agent", workspace.getTimeline().get(0).getSource());
        assertEquals("Agent", workspace.getTimeline().get(0).getSourceDisplay());
        assertTrue(workspace.getSync().isRealtimeEnabled());
        assertEquals("/api/v1/tasks/task-1/workspace", workspace.getSync().getSnapshotEndpoint());
        assertEquals("/api/v1/tasks/task-1/workspace/stream", workspace.getSync().getStreamEndpoint());
        assertEquals("backend_authoritative", workspace.getDiagnostics().getProgressSource());
        assertEquals(60, workspace.getDiagnostics().getProgressPercent());
        assertEquals("confirm2", workspace.getDiagnostics().getProgressPhase());
        assertEquals(1, workspace.getDiagnostics().getHiddenInternalArtifactCount());
        assertEquals(1, workspace.getDiagnostics().getPreviewWarningCount());
        assertEquals(12, workspace.getDiagnostics().getSlidePageCount());
        assertTrue(workspace.getDiagnostics().isShouldUseBackendProgress());
    }

    @Test
    void shouldUseExplicitImRequirementAsWorkspaceTitle() {
        AgentTask task = AgentTask.builder()
                .taskId("task-2")
                .inputText("""
                        【IM任务输入】
                        【触发方式】群聊@机器人

                        【用户明确需求】
                        帮我们生成一份校招宣讲PPT

                        【最近讨论上下文】
                        1. ou_1：重点讲岗位亮点和培养机制
                        """)
                .status(TaskStatus.CREATED)
                .nextAction("plan")
                .updatedAt(Instant.now())
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertEquals("帮我们生成一份校招宣讲PPT", workspace.getTitle());
        assertEquals("帮我们生成一份校招宣讲PPT", workspace.getInputSummary());
    }

    @Test
    void shouldExposeWorkspaceActionSourceForTimeline() {
        AgentTask task = AgentTask.builder()
                .taskId("task-3")
                .source("IM:p2p:oc_1")
                .inputText("生成项目文档")
                .status(TaskStatus.PLANNED)
                .updatedAt(Instant.now())
                .events(List.of(TaskEvent.builder()
                        .timestamp(Instant.now())
                        .type("STEP_APPROVED")
                        .message("Step approved by user")
                        .metadata(Map.of("stepId", "C_DOC", "source", "workspace", "clientId", "mobile-web"))
                        .build()))
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertEquals("飞书单聊", workspace.getSourceDisplay());
        assertEquals("workspace", workspace.getTimeline().get(0).getSource());
        assertEquals("工作台", workspace.getTimeline().get(0).getSourceDisplay());
        assertEquals("mobile-web", workspace.getTimeline().get(0).getMetadata().get("clientId"));
    }

    @Test
    void shouldExposeModelFailureMessageInTaskCardView() {
        AgentTask task = AgentTask.builder()
                .taskId("task-model-failed")
                .inputText("生成项目PPT")
                .status(TaskStatus.FAILED)
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

        var card = taskMapper.toCardView(task);

        assertTrue(card.getCompletion().getMessage().contains("大模型请求受限"));
        assertTrue(card.getCompletion().getMessage().contains("TPM"));
    }

    @Test
    void shouldKeepWorkspaceProgressMonotonicAcrossTwoConfirmStages() {
        PlanStep step = PlanStep.builder()
                .stepId("D_SLIDES")
                .action("创建宣讲 PPT")
                .status(StepStatus.WAIT_CONFIRM)
                .requiresConfirm(true)
                .build();
        AgentTask task = AgentTask.builder()
                .taskId("task-4")
                .inputText("生成宣讲 PPT")
                .status(TaskStatus.WAIT_CONFIRM)
                .nextAction("confirm:D_SLIDES")
                .updatedAt(Instant.now())
                .planSteps(List.of(step))
                .build();

        TaskWorkspaceView confirm1 = taskMapper.toWorkspaceView(task);

        step.setStatus(StepStatus.APPROVED);
        task.setStatus(TaskStatus.PLANNED);
        task.setNextAction("execute");
        TaskWorkspaceView previewGenerating = taskMapper.toWorkspaceView(task);

        step.setStatus(StepStatus.WAIT_CONFIRM);
        step.setPreviewData(objectMapper.createObjectNode()
                .put("artifactType", "PRESENTATION")
                .put("title", "宣讲 PPT")
                .put("theme", "business"));
        task.setStatus(TaskStatus.WAIT_CONFIRM);
        task.setNextAction("confirm:D_SLIDES");
        TaskWorkspaceView confirm2 = taskMapper.toWorkspaceView(task);

        assertEquals(40, confirm1.getProgress().getPercent());
        assertEquals("confirm1", confirm1.getProgress().getPhase());
        assertEquals(50, previewGenerating.getProgress().getPercent());
        assertEquals("preview_generating", previewGenerating.getProgress().getPhase());
        assertEquals("需求已确认，生成预览中", previewGenerating.getSteps().get(0).getDisplayStatus());
        assertEquals(60, confirm2.getProgress().getPercent());
        assertEquals("confirm2", confirm2.getProgress().getPhase());
        assertEquals("预览待确认", confirm2.getSteps().get(0).getDisplayStatus());
    }

    @Test
    void shouldNotRegressProgressWhenSecondArtifactEntersConfirm1() {
        PlanStep docStep = PlanStep.builder()
                .stepId("C_DOC")
                .action("创建方案文档")
                .status(StepStatus.DONE)
                .requiresConfirm(true)
                .previewData(objectMapper.createObjectNode()
                        .put("artifactType", "DOCUMENT")
                        .put("title", "方案文档"))
                .build();
        PlanStep slidesStep = PlanStep.builder()
                .stepId("D_SLIDES")
                .action("基于文档创建 PPT")
                .status(StepStatus.WAIT_CONFIRM)
                .requiresConfirm(true)
                .build();
        AgentTask task = AgentTask.builder()
                .taskId("task-progress-2")
                .inputText("生成方案文档和PPT")
                .status(TaskStatus.WAIT_CONFIRM)
                .nextAction("confirm:D_SLIDES")
                .updatedAt(Instant.now())
                .planSteps(List.of(docStep, slidesStep))
                .artifacts(List.of(Artifact.builder()
                        .type("docs")
                        .stepId("C_DOC")
                        .title("方案文档")
                        .url("https://example.feishu.cn/docx/abc")
                        .build()))
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertEquals("confirm1", workspace.getProgress().getPhase());
        assertEquals(70, workspace.getProgress().getPercent());
        assertEquals("后续产物需求待确认", workspace.getProgress().getLabel());
    }

    @Test
    void shouldNotRegressProgressWhenSecondArtifactEntersConfirm2() {
        PlanStep docStep = PlanStep.builder()
                .stepId("C_DOC")
                .action("创建方案文档")
                .status(StepStatus.DONE)
                .requiresConfirm(true)
                .build();
        PlanStep slidesStep = PlanStep.builder()
                .stepId("D_SLIDES")
                .action("基于文档创建 PPT")
                .status(StepStatus.WAIT_CONFIRM)
                .requiresConfirm(true)
                .previewData(objectMapper.createObjectNode()
                        .put("artifactType", "PRESENTATION")
                        .put("title", "方案PPT")
                        .put("theme", "business"))
                .build();
        AgentTask task = AgentTask.builder()
                .taskId("task-progress-3")
                .inputText("生成方案文档和PPT")
                .status(TaskStatus.WAIT_CONFIRM)
                .nextAction("confirm:D_SLIDES")
                .updatedAt(Instant.now())
                .planSteps(List.of(docStep, slidesStep))
                .artifacts(List.of(Artifact.builder()
                        .type("docs")
                        .stepId("C_DOC")
                        .title("方案文档")
                        .url("https://example.feishu.cn/docx/abc")
                        .build()))
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertEquals("confirm2", workspace.getProgress().getPhase());
        assertEquals(85, workspace.getProgress().getPercent());
        assertEquals("后续预览待确认", workspace.getProgress().getLabel());
    }

    @Test
    void shouldNormalizeWorkspaceOutputTypesAndKeepStepIds() {
        AgentTask task = AgentTask.builder()
                .taskId("task-5")
                .inputText("生成交付材料")
                .status(TaskStatus.DELIVERED)
                .updatedAt(Instant.now())
                .artifacts(List.of(
                        Artifact.builder()
                                .type("docs")
                                .stepId("C_DOC")
                                .title("方案文档")
                                .url("https://example.feishu.cn/docx/abc")
                                .build(),
                        Artifact.builder()
                                .type("delivery")
                                .stepId("F_DELIVER")
                                .title("交付包-task-5")
                                .url("https://workspace.example.com?taskId=task-5")
                                .build(),
                        Artifact.builder()
                                .type("docs-preview")
                                .stepId("C_DOC")
                                .title("文档预览")
                                .url("preview://task-5/C_DOC")
                                .build()))
                .build();

        TaskWorkspaceView workspace = taskMapper.toWorkspaceView(task);

        assertEquals(1, workspace.getOutputs().size());
        assertEquals("doc", workspace.getOutputs().get(0).getType());
        assertEquals("C_DOC", workspace.getOutputs().get(0).getStepId());
    }

    @Test
    void shouldHideInternalPreviewAndDeliveryArtifactsFromTaskView() {
        AgentTask task = AgentTask.builder()
                .taskId("task-6")
                .inputText("生成交付材料")
                .status(TaskStatus.DELIVERED)
                .updatedAt(Instant.now())
                .artifacts(List.of(
                        Artifact.builder()
                                .type("docs-preview")
                                .stepId("C_DOC")
                                .title("文档预览")
                                .url("preview://task-6/C_DOC")
                                .build(),
                        Artifact.builder()
                                .type("docs")
                                .stepId("C_DOC")
                                .title("正式文档")
                                .url("https://example.feishu.cn/docx/abc")
                                .build(),
                        Artifact.builder()
                                .type("delivery")
                                .stepId("F_DELIVER")
                                .title("交付包-task-6")
                                .url("https://workspace.example.com?taskId=task-6")
                                .build()))
                .build();

        var view = taskMapper.toView(task);

        assertEquals(1, view.getArtifacts().size());
        assertEquals("docs", view.getArtifacts().get(0).getType());
        assertEquals("正式文档", view.getArtifacts().get(0).getTitle());
    }
}
