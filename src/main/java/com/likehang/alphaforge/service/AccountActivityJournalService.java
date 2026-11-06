package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.rest.dto.request.JournalNoteRequest;
import com.likehang.alphaforge.rest.dto.response.JournalNoteResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Service
public class AccountActivityJournalService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivityJournalService.class);
    private static final int MAX_NOTE_LENGTH = 5000;

    private final AccountActivityRepository activities;
    private final UserWriteLockService userWriteLockService;

    public AccountActivityJournalService(AccountActivityRepository activities, UserWriteLockService userWriteLockService) {
        this.activities = activities;
        this.userWriteLockService = userWriteLockService;
    }

    @Transactional
    public JournalNoteResponse saveJournalNote(UUID userId, UUID brokerageAccountId, UUID activityId, JournalNoteRequest request) {
        if (userId == null || brokerageAccountId == null || activityId == null) {
            throw new BadRequestException("User, brokerage account, and activity ids are required");
        }
        if (request == null || request.journalNote() == null) {
            throw new BadRequestException("Journal note is required; use an empty string to clear it");
        }
        if (request.journalNote().length() > MAX_NOTE_LENGTH) {
            throw new BadRequestException("Journal note must be at most " + MAX_NOTE_LENGTH + " characters");
        }
        String note = request.journalNote().isBlank() ? null : request.journalNote().strip();
        userWriteLockService.lockUser(userId);
        AccountActivity activity = activities.findByIdAndBrokerageAccount_User_IdAndBrokerageAccount_Id(
                        activityId, userId, brokerageAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Account activity not found: " + activityId));
        if (!Objects.equals(note, activity.getJournalNote())) {
            activity.setJournalNote(note);
            activity.setJournalNoteUpdatedAt(Instant.now().truncatedTo(ChronoUnit.MICROS));
            activities.save(activity);
            log.info("Updated activity journal note: user={}, account={}, activity={}", userId, brokerageAccountId, activityId);
        }
        return new JournalNoteResponse(activity.getId(), activity.getJournalNote(), activity.getJournalNoteUpdatedAt());
    }
}
