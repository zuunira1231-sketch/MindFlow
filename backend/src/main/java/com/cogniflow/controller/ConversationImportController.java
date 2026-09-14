package com.cogniflow.controller;

import com.cogniflow.config.CurrentUser;
import com.cogniflow.dto.ConversationImportResponse;
import com.cogniflow.dto.ImportMessagesConversationRequest;
import com.cogniflow.dto.ImportTextConversationRequest;
import com.cogniflow.entity.Conversation;
import com.cogniflow.service.ConversationService;
import com.cogniflow.service.SharedConversationPdfParser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/conversation-imports")
@RequiredArgsConstructor
public class ConversationImportController {

    private final ConversationService conversationService;
    private final SharedConversationPdfParser sharedConversationPdfParser;

    /**
     * 显式导入用户粘贴的文本对话。
     */
    @PostMapping("/text")
    public ConversationImportResponse importText(
            @Valid @RequestBody ImportTextConversationRequest request
    ) {
        Long userId = CurrentUser.id();

        Conversation conversation = conversationService.importText(
                userId,
                request.getTitle(),
                request.getSourcePlatform(),
                request.getContent()
        );

        return new ConversationImportResponse(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getMessageCount()
        );
    }

    /**
     * 导入已经解析为 user/assistant 消息列表的完整对话。
     */
    @PostMapping("/messages")
    public ConversationImportResponse importMessages(
            @Valid @RequestBody ImportMessagesConversationRequest request
    ) {
        Long userId = CurrentUser.id();

        Conversation conversation = conversationService.importMessages(
                userId,
                request.getTitle(),
                request.getSourcePlatform(),
                request.getMessages()
        );

        return new ConversationImportResponse(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getMessageCount()
        );
    }

    /** 导入从 ChatGPT 分享页“打印为 PDF”得到的完整对话。 */
    @PostMapping(value = "/pdf", consumes = "multipart/form-data")
    public ConversationImportResponse importPdf(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title
    ) throws IOException {
        if (file.isEmpty()) {
            throw new IllegalArgumentException("PDF 文件不能为空");
        }
        Conversation conversation = conversationService.importMessages(
                CurrentUser.id(),
                title,
                "chatgpt-pdf",
                sharedConversationPdfParser.parse(file.getBytes())
        );
        return new ConversationImportResponse(
                conversation.getId(),
                conversation.getStatus(),
                conversation.getMessageCount()
        );
    }
}
