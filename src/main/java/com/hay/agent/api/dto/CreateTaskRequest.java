package com.hay.agent.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 文件作用：创建任务请求体。
 * 项目角色：承接前端传入的用户输入、来源、用户标识和请求幂等标识。
 */
@Data
public class CreateTaskRequest {

    @Schema(description = "用户输入的任务文本",example = "请帮我写一篇关于人工智能的文章，要求1000字以上，结构清晰，内容翔实。")
    @NotBlank(message = "输入内容不能为空")
    private String inputText;

    @Schema(description = "请求来源",example = "im_text")
    private String source;

    @Schema(description = "发起任务用户ID",example = "u-01")
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    @Schema(description = "请求幂等ID",example = "req-2001")
    @NotBlank(message = "请求ID不能为空")
    private String requestId;
}

