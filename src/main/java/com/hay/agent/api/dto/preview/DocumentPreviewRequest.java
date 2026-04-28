package com.hay.agent.api.dto.preview;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentPreviewRequest {

    /**
     * 用户原始需求描述
     * 例如：我需要一个关于AI的文档
     */
    @NotBlank(message = "userInput 不能为空")
    String userInput;

    /**
     * 文档类型
     * 可选值：需求文档、营销方案、技术方案、调研报告、活动策划等
     */
    @NotBlank(message = "docType 不能为空")
    String docType;
}

