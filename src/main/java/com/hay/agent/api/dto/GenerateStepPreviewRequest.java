package com.hay.agent.api.dto;

import lombok.Data;

@Data
public class GenerateStepPreviewRequest {
    /**
     * PPT 可选主题：business / tech / campaign / minimal。
     */
    private String theme;
}
