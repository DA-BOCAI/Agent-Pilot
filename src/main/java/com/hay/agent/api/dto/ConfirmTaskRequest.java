package com.hay.agent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;


/**
 * 文件作用：步骤确认请求体。
 * 项目角色：接收前端对确认闸门步骤的通过/拒绝操作。
 */
@Data
public class ConfirmTaskRequest {

    @Schema(description = "需要确认的步骤编号",example = "C_DOC")
    @NotBlank(message = "步骤编号不能为空")
    private String stepId;

    @Schema(description = "是否通过该步骤",example = "true")
    @NotNull(message = "是否通过不能为空")
    private Boolean approved;

    @Schema(description = "确认备注",example = "同意执行该步骤，理由是...")
    private String comment;

    @Schema(description = "操作来源，用于多端同步展示；例如 workspace、lark_card", example = "workspace")
    private String source;

    @Schema(description = "前端客户端标识，用于区分移动端或桌面端操作来源", example = "mobile-web")
    private String clientId;
}

