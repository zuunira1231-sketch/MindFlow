package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DomainSummaryResultDTO {

    /**
     * 需要更新摘要的 Domain 真实数据库 ID。
     */
    @JsonProperty("domain_id")
    private Long domainId;

    /**
     * AI 根据该领域当前全部 Knowledge 和 Relation
     * 重新生成的整体认知摘要。
     */
    @JsonProperty("cognitive_summary")
    private String cognitiveSummary;
}
