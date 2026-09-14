package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("knowledge_relation")
public class KnowledgeRelation {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceDraftId;

    private String description;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
