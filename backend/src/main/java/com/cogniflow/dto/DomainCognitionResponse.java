package com.cogniflow.dto;

import com.cogniflow.entity.Domain;
import java.util.List;

public record DomainCognitionResponse(
        Domain domain,
        List<DomainKnowledgeItem> knowledge,
        List<DomainRelationItem> relations
) {
}
