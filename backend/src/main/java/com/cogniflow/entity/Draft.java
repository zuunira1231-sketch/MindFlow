package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("draft")
public class Draft {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    /**
     * 本 Draft 来源的完整对话。
     */
    private Long conversationId;

    private String requestId;

    private String title;

    private String sourceContent;

    private String summary;

    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
