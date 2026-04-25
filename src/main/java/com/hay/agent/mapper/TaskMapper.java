package com.hay.agent.mapper;

import com.hay.agent.api.dto.TaskView;
import com.hay.agent.domain.AgentTask;
import org.springframework.stereotype.Component;

/**
 * 文件作用：任务对象映射器。
 * 项目角色：将领域模型 AgentTask 转换为接口输出模型 TaskView，保持控制层精简。
 */
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
}

