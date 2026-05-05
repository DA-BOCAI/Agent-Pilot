package com.hay.agent.service;

import com.hay.agent.domain.AgentTask;
import com.hay.agent.mapper.TaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class TaskWorkspaceStreamService {

    private static final long SSE_TIMEOUT_MILLIS = Duration.ofMinutes(30).toMillis();

    private final TaskMapper taskMapper;
    private final ConcurrentMap<String, List<SseEmitter>> emittersByTaskId = new ConcurrentHashMap<>();

    public SseEmitter subscribe(AgentTask task) {
        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MILLIS);
        String taskId = task.getTaskId();
        emittersByTaskId.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> removeEmitter(taskId, emitter));
        emitter.onTimeout(() -> removeEmitter(taskId, emitter));
        emitter.onError(error -> removeEmitter(taskId, emitter));

        sendToEmitter(taskId, emitter, task, "snapshot");
        return emitter;
    }

    public void publish(AgentTask task) {
        if (task == null || task.getTaskId() == null || task.getTaskId().isBlank()) {
            return;
        }
        List<SseEmitter> emitters = emittersByTaskId.get(task.getTaskId());
        if (emitters == null || emitters.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : emitters) {
            sendToEmitter(task.getTaskId(), emitter, task, "workspace");
        }
    }

    int subscriberCount(String taskId) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        return emitters == null ? 0 : emitters.size();
    }

    private void sendToEmitter(String taskId, SseEmitter emitter, AgentTask task, String eventName) {
        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .id(String.valueOf(task.getUpdatedAt()))
                    .data(taskMapper.toWorkspaceView(task)));
        } catch (IOException | IllegalStateException e) {
            log.debug("发送任务工作台 SSE 事件失败，taskId={}，event={}", taskId, eventName, e);
            removeEmitter(taskId, emitter);
        }
    }

    private void removeEmitter(String taskId, SseEmitter emitter) {
        List<SseEmitter> emitters = emittersByTaskId.get(taskId);
        if (emitters == null) {
            return;
        }
        emitters.remove(emitter);
        if (emitters.isEmpty()) {
            emittersByTaskId.remove(taskId, emitters);
        }
    }
}
