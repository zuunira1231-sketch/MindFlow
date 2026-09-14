package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class EvolutionResultDTO {

    /**
     * 演化标题。
     */
    private String title;

    /**
     * 演化的具体内容。
     */
    private String content;

    /**
     * 演化类型，例如：
     * NEW、EXPANDED、REVISED、CORRECTED。
     */
    @JsonProperty("event_type")
    private String eventType;

    /**
     * 本次演化涉及的旧 Knowledge ID。
     */
    @JsonProperty("knowledge_ids")
    private List<Long> knowledgeIds;

    /**
     * 本次演化涉及的新增 Knowledge 临时 ID。
     */
    @JsonProperty("knowledge_temp_ids")
    private List<String> knowledgeTempIds;
}
