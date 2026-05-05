package com.hay.agent.service.presentation;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class PresentationSlide {
    int slideNo;
    String title;
    String layout;
    List<SlideBlock> blocks;

    @Value
    @Builder
    public static class SlideBlock {
        String type;
        String text;
        List<String> items;
        List<List<String>> rows;
    }
}
