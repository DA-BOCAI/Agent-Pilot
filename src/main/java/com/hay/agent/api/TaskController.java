package com.hay.agent.api;

import com.hay.agent.api.dto.ConfirmTaskRequest;
import com.hay.agent.api.dto.CancelTaskRequest;
import com.hay.agent.api.dto.CreateTaskRequest;
import com.hay.agent.api.dto.GenerateStepPreviewRequest;
import com.hay.agent.api.dto.RefineStepPreviewRequest;
import com.hay.agent.api.dto.TaskCardView;
import com.hay.agent.api.dto.TaskView;
import com.hay.agent.api.dto.TaskWorkspaceView;
import com.hay.agent.api.dto.UpdateStepPreviewRequest;
import com.hay.agent.domain.TaskEvent;
import com.hay.agent.mapper.TaskMapper;
import com.hay.agent.runner.AgentRunner;
import com.hay.agent.service.AgentTaskService;
import com.hay.agent.service.workflow.TaskActionCoordinator;
import com.hay.agent.service.TaskWorkspaceStreamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tasks")
@Tag(name = "任务编排接口", description = "用于任务创建、规划、执行、确认、预览和过程追踪")
public class TaskController {

    private final AgentTaskService agentTaskService;
    private final AgentRunner agentRunner;
    private final TaskActionCoordinator taskActionCoordinator;
    private final TaskMapper taskMapper;
    private final TaskWorkspaceStreamService taskWorkspaceStreamService;

    public TaskController(AgentTaskService agentTaskService,
                          AgentRunner agentRunner,
                          TaskActionCoordinator taskActionCoordinator,
                          TaskMapper taskMapper,
                          TaskWorkspaceStreamService taskWorkspaceStreamService) {
        this.agentTaskService = agentTaskService;
        this.agentRunner = agentRunner;
        this.taskActionCoordinator = taskActionCoordinator;
        this.taskMapper = taskMapper;
        this.taskWorkspaceStreamService = taskWorkspaceStreamService;
    }

    @PostMapping
    @Operation(summary = "创建任务", description = "根据输入文本创建一个任务实例")
    public ResponseEntity<TaskView> createTask(@Valid @RequestBody CreateTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.createTask(request)));
    }

    @PostMapping("/{taskId}/plan")
    @Operation(summary = "生成规划（调试）", description = "调试/兼容入口；IM 演示主线通常由 Agent Runner 自动调用")
    public ResponseEntity<TaskView> planTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.planTask(taskId)));
    }

    @PostMapping("/{taskId}/execute")
    @Operation(summary = "执行任务（调试）", description = "调试/兼容入口；从当前进度继续执行，直到完成或遇到下一个确认闸门")
    public ResponseEntity<TaskView> executeTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.executeTask(taskId)));
    }

    @PostMapping("/{taskId}/run")
    @Operation(summary = "自动推进任务（调试）", description = "调试/兼容入口；由 Agent Runner 自动推进任务，直到完成或遇到确认闸门")
    public ResponseEntity<TaskView> runTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentRunner.runUntilBlocked(taskId)));
    }

    @Deprecated
    @PostMapping("/{taskId}/confirm")
    @Operation(summary = "确认步骤（Legacy）", description = "兼容旧调试入口；工作台前端请使用 /workspace/confirm")
    public ResponseEntity<TaskView> confirmTask(@PathVariable String taskId,
                                                @Valid @RequestBody ConfirmTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.confirmStep(taskId, request)));
    }

    @Deprecated
    @PostMapping("/{taskId}/confirm-and-run")
    @Operation(summary = "确认并继续推进（Legacy）", description = "兼容旧调试入口；工作台前端请使用 /workspace/confirm")
    public ResponseEntity<TaskView> confirmAndRunTask(@PathVariable String taskId,
                                                      @Valid @RequestBody ConfirmTaskRequest request) {
        agentTaskService.confirmStep(taskId, request);
        return ResponseEntity.ok(taskMapper.toView(agentRunner.runUntilBlocked(taskId)));
    }

    @PostMapping("/{taskId}/workspace/confirm")
    @Operation(summary = "工作台确认并继续推进", description = "工作台确认当前步骤后，统一推进 Agent 链路，并同步更新飞书数字卡片")
    public ResponseEntity<TaskWorkspaceView> confirmFromWorkspace(@PathVariable String taskId,
                                                                  @Valid @RequestBody ConfirmTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toWorkspaceView(taskActionCoordinator.confirmFromWorkspace(taskId, request)));
    }

    @PostMapping("/{taskId}/workspace/cancel")
    @Operation(summary = "工作台取消任务", description = "工作台取消当前确认步骤或任务，并同步更新飞书数字卡片")
    public ResponseEntity<TaskWorkspaceView> cancelFromWorkspace(@PathVariable String taskId,
                                                                 @RequestBody(required = false) CancelTaskRequest request) {
        return ResponseEntity.ok(taskMapper.toWorkspaceView(taskActionCoordinator.cancelFromWorkspace(taskId, request)));
    }

    @PostMapping("/{taskId}/steps/{stepId}/preview")
    @Operation(summary = "生成步骤预览（调试）", description = "调试/兼容入口；confirm1 后通常由 Agent Runner 自动生成预览")
    public ResponseEntity<TaskView> generateStepPreview(@PathVariable String taskId,
                                                        @PathVariable String stepId,
                                                        @RequestBody(required = false) GenerateStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.generateStepPreview(taskId, stepId, request)));
    }

    @Deprecated
    @PutMapping("/{taskId}/steps/{stepId}/preview")
    @Operation(summary = "更新步骤预览（Legacy）", description = "兼容旧调试入口；工作台前端请使用 /workspace/steps/{stepId}/preview")
    public ResponseEntity<TaskView> updateStepPreview(@PathVariable String taskId,
                                                      @PathVariable String stepId,
                                                      @RequestBody UpdateStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.updateStepPreview(taskId, stepId, request)));
    }

    @PutMapping("/{taskId}/workspace/steps/{stepId}/preview")
    @Operation(summary = "工作台更新步骤预览", description = "保存工作台编辑后的结构化预览数据，并返回完整工作台快照")
    public ResponseEntity<TaskWorkspaceView> updateStepPreviewFromWorkspace(@PathVariable String taskId,
                                                                            @PathVariable String stepId,
                                                                            @RequestBody UpdateStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toWorkspaceView(agentTaskService.updateStepPreview(taskId, stepId, request)));
    }

    @Deprecated
    @PostMapping("/{taskId}/steps/{stepId}/preview/refine")
    @Operation(summary = "自然语言精修预览（Legacy）", description = "兼容旧调试入口；工作台前端请使用 /workspace/steps/{stepId}/preview/refine")
    public ResponseEntity<TaskView> refineStepPreview(@PathVariable String taskId,
                                                      @PathVariable String stepId,
                                                      @RequestBody RefineStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.refineStepPreview(taskId, stepId, request)));
    }

    @PostMapping("/{taskId}/workspace/steps/{stepId}/preview/refine")
    @Operation(summary = "工作台自然语言精修预览", description = "根据用户自然语言修改当前预览数据，并返回完整工作台快照")
    public ResponseEntity<TaskWorkspaceView> refineStepPreviewFromWorkspace(@PathVariable String taskId,
                                                                            @PathVariable String stepId,
                                                                            @RequestBody RefineStepPreviewRequest request) {
        return ResponseEntity.ok(taskMapper.toWorkspaceView(agentTaskService.refineStepPreview(taskId, stepId, request)));
    }

    @GetMapping("/{taskId}")
    @Operation(summary = "查询任务详情", description = "返回任务状态、步骤、产物和事件信息")
    public ResponseEntity<TaskView> getTask(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toView(agentTaskService.getTask(taskId)));
    }

    @GetMapping("/{taskId}/cards")
    @Operation(summary = "查询任务卡片视图", description = "返回飞书三张数字卡片所需的进度、确认和交付数据")
    public ResponseEntity<TaskCardView> getTaskCards(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toCardView(agentTaskService.getTask(taskId)));
    }

    @GetMapping("/{taskId}/workspace")
    @Operation(summary = "查询任务工作台视图", description = "返回同一个详情网页所需的任务、卡片、预览、产物和时间线数据")
    public ResponseEntity<TaskWorkspaceView> getTaskWorkspace(@PathVariable String taskId) {
        return ResponseEntity.ok(taskMapper.toWorkspaceView(agentTaskService.getTask(taskId)));
    }

    @GetMapping("/{taskId}/workspace/stream")
    @Operation(summary = "订阅任务工作台实时更新", description = "通过 SSE 推送任务、计划、确认、预览和产物的最新工作台视图")
    public SseEmitter streamTaskWorkspace(@PathVariable String taskId) {
        return taskWorkspaceStreamService.subscribe(agentTaskService.getTask(taskId));
    }

    @GetMapping("/{taskId}/events")
    @Operation(summary = "查询任务事件", description = "返回任务执行过程中的完整事件时间线")
    public ResponseEntity<List<TaskEvent>> getEvents(@PathVariable String taskId) {
        return ResponseEntity.ok(agentTaskService.listEvents(taskId));
    }
}
