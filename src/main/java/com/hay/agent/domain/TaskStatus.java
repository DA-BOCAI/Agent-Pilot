package com.hay.agent.domain;

/**
 * 任务状态枚举与允许转移规则
 */
public enum TaskStatus {
    CREATED,
    PLANNED,
    WAIT_CONFIRM,
    RUNNING,
    DELIVERED,
    FAILED
}

