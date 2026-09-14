package com.cogniflow.dto;

public record DomainRelationKnowledgeItem(
        Long id,
        String title,
        boolean inCurrentDomain
) {
}
