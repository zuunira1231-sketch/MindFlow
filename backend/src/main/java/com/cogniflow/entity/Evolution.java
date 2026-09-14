package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("evolution")
public class Evolution {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private Long sourceDraftId;

    private Integer stepOrder;

    private String eventType;

    private String title;

    private String content;

    private LocalDateTime createdAt;
}