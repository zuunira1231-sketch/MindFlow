package com.cogniflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ConversationImportResponse {

    private Long conversationId;

    private String status;

    private Integer messageCount;
}
