ALTER TABLE account_activity
    ADD COLUMN journal_note text,
    ADD COLUMN journal_note_updated_at timestamp(6) with time zone,
    ADD CONSTRAINT chk_account_activity_journal_note_length
        CHECK (journal_note IS NULL OR char_length(journal_note) <= 5000);
