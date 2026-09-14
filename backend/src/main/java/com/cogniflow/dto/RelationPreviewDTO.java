package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class RelationPreviewDTO {

    @NotBlank(message = "Relation 描述不能为空")
    private String description;

    @NotNull(message = "Relation 的 Knowledge 引用不能为空")
    @Size(
            min = 2,
            message = "Relation 至少需要引用两个 Knowledge"
    )
    @JsonProperty("knowledge_temp_ids")
    private List<@NotBlank String> knowledgeTempIds;
}