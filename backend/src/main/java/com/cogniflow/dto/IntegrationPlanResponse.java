package com.cogniflow.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
public class IntegrationPlanResponse {
    private Long planId;
    private String status;
    private LocalDateTime expiresAt;
    private Map<String, Object> changes;
}
