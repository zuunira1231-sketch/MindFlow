package com.cogniflow.dto;

import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommitDraftResponse {

    /**
     * 保存后的 Draft 数据库 ID。
     */
    private Long draftId;

    /**
     * Draft 当前状态。
     */
    private String status;

    /**
     * 第二次 AI 生成并已执行的认知更新结果。
     */
    private CognitiveUpdateDTO cognitiveUpdate;
}
