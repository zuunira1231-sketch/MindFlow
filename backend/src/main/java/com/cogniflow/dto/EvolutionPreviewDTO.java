package com.cogniflow.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class EvolutionPreviewDTO {

    @NotBlank(message = "Evolution 标题不能为空")
    private String title;

    @NotBlank(message = "Evolution 描述不能为空")
    private String description;

    @NotNull(message = "Evolution 的 Knowledge 引用不能为空")
    @Size(
            min = 1,
            message = "Evolution 至少需要引用一个 Knowledge"
    )
    @JsonProperty("knowledge_temp_ids")
    private List<@NotBlank String> knowledgeTempIds;
}
