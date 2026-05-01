package com.hay.agent.mapper;

import com.fasterxml.jackson.databind.JsonNode;
import com.hay.agent.api.dto.TaskCardView;
import com.hay.agent.api.dto.TaskView;
import com.hay.agent.api.dto.TaskWorkspaceView;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.Artifact;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class TaskMapper {

    public TaskView toView(AgentTask task) {
        return TaskView.builder()
                .taskId(task.getTaskId())
                .requestId(task.getRequestId())
                .source(task.getSource())
                .userId(task.getUserId())
                .inputText(task.getInputText())
                .status(task.getStatus())
                .nextAction(task.getNextAction())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .planSteps(task.getPlanSteps())
                .artifacts(task.getArtifacts())
                .events(task.getEvents())
                .build();
    }

    public TaskCardView toCardView(AgentTask task) {
        return TaskCardView.builder()
                .taskId(task.getTaskId())
                .status(task.getStatus().name())
                .nextAction(task.getNextAction())
                .steps(task.getPlanSteps().stream().map(this::toStepProgress).toList())
                .confirm(toConfirmCard(task))
                .completion(toCompletionCard(task))
                .build();
    }

    public TaskWorkspaceView toWorkspaceView(AgentTask task) {
        return TaskWorkspaceView.builder()
                .task(toView(task))
                .cards(toCardView(task))
                .previews(task.getArtifacts().stream().filter(this::isPreviewArtifact).toList())
                .outputs(task.getArtifacts().stream().filter(artifact -> !isPreviewArtifact(artifact)).toList())
                .timeline(task.getEvents())
                .build();
    }

    private TaskCardView.StepProgress toStepProgress(PlanStep step) {
        return TaskCardView.StepProgress.builder()
                .stepId(step.getStepId())
                .action(step.getAction())
                .status(step.getStatus() == null ? StepStatus.PENDING.name() : step.getStatus().name())
                .displayStatus(displayStatus(step.getStatus()))
                .requiresConfirm(step.isRequiresConfirm())
                .build();
    }

    private TaskCardView.ConfirmCard toConfirmCard(AgentTask task) {
        Optional<PlanStep> waitingStep = task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .findFirst();
        if (waitingStep.isEmpty()) {
            return TaskCardView.ConfirmCard.builder().waiting(false).build();
        }

        PlanStep step = waitingStep.get();
        JsonNode previewData = step.getPreviewData();
        return TaskCardView.ConfirmCard.builder()
                .waiting(true)
                .stepId(step.getStepId())
                .phase(previewData == null || previewData.isMissingNode() ? "confirm1" : "confirm2")
                .action(step.getAction())
                .artifactType(readArtifactType(step))
                .recommendedTheme(previewData == null ? null : previewData.path("theme").asText(null))
                .previewData(previewData)
                .build();
    }

    private TaskCardView.CompletionCard toCompletionCard(AgentTask task) {
        boolean finished = task.getStatus() == TaskStatus.DELIVERED;
        boolean failed = task.getStatus() == TaskStatus.FAILED;
        List<TaskCardView.LinkItem> links = task.getArtifacts().stream()
                .filter(artifact -> artifact.getUrl() != null && !artifact.getUrl().startsWith("preview://"))
                .map(this::toLinkItem)
                .toList();

        return TaskCardView.CompletionCard.builder()
                .finished(finished)
                .failed(failed)
                .links(links)
                .message(failed ? "任务执行失败，请查看事件列表" : finished ? "任务已完成" : "任务进行中")
                .build();
    }

    private TaskCardView.LinkItem toLinkItem(Artifact artifact) {
        return TaskCardView.LinkItem.builder()
                .type(artifact.getType())
                .title(artifact.getTitle())
                .url(artifact.getUrl())
                .build();
    }

    private String displayStatus(StepStatus status) {
        if (status == null || status == StepStatus.PENDING) {
            return "待完成";
        }
        if (status == StepStatus.RUNNING) {
            return "完成中";
        }
        if (status == StepStatus.DONE) {
            return "已完成";
        }
        if (status == StepStatus.WAIT_CONFIRM) {
            return "等待确认";
        }
        if (status == StepStatus.FAILED) {
            return "失败";
        }
        if (status == StepStatus.APPROVED) {
            return "已确认";
        }
        if (status == StepStatus.SKIPPED) {
            return "已跳过";
        }
        return status.name();
    }

    private String readArtifactType(PlanStep step) {
        if (step.getPreviewData() != null && step.getPreviewData().hasNonNull("artifactType")) {
            return displayArtifactType(step.getPreviewData().path("artifactType").asText());
        }
        if ("D_SLIDES".equals(step.getStepId())) {
            return "PPT";
        }
        if ("C_DOC".equals(step.getStepId())) {
            return "文档";
        }
        return null;
    }

    private String displayArtifactType(String artifactType) {
        if ("PRESENTATION".equalsIgnoreCase(artifactType) || "slides-preview".equalsIgnoreCase(artifactType)) {
            return "PPT";
        }
        if ("DOCUMENT".equalsIgnoreCase(artifactType) || "docs-preview".equalsIgnoreCase(artifactType)) {
            return "文档";
        }
        return artifactType;
    }

    private boolean isPreviewArtifact(Artifact artifact) {
        return artifact.getType() != null && artifact.getType().endsWith("-preview");
    }
}
