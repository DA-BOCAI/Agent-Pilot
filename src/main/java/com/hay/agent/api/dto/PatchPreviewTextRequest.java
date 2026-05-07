package com.hay.agent.api.dto;

import lombok.Data;

@Data
public class PatchPreviewTextRequest {
    /**
     * 1-based page number. Prefer slideId when available.
     */
    private Integer slideNo;

    /**
     * Stable slide id from preview.data.slides[].id.
     */
    private String slideId;

    /**
     * Backend-generated stable locator from workspace.preview.data editableTextId.
     * Preferred by frontend because it avoids manually mapping target/index fields.
     */
    private String editableTextId;

    /**
     * Text target: title, bodyMarkdown, speakerNotes, bullet, blockText, blockItem, tableCell.
     */
    private String target;

    private Integer bulletIndex;
    private Integer blockIndex;
    private Integer itemIndex;
    private Integer rowIndex;
    private Integer cellIndex;
    private String value;
    private String source;
    private String clientId;
}
