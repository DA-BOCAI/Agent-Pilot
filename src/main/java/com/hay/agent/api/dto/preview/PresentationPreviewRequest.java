package com.hay.agent.api.dto.preview;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PresentationPreviewRequest {
    /**
     * 用户原始需求描述
     * 例如：我需要一个关于AI的演示文稿
     */
    @NotBlank(message = "userInput 不能为空")
    String userInput;

    /**
     * 演示文稿主题
     * 例如：AI技术
     */
    @NotBlank(message = "topic 不能为空")
    String topic;

    /**
     * 可选主题：business / tech / campaign / minimal。
     * 不传时后端会根据用户输入和主题自动推断。
     */
    String theme;
}

