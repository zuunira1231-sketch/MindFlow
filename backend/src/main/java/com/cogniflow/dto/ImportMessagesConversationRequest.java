package com.cogniflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class ImportMessagesConversationRequest {

    private String title;

    private String sourcePlatform;

    @Valid
    @NotEmpty(message = "对话消息不能为空")
    private List<ConversationMessageInput> messages;
}
