package com.hay.agent.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hay.agent.domain.AgentTask;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 文件作用：任务存储组件。
 * 项目角色：优先使用 Redis 持久化任务，Redis 不可用时自动回退到内存存储，保障开发和测试可用性。
 */
@Component
public class TaskStore {

    private final ObjectMapper objectMapper;

    @Nullable
    private final StringRedisTemplate redisTemplate;

    private final ConcurrentMap<String, AgentTask> localFallback = new ConcurrentHashMap<>();

    @Value("${agent.store.redis.enabled:true}")
    private boolean redisEnabled;

    public TaskStore(ObjectMapper objectMapper, @Nullable StringRedisTemplate redisTemplate) {
        this.objectMapper = objectMapper;
        this.redisTemplate = redisTemplate;
    }

    public AgentTask save(AgentTask task) {
        if (redisEnabled && redisTemplate != null) {
            try {
                String json = objectMapper.writeValueAsString(task);
                redisTemplate.opsForValue().set(redisKey(task.getTaskId()), json);
                localFallback.remove(task.getTaskId());
                return task;
            } catch (Exception ignored) {
                // Redis 不可用时进入本地回退，避免中断主流程。
            }
        }

        localFallback.put(task.getTaskId(), task);
        return task;
    }

    public Optional<AgentTask> findById(String taskId) {
        if (redisEnabled && redisTemplate != null) {
            try {
                String json = redisTemplate.opsForValue().get(redisKey(taskId));
                if (json != null && !json.isBlank()) {
                    return Optional.of(objectMapper.readValue(json, AgentTask.class));
                }
            } catch (JsonProcessingException ignored) {
                // JSON 解析失败时回退本地，保证查询不中断。
            } catch (Exception ignored) {
                // Redis 连接失败时回退本地。
            }
        }

        return Optional.ofNullable(localFallback.get(taskId));
    }

    private String redisKey(String taskId) {
        return "agent:task:" + taskId;
    }
}

