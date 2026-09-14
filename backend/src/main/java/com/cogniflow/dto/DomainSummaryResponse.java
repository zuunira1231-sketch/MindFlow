package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DomainSummaryResponse {

    /**
     * 领域摘要 AI 返回的所有更新结果。
     */
    @JsonProperty("domain_summaries")
    private List<DomainSummaryResultDTO> domainSummaries;
}