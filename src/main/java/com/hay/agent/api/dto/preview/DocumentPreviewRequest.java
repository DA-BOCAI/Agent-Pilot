package com.hay.agent.api.dto.preview;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class DocumentPreviewRequest {
    @NotBlank(message = "userInput 不能为空")
    String userInput;

    @NotBlank(message = "docType 不能为空")
    String docType;
}

