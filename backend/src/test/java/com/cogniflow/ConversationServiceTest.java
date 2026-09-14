package com.cogniflow;

import com.cogniflow.entity.Conversation;
import com.cogniflow.entity.ConversationMessage;
import com.cogniflow.mapper.ConversationMapper;
import com.cogniflow.mapper.ConversationMessageMapper;
import com.cogniflow.service.impl.ConversationServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConversationServiceTest {

    @Test
    void splitsAtMessageBoundariesAndPreservesOrder() {
        ConversationMapper conversationMapper =
                mock(ConversationMapper.class);
        ConversationMessageMapper messageMapper =
                mock(ConversationMessageMapper.class);

        Conversation conversation = new Conversation();
        conversation.setId(7L);
        conversation.setUserId(1L);
        when(conversationMapper.selectById(7L)).thenReturn(conversation);

        ConversationMessage first = new ConversationMessage();
        first.setRole("user");
        first.setContent("甲".repeat(700));

        ConversationMessage second = new ConversationMessage();
        second.setRole("assistant");
        second.setContent("乙".repeat(700));

        when(messageMapper.selectList(any())).thenReturn(
                List.of(first, second)
        );

        ConversationServiceImpl service =
                new ConversationServiceImpl(
                        conversationMapper,
                        messageMapper
                );

        List<String> chunks =
                service.buildAnalysisChunks(7L, 1L, 1000);

        assertEquals(2, chunks.size());
        assertTrue(chunks.get(0).startsWith("[USER]"));
        assertTrue(chunks.get(1).startsWith("[ASSISTANT]"));
        assertTrue(chunks.stream().allMatch(
                chunk -> chunk.length() <= 1000
        ));
    }

    @Test
    void splitsLongSingleMessageWithoutLosingText() {
        ConversationMapper conversationMapper =
                mock(ConversationMapper.class);
        ConversationMessageMapper messageMapper =
                mock(ConversationMessageMapper.class);

        Conversation conversation = new Conversation();
        conversation.setId(8L);
        conversation.setUserId(1L);
        when(conversationMapper.selectById(8L)).thenReturn(conversation);

        String original = "认知".repeat(900) + "🙂";
        ConversationMessage message = new ConversationMessage();
        message.setRole("user");
        message.setContent(original);
        when(messageMapper.selectList(any())).thenReturn(List.of(message));

        ConversationServiceImpl service =
                new ConversationServiceImpl(
                        conversationMapper,
                        messageMapper
                );

        List<String> chunks =
                service.buildAnalysisChunks(8L, 1L, 1000);

        assertTrue(chunks.size() > 1);
        String restored = chunks.stream()
                .map(chunk -> chunk.substring("[USER]\n".length()))
                .reduce("", String::concat);
        assertEquals(original, restored);
        assertFalse(restored.contains("\uFFFD"));
    }
}
