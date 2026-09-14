ALTER TABLE draft
    ADD COLUMN conversation_id BIGINT NULL AFTER user_id,
    ADD KEY idx_draft_conversation_id (conversation_id);
