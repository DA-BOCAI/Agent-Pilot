package com.hay.agent.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Artifact {
    private String type;
    private String title;
    private String url;
    private String externalId;
    private Map<String, Object> metadata;
}

