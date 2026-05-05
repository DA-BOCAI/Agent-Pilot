package com.hay.agent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

@Data
public class CancelTaskRequest {

    @Schema(description = "需要取消的步骤编号；为空时取消当前等待确认的步骤或整个任务", example = "C_DOC")
    private String stepId;

    @Schema(description = "取消原因", example = "用户在工作台取消")
    private String comment;

    @Schema(description = "操作来源，用于多端同步展示；例如 workspace、lark_card", example = "workspace")
    private String source;

    @Schema(description = "前端客户端标识，用于区分移动端或桌面端操作来源", example = "mobile-web")
    private String clientId;
}
