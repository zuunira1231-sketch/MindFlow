package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class KnowledgeResultDTO {

    /**
     * 旧 Knowledge 的数据库 ID。
     *
     * 有值：更新已有 Knowledge。
     * 为 null：新增 Knowledge。
     */
    private Long id;

    /**
     * 本次新增 Knowledge 的临时 ID。
     *
     * 新增 Knowledge 时使用，
     * 供 Relation 和 Evolution 引用。
     */
    @JsonProperty("temp_id")
    private String tempId;

    /**
     * 第二次 AI 整理后的 Knowledge 标题。
     */
    private String title;

    /**
     * 第二次 AI 整理后的 Knowledge 描述。
     */
    private String description;

    /**
     * 本次需要关联的已有 Domain ID。
     */
    @JsonProperty("domain_ids")
    private List<Long> domainIds;

    /**
     * 本次需要关联的、经过用户确认的新 Domain 临时 ID。
     */
    @JsonProperty("domain_temp_ids")
    private List<String> domainTempIds;
}
