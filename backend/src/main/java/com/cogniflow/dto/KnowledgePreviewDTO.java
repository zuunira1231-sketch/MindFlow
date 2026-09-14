package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgePreviewDTO {

    /**
     * Draft 阶段 Knowledge 的临时 ID。
     *
     * Relation、Evolution 可以通过它引用这条新认知。
     */
    @JsonProperty("temp_id")
    private String tempId;

    private String title;

    private String description;

    /**
     * 该 Knowledge 所属的已有领域 ID。
     */
    @JsonProperty("domain_ids")
    private List<Long> domainIds;

    /**
     * 该 Knowledge 所属的新领域临时 ID。
     */
    @JsonProperty("domain_temp_ids")
    private List<String> domainTempIds;
}
