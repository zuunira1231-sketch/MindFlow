package com.cogniflow.dto;

import lombok.Data;

@Data
public class AnalyzeDraftRequest {

    /**
     * 兼容旧入口：用户直接粘贴的聊天内容。
     */
    private String chatContent;

    /**
     * 新入口：已经导入并保存的完整对话 ID。
     */
    private Long conversationId;
}
