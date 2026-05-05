package com.hay.agent.service;

import com.hay.agent.domain.AgentTask;
import com.hay.agent.domain.TaskStatus;
import com.hay.agent.mapper.TaskMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaskWorkspaceStreamServiceTest {

    @Test
    void shouldRegisterEmitterAndPublishWorkspaceView() {
        TaskWorkspaceStreamService streamService = new TaskWorkspaceStreamService(new TaskMapper());
        AgentTask task = AgentTask.builder()
                .taskId("task-1")
                .inputText("生成一份周报")
                .status(TaskStatus.CREATED)
                .nextAction("plan")
                .updatedAt(Instant.now())
                .build();

        SseEmitter emitter = streamService.subscribe(task);

        assertEquals(1, streamService.subscriberCount("task-1"));

        task.setStatus(TaskStatus.PLANNED);
        task.setUpdatedAt(Instant.now());
        streamService.publish(task);

        assertEquals(1, streamService.subscriberCount("task-1"));
        emitter.complete();
    }
}
