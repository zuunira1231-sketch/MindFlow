package com.cogniflow.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PrepareIntegrationPlanRequest {
    @NotBlank
    @Size(max = 100)
    private String planRequestId;
    @Valid
    @NotNull
    private DraftDTO draft;
}
