package com.hay.agent.service.im;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ImIntentClassification {
    private ImIntentType type;
    private String reason;
    private double confidence;

    public static ImIntentClassification of(ImIntentType type, String reason, double confidence) {
        return ImIntentClassification.builder()
                .type(type)
                .reason(reason)
                .confidence(confidence)
                .build();
    }
}
