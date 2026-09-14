package com.cogniflow.dto;

import java.time.LocalDateTime;
import java.util.List;

/** GET /domains：保留原 Domain 字段，并补充首页知识标签。 */
public record DomainListItemResponse(
        Long id,
        Long userId,
        String name,
        String description,
        String cognitiveSummary,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<DomainKnowledgeBrief> recentKnowledge
) {
}
