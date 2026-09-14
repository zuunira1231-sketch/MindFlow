package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("conversation")
public class Conversation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String title;

    /**
     * TEXT、MESSAGES、SHARED_LINK、FILE 或 BROWSER_EXTENSION。
     */
    private String sourceType;

    /**
     * chatgpt、claude、gemini 等来源平台。
     */
    private String sourcePlatform;

    /**
     * IMPORTED、ANALYZING、READY 或 FAILED。
     */
    private String status;

    private Integer messageCount;

    /**
     * 规范化对话内容的 SHA-256，用于阻止重复导入。
     */
    private String contentHash;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
