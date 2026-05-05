package com.hay.agent.api.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

@Data
public class UpdateStepPreviewRequest {
    /**
     * 卡片 2 或详情页编辑后的结构化预览数据。
     */
    private JsonNode previewData;

    /**
     * 操作来源，用于工作台时间轴和多端同步展示；例如 workspace、lark_card。
     */
    private String source;

    /**
     * 客户端标识，用于区分桌面端、移动端等；例如 desktop-web、mobile-web。
     */
    private String clientId;
}
