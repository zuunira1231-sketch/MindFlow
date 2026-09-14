package com.cogniflow.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CommitDraftResponseJsonTest {
    @Test
    void savedResultCanBeReturnedOnRepeatedConfirmation() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        CommitDraftResponse original = new CommitDraftResponse(
                11L, "completed", new CognitiveUpdateDTO());
        CommitDraftResponse restored = mapper.readValue(
                mapper.writeValueAsString(original), CommitDraftResponse.class);
        assertEquals(11L, restored.getDraftId());
        assertEquals("completed", restored.getStatus());
    }
}
