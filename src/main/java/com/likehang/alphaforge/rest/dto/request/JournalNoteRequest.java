package com.likehang.alphaforge.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record JournalNoteRequest(
        @NotNull(message = "Journal note is required; use an empty string to clear it")
        @Size(max = 5000, message = "Journal note must be at most 5000 characters")
        String journalNote
) {
}
