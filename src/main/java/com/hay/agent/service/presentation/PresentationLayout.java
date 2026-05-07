package com.hay.agent.service.presentation;

public enum PresentationLayout {
    COVER("cover"),
    CONTENT("content"),
    TWO_COLUMN("two_column"),
    METRIC_CARDS("metric_cards"),
    TIMELINE("timeline"),
    COMPARISON_TABLE("comparison_table"),
    SECTION_DIVIDER("section_divider"),
    CLOSING("closing");

    private final String code;

    PresentationLayout(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    public static PresentationLayout fromCode(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        for (PresentationLayout layout : values()) {
            if (layout.code.equalsIgnoreCase(code) || layout.name().equalsIgnoreCase(code)) {
                return layout;
            }
        }
        return null;
    }
}
