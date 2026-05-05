package com.hay.agent.runner;

import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.preview.StepPreviewGenerator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
public class AgentRunner {
    private final AgentTaskService agentTaskService;
    private final List<StepPreviewGenerator> stepPreviewGenerators;

    public AgentRunner(AgentTaskService agentTaskService,
                       List<StepPreviewGenerator> stepPreviewGenerators) {
        this.agentTaskService = agentTaskService;
        this.stepPreviewGenerators = stepPreviewGenerators;
    }

    public void runAsync(String taskId) {
        CompletableFuture.runAsync(() -> runUntilBlocked(taskId));
    }

    public AgentTask runUntilBlocked(String taskId) {
        AgentTask task = agentTaskService.getTask(taskId);
        try {
            while (true) {
                if (task.getStatus() == TaskStatus.CREATED) {
                    task = agentTaskService.planTask(taskId);
                    continue;
                }

                PlanStep approvedPreviewStep = findApprovedPreviewStepWithoutPreview(task);
                if (approvedPreviewStep != null) {
                    task = agentTaskService.generateStepPreview(taskId, approvedPreviewStep.getStepId(), new GenerateStepPreviewRequest());
                    continue;
                }

                if (task.getStatus() == TaskStatus.PLANNED || task.getStatus() == TaskStatus.RUNNING) {
                    AgentTask advancedTask = agentTaskService.executeTask(taskId);
                    if (advancedTask.getStatus() == task.getStatus()
                            && String.valueOf(advancedTask.getUpdatedAt()).equals(String.valueOf(task.getUpdatedAt()))) {
                        return advancedTask;
                    }
                    task = advancedTask;
                    if (task.getStatus() == TaskStatus.WAIT_CONFIRM
                            || task.getStatus() == TaskStatus.DELIVERED
                            || task.getStatus() == TaskStatus.FAILED) {
                        return task;
                    }
                    continue;
                }

                return task;
            }
        } catch (Exception e) {
            log.error("AgentRunner 推进任务失败，taskId={}", taskId, e);
            throw e;
        }
    }

    private PlanStep findApprovedPreviewStepWithoutPreview(AgentTask task) {
        return task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.APPROVED)
                .filter(step -> step.getPreviewData() == null || step.getPreviewData().isMissingNode() || step.getPreviewData().isNull())
                .filter(this::supportsPreview)
                .findFirst()
                .orElse(null);
    }

    private boolean supportsPreview(PlanStep step) {
        if ("C_DOC".equals(step.getStepId()) || "D_SLIDES".equals(step.getStepId())) {
            return true;
        }
        return stepPreviewGenerators.stream().anyMatch(generator -> generator.supports(step));
    }
}
