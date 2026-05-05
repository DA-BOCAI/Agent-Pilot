package com.hay.agent.api.dto;

import lombok.Data;

@Data
public class RefineStepPreviewRequest {
    /**
     * 来自 IM 或卡片 2 详情页的自然语言精修指令。
     */
    private String instruction;

    /**
     * 操作来源，用于工作台时间轴和多端同步展示；例如 workspace、lark_card。
     */
    private String source;

    /**
     * 客户端标识，用于区分桌面端、移动端等；例如 desktop-web、mobile-web。
     */
    private String clientId;
}
