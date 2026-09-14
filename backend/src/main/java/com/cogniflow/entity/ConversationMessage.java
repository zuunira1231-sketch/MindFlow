package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation_message")
public class ConversationMessage {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long conversationId;

    /**
     * 消息在原对话中的顺序，从 1 开始。
     */
    private Integer sequenceNo;

    /**
     * user、assistant、system 或 tool。
     */
    private String role;

    private String content;

    /**
     * 来源平台能够提供时保存原消息时间。
     */
    private LocalDateTime originalCreatedAt;

    private LocalDateTime createdAt;
}
