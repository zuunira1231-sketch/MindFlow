package com.cogniflow.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("integration_plan")
public class IntegrationPlan {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long draftId;
    private String draftRequestId;
    private String planRequestId;
    private String status;
    private Long baselineRevision;
    private String confirmedDraftJson;
    private String cognitiveUpdateJson;
    private String previewJson;
    private String summaryJson;
    private String resultJson;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
