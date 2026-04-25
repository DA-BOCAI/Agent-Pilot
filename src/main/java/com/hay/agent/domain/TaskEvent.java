package com.hay.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 事件模型
 */

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskEvent {
    private Instant timestamp;
    private String type;
    private String message;
    private Map<String, String> metadata;
}

