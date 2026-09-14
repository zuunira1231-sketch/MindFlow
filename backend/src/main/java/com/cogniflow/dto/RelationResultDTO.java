package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class RelationResultDTO {

    /**
     * 旧关系数据库 ID。
     *
     * 有值：更新已有 Relation。
     * 为空：新增 Relation。
     */
    private Long id;

    /**
     * 关系的具体描述。
     */
    private String description;

    /**
     * 这条关系关联的旧 Knowledge ID。
     */
    @JsonProperty("knowledge_ids")
    private List<Long> knowledgeIds;

    /**
     * 这条关系关联的本次新增 Knowledge 临时 ID。
     */
    @JsonProperty("knowledge_temp_ids")
    private List<String> knowledgeTempIds;
}