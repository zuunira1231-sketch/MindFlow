package com.cogniflow.service;

import com.cogniflow.dto.ConversationMessageInput;
import com.cogniflow.entity.Conversation;

import java.util.List;

public interface ConversationService {

    /**
     * 将旧入口粘贴的文字保存为一条单消息对话。
     */
    Conversation importText(
            Long userId,
            String title,
            String sourcePlatform,
            String content
    );

    /**
     * 按原顺序导入完整的 user/assistant 对话。
     */
    Conversation importMessages(
            Long userId,
            String title,
            String sourcePlatform,
            List<ConversationMessageInput> messages
    );

    /**
     * 将指定对话按消息顺序整理成第一次 AI 的输入。
     */
    String buildAnalysisContent(
            Long conversationId,
            Long userId
    );

    /**
     * 按消息边界切分第一次 AI 的输入；单条超长消息继续分块。
     */
    List<String> buildAnalysisChunks(
            Long conversationId,
            Long userId,
            int maxCharacters
    );
}
