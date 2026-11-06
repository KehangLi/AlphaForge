package com.likehang.alphaforge.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record JournalNoteResponse(UUID activityId, String journalNote, Instant journalNoteUpdatedAt) {
}
