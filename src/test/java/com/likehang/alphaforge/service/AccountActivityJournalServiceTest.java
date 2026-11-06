package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.rest.dto.request.JournalNoteRequest;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AccountActivityJournalServiceTest {

    private final UUID user = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private final UUID activityId = UUID.randomUUID();
    private final AccountActivityRepository activities = mock(AccountActivityRepository.class);
    private final UserWriteLockService locks = mock(UserWriteLockService.class);
    private final AccountActivityJournalService service = new AccountActivityJournalService(activities, locks);

    @Test
    void locksOwnerBeforeLoadingAndUpdatingOnlyTheJournalFields() {
        AccountActivity activity = existing();
        activity.setNotes("Broker's original note");
        activity.setRawRowHash("original hash");
        var response = service.saveJournalNote(user, account, activityId, new JournalNoteRequest("  Entry reason\nExit plan  "));
        assertThat(response.activityId()).isEqualTo(activityId);
        assertThat(response.journalNote()).isEqualTo("Entry reason\nExit plan");
        assertThat(response.journalNoteUpdatedAt()).isNotNull();
        assertThat(activity.getNotes()).isEqualTo("Broker's original note");
        assertThat(activity.getRawRowHash()).isEqualTo("original hash");
        InOrder order = inOrder(locks, activities);
        order.verify(locks).lockUser(user);
        order.verify(activities).findByIdAndBrokerageAccount_User_IdAndBrokerageAccount_Id(activityId, user, account);
        order.verify(activities).save(activity);
    }

    @Test
    void savesIdempotentlyWithoutChangingTheTimestampWhenTextIsUnchanged() {
        AccountActivity activity = existing();
        activity.setJournalNote("Existing note");
        Instant timestamp = Instant.parse("2026-10-01T00:00:00Z");
        activity.setJournalNoteUpdatedAt(timestamp);
        var response = service.saveJournalNote(user, account, activityId, new JournalNoteRequest("Existing note"));
        assertThat(response.journalNoteUpdatedAt()).isEqualTo(timestamp);
        verify(activities, never()).save(any());
    }

    @Test
    void clearsBlankNotesAndRecordsTheEditTime() {
        AccountActivity activity = existing();
        activity.setJournalNote("Old note");
        var response = service.saveJournalNote(user, account, activityId, new JournalNoteRequest(" \n\t"));
        assertThat(response.journalNote()).isNull();
        assertThat(response.journalNoteUpdatedAt()).isNotNull();
        assertThat(activity.getJournalNote()).isNull();
    }

    @Test
    void rejectsForeignOrMissingActivities() {
        assertThatThrownBy(() -> service.saveJournalNote(user, account, activityId, new JournalNoteRequest("Note")))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(activities, never()).save(any());
    }

    @Test
    void validatesIdsAndInputBeforeTakingTheWriteLock() {
        assertThatThrownBy(() -> service.saveJournalNote(null, account, activityId, new JournalNoteRequest("Note")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveJournalNote(user, null, activityId, new JournalNoteRequest("Note")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveJournalNote(user, account, null, new JournalNoteRequest("Note")))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveJournalNote(user, account, activityId, null))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveJournalNote(user, account, activityId, new JournalNoteRequest(null)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.saveJournalNote(user, account, activityId, new JournalNoteRequest("x".repeat(5001))))
                .isInstanceOf(BadRequestException.class);
        verifyNoInteractions(locks, activities);
    }

    private AccountActivity existing() {
        AccountActivity activity = new AccountActivity();
        activity.setId(activityId);
        when(activities.findByIdAndBrokerageAccount_User_IdAndBrokerageAccount_Id(activityId, user, account))
                .thenReturn(Optional.of(activity));
        return activity;
    }
}
