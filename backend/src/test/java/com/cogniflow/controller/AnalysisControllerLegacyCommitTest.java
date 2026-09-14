package com.cogniflow.controller;

import com.cogniflow.dto.DraftDTO;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AnalysisControllerLegacyCommitTest {
    @Test
    void legacyCommitCannotBypassFinalReview() {
        AnalysisController controller = new AnalysisController(null);
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> controller.commitDraft(new DraftDTO())
        );
        assertEquals(HttpStatus.GONE, exception.getStatusCode());
    }
}
