package com.hay.agent.service.presentation;

import java.util.Locale;

public enum PresentationTheme {
    BUSINESS(
            "business",
            "rgb(248,250,252)",
            "rgb(59,130,246)",
            "rgb(15,23,42)",
            "rgb(30,41,59)",
            "rgb(100,116,139)"
    ),
    TECH(
            "tech",
            "linear-gradient(135deg,rgba(15,23,42,1) 0%,rgba(30,64,175,1) 100%)",
            "rgb(96,165,250)",
            "rgb(248,250,252)",
            "rgb(226,232,240)",
            "rgb(186,230,253)"
    ),
    CAMPAIGN(
            "campaign",
            "linear-gradient(135deg,rgba(153,27,27,1) 0%,rgba(234,88,12,1) 100%)",
            "rgb(253,224,71)",
            "rgb(255,247,237)",
            "rgb(255,237,213)",
            "rgb(254,243,199)"
    ),
    MINIMAL(
            "minimal",
            "rgb(255,255,255)",
            "rgb(34,197,94)",
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
