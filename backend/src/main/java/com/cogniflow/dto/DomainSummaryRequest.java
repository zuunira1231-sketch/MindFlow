package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class DomainSummaryRequest {

    /**
     * 本次需要更新摘要的所有 Domain 上下文。
     */
    @JsonProperty("domain_contexts")
    private List<DomainSummaryContextDTO> domainContexts;
}
