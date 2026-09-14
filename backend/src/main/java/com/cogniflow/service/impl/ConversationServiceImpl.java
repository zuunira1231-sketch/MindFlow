package com.cogniflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.cogniflow.dto.ConversationMessageInput;
import com.cogniflow.entity.Conversation;
import com.cogniflow.entity.ConversationMessage;
import com.cogniflow.mapper.ConversationMapper;
import com.cogniflow.mapper.ConversationMessageMapper;
import com.cogniflow.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.List;
import java.util.ArrayList;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ConversationServiceImpl
        implements ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper conversationMessageMapper;

    @Override
    @Transactional
    public Conversation importText(
            Long userId,
            String title,
            String sourcePlatform,
            String content
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        if (!StringUtils.hasText(content)) {
            throw new IllegalArgumentException("聊天内容不能为空");
        }

        String normalizedContent = content.trim();
        String contentHash = sha256("user\n" + normalizedContent);

        /*
         * 相同用户导入相同内容时，直接返回原 Conversation，
         * 不重复创建消息记录。
         */
        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getContentHash, contentHash)
                        .last("LIMIT 1")
        );

        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();

        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(
                StringUtils.hasText(title) ? title.trim() : null
        );
        conversation.setSourceType("TEXT");
        conversation.setSourcePlatform(
                StringUtils.hasText(sourcePlatform)
                        ? sourcePlatform.trim().toLowerCase()
                        : "manual"
        );
        conversation.setStatus("IMPORTED");
        conversation.setMessageCount(1);
        conversation.setContentHash(contentHash);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);

        conversationMapper.insert(conversation);

        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversation.getId());
        message.setSequenceNo(1);
        message.setRole("user");
        message.setContent(normalizedContent);
        message.setCreatedAt(now);

        conversationMessageMapper.insert(message);

        return conversation;
    }

    @Override
    @Transactional
    public Conversation importMessages(
            Long userId,
            String title,
            String sourcePlatform,
            List<ConversationMessageInput> messages
    ) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (messages == null || messages.isEmpty()) {
            throw new IllegalArgumentException("对话消息不能为空");
        }

        List<ConversationMessageInput> normalized = new ArrayList<>();
        StringBuilder hashInput = new StringBuilder();

        for (ConversationMessageInput input : messages) {
            if (input == null
                    || !StringUtils.hasText(input.getRole())
                    || !StringUtils.hasText(input.getContent())) {
                throw new IllegalArgumentException("每条消息都必须有角色和内容");
            }

            String role = input.getRole().trim().toLowerCase(Locale.ROOT);
            if (!role.equals("user") && !role.equals("assistant")) {
                throw new IllegalArgumentException(
                        "只支持 user 和 assistant 消息"
                );
            }

            String content = input.getContent().trim();
            ConversationMessageInput item = new ConversationMessageInput();
            item.setRole(role);
            item.setContent(content);
            normalized.add(item);

            hashInput.append(role.length()).append(':').append(role)
                    .append(content.length()).append(':').append(content);
        }

        String contentHash = sha256(hashInput.toString());
        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getContentHash, contentHash)
                        .last("LIMIT 1")
        );
        if (existing != null) {
            return existing;
        }

        LocalDateTime now = LocalDateTime.now();
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setTitle(
                StringUtils.hasText(title) ? title.trim() : null
        );
        conversation.setSourceType("MESSAGES");
        conversation.setSourcePlatform(
                StringUtils.hasText(sourcePlatform)
                        ? sourcePlatform.trim().toLowerCase(Locale.ROOT)
                        : "unknown"
        );
        conversation.setStatus("IMPORTED");
        conversation.setMessageCount(normalized.size());
        conversation.setContentHash(contentHash);
        conversation.setCreatedAt(now);
        conversation.setUpdatedAt(now);
        conversationMapper.insert(conversation);

        for (int index = 0; index < normalized.size(); index++) {
            ConversationMessageInput item = normalized.get(index);
            ConversationMessage message = new ConversationMessage();
            message.setConversationId(conversation.getId());
            message.setSequenceNo(index + 1);
            message.setRole(item.getRole());
            message.setContent(item.getContent());
            message.setCreatedAt(now);
            conversationMessageMapper.insert(message);
        }

        return conversation;
    }

    @Override
    public String buildAnalysisContent(
            Long conversationId,
            Long userId
    ) {
        List<ConversationMessage> messages =
                loadOwnedMessages(conversationId, userId);

        StringBuilder content = new StringBuilder();

        for (ConversationMessage message : messages) {
            content.append('[')
                    .append(message.getRole().toUpperCase(Locale.ROOT))
                    .append("]\n")
                    .append(message.getContent())
                    .append("\n\n");
        }

        return content.toString().trim();
    }

    @Override
    public List<String> buildAnalysisChunks(
            Long conversationId,
            Long userId,
            int maxCharacters
    ) {
        if (maxCharacters < 1000) {
            throw new IllegalArgumentException(
                    "每段最大字符数不能小于 1000"
            );
        }

        List<ConversationMessage> messages =
                loadOwnedMessages(conversationId, userId);
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (ConversationMessage message : messages) {
            String role = message.getRole().toUpperCase(Locale.ROOT);
            String text = message.getContent();
            String prefix = "[" + role + "]\n";
            int available = maxCharacters - prefix.length() - 2;

            for (int offset = 0; offset < text.length();) {
                int end = Math.min(offset + available, text.length());
                if (end < text.length()
                        && Character.isHighSurrogate(text.charAt(end - 1))) {
                    end--;
                }
                String part = prefix + text.substring(offset, end) + "\n\n";

                if (current.length() > 0
                        && current.length() + part.length() > maxCharacters) {
                    chunks.add(current.toString().trim());
                    current.setLength(0);
                }
                current.append(part);
                offset = end;
            }
        }

        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }
        return chunks;
    }

    private List<ConversationMessage> loadOwnedMessages(
            Long conversationId,
            Long userId
    ) {
        if (conversationId == null || userId == null) {
            throw new IllegalArgumentException(
                    "conversationId 和 userId 不能为空"
            );
        }

        Conversation conversation =
                conversationMapper.selectById(conversationId);

        if (conversation == null) {
            throw new IllegalArgumentException(
                    "Conversation 不存在，id=" + conversationId
            );
        }

        if (!userId.equals(conversation.getUserId())) {
            throw new IllegalArgumentException(
                    "无权读取该 Conversation，id=" + conversationId
            );
        }

        List<ConversationMessage> messages =
                conversationMessageMapper.selectList(
                        new LambdaQueryWrapper<ConversationMessage>()
                                .eq(
                                        ConversationMessage::getConversationId,
                                        conversationId
                                )
                                .orderByAsc(
                                        ConversationMessage::getSequenceNo
                                )
                );

        if (messages.isEmpty()) {
            throw new IllegalArgumentException(
                    "Conversation 中没有可分析的消息"
            );
        }

        return messages;
    }

    private String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "当前运行环境不支持 SHA-256",
                    exception
            );
        }
    }
}
