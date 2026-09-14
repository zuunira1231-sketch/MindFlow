package com.cogniflow.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDomainRequest {

    @NotBlank(message = "领域名称不能为空")
    private String name;

    private String description;
}
