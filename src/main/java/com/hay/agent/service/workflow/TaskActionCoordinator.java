package com.hay.agent.service.workflow;

import com.hay.agent.api.dto.CancelTaskRequest;
import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.im.LarkTaskCardService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskActionCoordinator {

    private final AgentTaskService agentTaskService;
    private final AgentRunner agentRunner;
    private final LarkTaskCardService larkTaskCardService;
    private final ConcurrentMap<String, Object> taskLocks = new ConcurrentHashMap<>();

    public AgentTask confirmFromWorkspace(String taskId, ConfirmTaskRequest request) {
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            try {
                AgentTask waitingTask = agentTaskService.getTask(taskId);
                String stepId = resolveStepId(waitingTask, request == null ? "" : request.getStepId());
                Optional<PlanStep> waitingStep = findStep(waitingTask, stepId);
                String chatId = resolveChatId(waitingTask);
                log.info("收到工作台确认请求，taskId={}，stepId={}，source={}，clientId={}，status={}，chatIdResolved={}",
                        taskId,
                        stepId,
                        request == null ? "" : request.getSource(),
                        request == null ? "" : request.getClientId(),
                        waitingTask.getStatus(),
                        chatId != null && !chatId.isBlank());

                if (waitingStep.isEmpty() || waitingStep.get().getStatus() != StepStatus.WAIT_CONFIRM) {
                    log.info("工作台确认请求已过期或当前没有等待确认步骤，taskId={}，stepId={}", taskId, stepId);
                    larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, waitingTask);
                    return waitingTask;
                }

                larkTaskCardService.updateConfirmCardResolved(chatId, waitingTask, waitingStep.get(), true);

                ConfirmTaskRequest confirmRequest = new ConfirmTaskRequest();
                confirmRequest.setStepId(stepId);
                confirmRequest.setApproved(true);
                confirmRequest.setComment(request == null || request.getComment() == null || request.getComment().isBlank()
                        ? "来自工作台确认"
                        : request.getComment());
                confirmRequest.setSource(request == null || request.getSource() == null || request.getSource().isBlank()
                        ? "workspace"
                        : request.getSource());
                confirmRequest.setClientId(request == null ? null : request.getClientId());
                agentTaskService.confirmStep(taskId, confirmRequest);

                AgentTask advancedTask = agentRunner.runUntilBlocked(taskId);
                larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, advancedTask);
                log.info("工作台确认已完成推进，taskId={}，stepId={}，status={}，nextAction={}",
                        taskId, stepId, advancedTask.getStatus(), advancedTask.getNextAction());
                return advancedTask;
            } catch (Exception e) {
                sendLatestCards(taskId);
                throw e;
            } finally {
                taskLocks.remove(taskId, lock);
            }
        }
    }

    public AgentTask cancelFromWorkspace(String taskId, CancelTaskRequest request) {
        Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
        synchronized (lock) {
            try {
                AgentTask waitingTask = agentTaskService.getTask(taskId);
                String stepId = request == null ? "" : request.getStepId();
                if (stepId == null || stepId.isBlank()) {
                    stepId = currentWaitingStepId(waitingTask).orElse("");
                }
                Optional<PlanStep> waitingStep = findStep(waitingTask, stepId);
                String chatId = resolveChatId(waitingTask);

                larkTaskCardService.updateConfirmCardResolved(chatId, waitingTask, waitingStep.orElse(null), false);

                AgentTask cancelledTask = agentTaskService.cancelTask(taskId,
                        request == null || request.getComment() == null || request.getComment().isBlank()
                                ? "用户通过工作台取消任务"
                                : request.getComment(),
                        request == null || request.getSource() == null || request.getSource().isBlank()
                                ? "workspace"
                                : request.getSource(),
                        request == null ? null : request.getClientId());

                larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, cancelledTask);
                return cancelledTask;
            } finally {
                taskLocks.remove(taskId, lock);
            }
        }
    }

    private String resolveStepId(AgentTask task, String stepId) {
        if (stepId != null && !stepId.isBlank()) {
            return stepId;
        }
        return currentWaitingStepId(task).orElse("");
    }

    private Optional<String> currentWaitingStepId(AgentTask task) {
        return task.getPlanSteps().stream()
                .filter(step -> step.getStatus() == StepStatus.WAIT_CONFIRM)
                .map(PlanStep::getStepId)
                .findFirst();
    }

    private Optional<PlanStep> findStep(AgentTask task, String stepId) {
        if (stepId == null || stepId.isBlank()) {
            return Optional.empty();
        }
        return task.getPlanSteps().stream()
                .filter(step -> stepId.equals(step.getStepId()))
                .findFirst();
    }

    private String resolveChatId(AgentTask task) {
        String source = task == null ? "" : task.getSource();
        if (source != null && source.startsWith("IM:")) {
            int lastSeparator = source.lastIndexOf(':');
            if (lastSeparator > 0 && lastSeparator + 1 < source.length()) {
                return source.substring(lastSeparator + 1);
            }
        }
        return "";
    }

    private void sendLatestCards(String taskId) {
        try {
            AgentTask latestTask = agentTaskService.getTask(taskId);
            larkTaskCardService.sendFollowUpCardsForCurrentState(resolveChatId(latestTask), latestTask);
        } catch (Exception ignored) {
            log.warn("Failed to send latest IM card after workspace action failure, taskId={}", taskId);
        }
    }
}
