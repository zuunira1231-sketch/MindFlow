package com.cogniflow.dto;

import java.time.LocalDateTime;
import java.util.List;

public record DomainKnowledgeItem(
        Long id,
        String title,
        String description,
        List<Long> domainIds,
        LocalDateTime updatedAt
) {
}
