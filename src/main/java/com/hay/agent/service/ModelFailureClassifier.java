package com.hay.agent.service;

import org.springframework.web.client.RestClientResponseException;

import java.util.Locale;
import java.util.Optional;

public final class ModelFailureClassifier {

    private ModelFailureClassifier() {
    }

    public static FailureInfo classify(Throwable throwable) {
        String rawMessage = collectMessage(throwable);
        String normalized = rawMessage.toLowerCase(Locale.ROOT);
        if (isRateLimit(normalized, throwable)) {
            return new FailureInfo(
                    "LLM_RATE_LIMIT",
                    "大模型请求受限，可能触发 TPM/限流。请稍等片刻后重试，或减少一次生成的内容规模。",
                    "建议等待 1-3 分钟后重试；如果连续出现，请减少页数/篇幅或切换可用模型。"
            );
        }
        if (isLikelyModelFailure(normalized)) {
            return new FailureInfo(
                    "LLM_REQUEST_FAILED",
                    "大模型请求出问题了，当前任务没有继续生成内容。",
                    "建议稍后重试；如果仍失败，请检查模型服务、网络或 API 配额。"
            );
        }
        return new FailureInfo(
                "TASK_EXECUTION_FAILED",
                "任务执行失败，请进入工作台查看失败原因和事件时间线。",
                "可根据事件详情调整输入后重新发起任务。"
        );
    }

    public static Optional<String> latestUserMessage(com.hay.agent.domain.AgentTask task) {
        if (task == null || task.getEvents() == null) {
            return Optional.empty();
        }
        for (int i = task.getEvents().size() - 1; i >= 0; i--) {
            com.hay.agent.domain.TaskEvent event = task.getEvents().get(i);
            if (event == null || event.getMetadata() == null) {
                continue;
            }
            String message = event.getMetadata().get("userMessage");
            if (message != null && !message.isBlank()) {
                return Optional.of(message);
            }
        }
        return Optional.empty();
    }

    private static boolean isRateLimit(String normalized, Throwable throwable) {
        if (throwable instanceof RestClientResponseException restException && restException.getStatusCode().value() == 429) {
            return true;
        }
        Throwable current = throwable == null ? null : throwable.getCause();
        while (current != null) {
            if (current instanceof RestClientResponseException restException && restException.getStatusCode().value() == 429) {
                return true;
            }
            current = current.getCause();
        }
        return normalized.contains("tpm")
                || normalized.contains("tokens per minute")
                || normalized.contains("rate limit")
                || normalized.contains("ratelimit")
                || normalized.contains("too many requests")
                || normalized.contains("resource_exhausted")
                || normalized.contains("quota");
    }

    private static boolean isLikelyModelFailure(String normalized) {
        return normalized.contains("大模型")
                || normalized.contains("llm")
                || normalized.contains("openai")
                || normalized.contains("model")
                || normalized.contains("completion")
                || normalized.contains("chat/completions");
    }

    private static String collectMessage(Throwable throwable) {
        StringBuilder builder = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (current.getMessage() != null && !current.getMessage().isBlank()) {
                if (builder.length() > 0) {
                    builder.append(" | ");
                }
                builder.append(current.getMessage());
            }
            current = current.getCause();
        }
        return builder.toString();
    }

    public record FailureInfo(String kind, String userMessage, String retryAdvice) {
    }
}
