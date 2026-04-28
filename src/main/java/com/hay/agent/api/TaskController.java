package com.hay.agent.api;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.TaskView;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.mapper.TaskMapper;
import com.hay.agent.service.AgentTaskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 文件作用：任务编排对外接口。
 * 项目角色：承接前端请求，调用业务层完成任务创建、规划、执行、确认和事件查询。
 */

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务编排接口", description = "用于任务创建、规划、执行、确认和过程追踪")
public class TaskController {

    private final AgentTaskService agentTaskService;
    private final TaskMapper taskMapper;

    public TaskController(AgentTaskService agentTaskService, TaskMapper taskMapper) {
        this.agentTaskService = agentTaskService;
        this.taskMapper = taskMapper;
    }

    @PostMapping
    @Operation(summary = "创建任务", description = "根据输入文本创建一个任务实例")
    public ResponseEntity<TaskView> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.createTask(request)));
    }

    @PostMapping("/{taskId}/plan")
    @Operation(summary = "生成规划", description = "为指定任务生成可执行步骤清单")
    public ResponseEntity<TaskView> planTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.planTask(taskId)));
    }

    @PostMapping("/{taskId}/confirm")
    @Operation(summary = "确认步骤", description = "对需要人工确认的步骤进行通过或拒绝")
    public ResponseEntity<TaskView> confirmTask(@PathVariable String taskId,
                                                @Valid @RequestBody ConfirmTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.confirmStep(taskId, request)));
    }

    @PostMapping("/{taskId}/steps/{stepId}/preview")
    @Operation(summary = "生成步骤预览", description = "为文档/PPT步骤生成结构化预览，用于正式创建飞书产物前的二次确认")
    public ResponseEntity<TaskView> generateStepPreview(@PathVariable String taskId,
                                                        @PathVariable String stepId,
                                                        @RequestBody(required = false) GenerateStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.generateStepPreview(taskId, stepId, request)));
    }

    @PostMapping("/{taskId}/execute")
    @Operation(summary = "执行任务", description = "从当前进度继续执行，直到完成或遇到下一个确认闸门")
    public ResponseEntity<TaskView> executeTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.executeTask(taskId)));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情", description = "返回任务状态、步骤、产物和事件信息")
    public ResponseEntity<TaskView> getTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.getTask(taskId)));
    }

    @GetMapping("/{taskId}/events")
    @Operation(summary = "查询任务事件", description = "返回任务执行过程中的完整事件时间线")
    public ResponseEntity<List<TaskEvent>> getEvents(@PathVariable String taskId) {
        return ResponseEntity.ok(agentTaskService.listEvents(taskId));
    }
}

