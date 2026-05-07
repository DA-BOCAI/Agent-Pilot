package com.hay.agent.service.im;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.PlanStep;
import com.hay.agent.domain.StepStatus;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LarkCardCallbackService {

    private final AgentTaskService agentTaskService;
    private final AgentRunner agentRunner;
    private final LarkTaskCardService larkTaskCardService;
    private final ObjectMapper objectMapper;
    private final ConcurrentMap<String, Object> taskLocks = new ConcurrentHashMap<>();

    public ObjectNode handleCallback(JsonNode payload) {
        String challenge = payload.path("challenge").asText("");
        if (!challenge.isBlank()) {
            return objectMapper.createObjectNode().put("challenge", challenge);
        }

        JsonNode value = readActionValue(payload);
        String action = value.path("action").asText("");
        String taskId = value.path("taskId").asText("");
        String stepId = value.path("stepId").asText("");
        String confirmStage = value.path("confirmStage").asText("");
        if (action.isBlank() || taskId.isBlank()) {
            log.warn("忽略缺少 action 或 taskId 的飞书卡片回调：{}", payload);
            return toast("warning", "未识别到任务操作，请刷新后重试");
        }

        AgentTask task = agentTaskService.getTask(taskId);
        String chatId = resolveChatId(payload, task);
        switch (action) {
            case "confirm" -> confirmAndContinueAsync(taskId, stepId, confirmStage, chatId);
            case "reject", "cancel" -> cancelAsync(taskId, stepId, chatId);
            case "open_workspace" -> log.debug("收到工作台跳转回调，taskId={}", taskId);
            default -> {
                log.warn("忽略未知飞书卡片操作，action={}，taskId={}", action, taskId);
                return toast("warning", "暂不支持该操作");
            }
        }
        return toast("success", callbackToastText(action));
    }

    private void confirmAndContinueAsync(String taskId, String stepId, String confirmStage, String chatId) {
        CompletableFuture.runAsync(() -> {
            Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
            synchronized (lock) {
                try {
                    AgentTask waitingTask = agentTaskService.getTask(taskId);
                    String resolvedStepId = resolveStepId(waitingTask, stepId);
                    PlanStep waitingStep = findStep(waitingTask, resolvedStepId).orElse(null);
                    if (waitingStep == null || waitingStep.getStatus() != StepStatus.WAIT_CONFIRM) {
                        log.info("忽略重复或过期的飞书确认回调，taskId={}，stepId={}，当前步骤已不再等待确认",
                                taskId, resolvedStepId);
                        larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, waitingTask);
                        return;
                    }
                    if (!confirmStageMatches(confirmStage, waitingStep)) {
                        log.info("忽略阶段不匹配的飞书确认回调，taskId={}，stepId={}，callbackStage={}，currentStage={}",
                                taskId, resolvedStepId, confirmStage, currentConfirmStage(waitingStep));
                        larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, waitingTask);
                        return;
                    }

                    ConfirmTaskRequest request = new ConfirmTaskRequest();
                    request.setStepId(resolvedStepId);
                    request.setApproved(true);
                    request.setComment("来自飞书确认卡片");
                    request.setSource("lark_card");
                    AgentTask confirmedTask = agentTaskService.confirmStep(taskId, request);
                    larkTaskCardService.updateConfirmCardResolved(chatId, waitingTask, waitingStep, true);
                    larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, confirmedTask);

                    AgentTask advancedTask = agentRunner.runUntilBlocked(taskId);
                    larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, advancedTask);
                } catch (Exception e) {
                    log.error("处理飞书确认卡片回调失败，taskId={}，stepId={}", taskId, stepId, e);
                    sendLatestCards(chatId, taskId);
                } finally {
                    taskLocks.remove(taskId, lock);
                }
            }
        });
    }

    private void cancelAsync(String taskId, String stepId, String chatId) {
        CompletableFuture.runAsync(() -> {
            Object lock = taskLocks.computeIfAbsent(taskId, ignored -> new Object());
            synchronized (lock) {
                try {
                    AgentTask waitingTask = agentTaskService.getTask(taskId);
                    String resolvedStepId = stepId;
                    if (resolvedStepId == null || resolvedStepId.isBlank()) {
                        resolvedStepId = currentWaitingStepId(waitingTask).orElse("");
                    }
                    larkTaskCardService.updateConfirmCardResolved(chatId, waitingTask, findStep(waitingTask, resolvedStepId).orElse(null), false);
                    AgentTask cancelledTask = agentTaskService.cancelTask(taskId, "用户通过飞书卡片取消任务");
                    larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, cancelledTask);
                } catch (Exception e) {
                    log.error("处理飞书取消卡片回调失败，taskId={}，stepId={}", taskId, stepId, e);
                    AgentTask cancelledTask = agentTaskService.cancelTask(taskId, "用户通过飞书卡片取消任务");
                    larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, cancelledTask);
                } finally {
                    taskLocks.remove(taskId, lock);
                }
            }
        });
    }

    private void sendLatestCards(String chatId, String taskId) {
        try {
            larkTaskCardService.sendFollowUpCardsForCurrentState(chatId, agentTaskService.getTask(taskId));
        } catch (Exception ignored) {
            log.warn("飞书卡片回调异常后发送最新卡片失败，taskId={}", taskId);
        }
    }

    private String resolveStepId(AgentTask task, String stepId) {
        if (stepId != null && !stepId.isBlank()) {
            return stepId;
        }
        return currentWaitingStepId(task)
                .orElseThrow(() -> new IllegalStateException("当前任务没有等待确认的步骤"));
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

    private boolean confirmStageMatches(String callbackStage, PlanStep step) {
        return callbackStage == null
                || callbackStage.isBlank()
                || callbackStage.equals(currentConfirmStage(step));
    }

    private String currentConfirmStage(PlanStep step) {
        return step != null
                && step.getPreviewData() != null
                && !step.getPreviewData().isNull()
                && !step.getPreviewData().isMissingNode()
                ? "confirm2"
                : "confirm1";
    }

    private JsonNode readActionValue(JsonNode payload) {
        JsonNode value = firstExisting(payload.at("/event/action/value"), payload.at("/action/value"));
        if (value.isTextual()) {
            try {
                return objectMapper.readTree(value.asText());
            } catch (Exception ignored) {
                return objectMapper.createObjectNode();
            }
        }
        return value.isMissingNode() || value.isNull() ? objectMapper.createObjectNode() : value;
    }

    private JsonNode firstExisting(JsonNode first, JsonNode second) {
        return first == null || first.isMissingNode() || first.isNull() ? second : first;
    }

    private String resolveChatId(JsonNode payload, AgentTask task) {
        String callbackChatId = firstText(
                payload.at("/event/context/open_chat_id"),
                payload.at("/event/message/chat_id"),
                payload.at("/event/message/open_chat_id"),
                payload.at("/event/action/open_chat_id"),
                payload.at("/event/action/value/chatId"),
                payload.at("/event/action/value/openChatId"),
                payload.at("/message/chat_id"),
                payload.at("/message/open_chat_id"),
                payload.at("/action/open_chat_id"),
                payload.at("/action/value/chatId"),
                payload.at("/action/value/openChatId"),
                payload.at("/open_chat_id")
        );
        if (!callbackChatId.isBlank()) {
            return callbackChatId;
        }
        String source = task.getSource();
        if (source != null && source.startsWith("IM:")) {
            int lastSeparator = source.lastIndexOf(':');
            if (lastSeparator > 0 && lastSeparator + 1 < source.length()) {
                return source.substring(lastSeparator + 1);
            }
        }
        return "";
    }

    private String firstText(JsonNode... nodes) {
        for (JsonNode node : nodes) {
            String value = node == null ? "" : node.asText("");
            if (!value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private ObjectNode toast(String type, String content) {
        return objectMapper.createObjectNode()
                .set("toast", objectMapper.createObjectNode()
                        .put("type", type)
                        .put("content", content));
    }

    private String callbackToastText(String action) {
        if ("confirm".equals(action)) {
            return "已确认，Agent 将继续推进任务";
        }
        if ("reject".equals(action) || "cancel".equals(action)) {
            return "已取消，任务将停止推进";
        }
        return "已打开工作台";
    }
}
