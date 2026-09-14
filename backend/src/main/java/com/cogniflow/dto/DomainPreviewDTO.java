package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class DomainPreviewDTO {

    /**
     * 已有领域的数据库 ID。
     *
     * 选择已有领域时有值；
     * AI 建议新建领域时为 null。
     */
    private Long id;

    /**
     * AI 建议新领域时使用的临时 ID。
     *
     * 例如：domain_temp_1
     * 选择已有领域时为 null。
     */
    @JsonProperty("temp_id")
    private String tempId;

    /**
     * 领域名称。
     *
     * 用户可以在 Draft 审核时修改新领域名称。
     */
    private String name;

    /**
     * 领域的简要说明。
     */
    private String description;
}
