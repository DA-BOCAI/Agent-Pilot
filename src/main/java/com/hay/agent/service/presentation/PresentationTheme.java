package com.hay.agent.service.presentation;

import java.util.Locale;

public enum PresentationTheme {
    BUSINESS(
            "business",
            "rgb(248,247,242)",
            "rgb(15,118,110)",
            "rgb(17,24,39)",
            "rgb(55,65,81)",
            "rgb(115,115,105)"
    ),
    TECH(
            "tech",
            "rgb(11,18,32)",
            "rgb(45,212,191)",
            "rgb(248,250,252)",
            "rgb(203,213,225)",
            "rgb(148,163,184)"
    ),
    CAMPAIGN(
            "campaign",
            "rgb(43,18,30)",
            "rgb(244,114,92)",
            "rgb(255,247,237)",
            "rgb(254,226,208)",
            "rgb(253,186,116)"
    ),
    MINIMAL(
            "minimal",
            "rgb(255,255,255)",
            "rgb(79,70,229)",
            "rgb(17,24,39)",
            "rgb(55,65,81)",
            "rgb(107,114,128)"
    );

    private final String code;
    private final String background;
    private final String accent;
    private final String titleColor;
    private final String bodyColor;
    private final String captionColor;

    PresentationTheme(String code,
                      String background,
                      String accent,
                      String titleColor,
                      String bodyColor,
                      String captionColor) {
        this.code = code;
        this.background = background;
        this.accent = accent;
        this.titleColor = titleColor;
        this.bodyColor = bodyColor;
        this.captionColor = captionColor;
    }

    public static PresentationTheme fromText(String text) {
        String value = text == null ? "" : text.toLowerCase(Locale.ROOT);
        if (value.contains("科技") || value.contains("ai") || value.contains("tech")) {
            return TECH;
        }
        if (value.contains("双十一") || value.contains("大促") || value.contains("营销")
                || value.contains("campaign") || value.contains("活动")) {
            return CAMPAIGN;
        }
        if (value.contains("极简") || value.contains("简洁") || value.contains("minimal")) {
            return MINIMAL;
        }
        return BUSINESS;
    }

    public String getCode() {
        return code;
    }

    public String getBackground() {
        return background;
    }

    public String getAccent() {
        return accent;
    }

    public String getTitleColor() {
        return titleColor;
    }

    public String getBodyColor() {
        return bodyColor;
    }

    public String getCaptionColor() {
        return captionColor;
    }

    public String getBackgroundSafeTextColor() {
        return this == MINIMAL || this == BUSINESS ? "rgb(255,255,255)" : titleColor;
    }
}
