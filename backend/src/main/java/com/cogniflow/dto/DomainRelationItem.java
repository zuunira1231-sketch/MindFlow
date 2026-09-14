package com.cogniflow.dto;

import java.util.List;

public record DomainRelationItem(
        Long id,
        String description,
        List<DomainRelationKnowledgeItem> knowledge
) {
}
