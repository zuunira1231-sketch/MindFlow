package com.cogniflow.service.impl;

import com.cogniflow.dto.DomainPreviewDTO;
import com.cogniflow.dto.DraftDTO;
import com.cogniflow.dto.EvolutionPreviewDTO;
import com.cogniflow.dto.KnowledgePreviewDTO;
import com.cogniflow.dto.RelationPreviewDTO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DraftReferenceValidationTest {
    @Test
    void deletingKnowledgeRequiresFixingRelationsAndEvolutions() {
        DraftDTO draft = draft();
        draft.setKnowledgePreview(List.of(knowledge("k1", "d1")));
        assertThrows(IllegalArgumentException.class,
                () -> AnalysisServiceImpl.validateDraftReferences(draft));
        draft.setRelationPreview(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> AnalysisServiceImpl.validateDraftReferences(draft));
        draft.setEvolutionPreview(List.of());
        assertDoesNotThrow(() -> AnalysisServiceImpl.validateDraftReferences(draft));
    }

    @Test
    void cancellingNewDomainRequiresFixingKnowledgeMembership() {
        DraftDTO draft = draft();
        draft.setDomainPreview(List.of());
        assertThrows(IllegalArgumentException.class,
                () -> AnalysisServiceImpl.validateDraftReferences(draft));
    }

    private DraftDTO draft() {
        DraftDTO draft = new DraftDTO();
        draft.setRequestId("request-1");
        draft.setKnowledgePreview(List.of(knowledge("k1", "d1"),
                knowledge("k2", "d1")));
        RelationPreviewDTO relation = new RelationPreviewDTO();
        relation.setDescription("related");
        relation.setKnowledgeTempIds(List.of("k1", "k2"));
        draft.setRelationPreview(List.of(relation));
        EvolutionPreviewDTO evolution = new EvolutionPreviewDTO();
        evolution.setTitle("new understanding");
        evolution.setDescription("new understanding");
        evolution.setKnowledgeTempIds(List.of("k1", "k2"));
        draft.setEvolutionPreview(List.of(evolution));
        DomainPreviewDTO domain = new DomainPreviewDTO();
        domain.setTempId("d1");
        domain.setName("Physics");
        draft.setDomainPreview(List.of(domain));
        return draft;
    }

    private KnowledgePreviewDTO knowledge(String temp, String domain) {
        KnowledgePreviewDTO item = new KnowledgePreviewDTO();
        item.setTempId(temp);
        item.setTitle(temp);
        item.setDomainTempIds(List.of(domain));
        return item;
    }
}
