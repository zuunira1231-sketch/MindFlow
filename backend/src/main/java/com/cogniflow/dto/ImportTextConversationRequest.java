package com.cogniflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ImportTextConversationRequest {

    private String title;

    private String sourcePlatform;

    @NotBlank(message = "聊天内容不能为空")
    private String content;
}
