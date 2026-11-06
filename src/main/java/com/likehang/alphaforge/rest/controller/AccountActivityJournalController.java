package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.request.JournalNoteRequest;
import com.likehang.alphaforge.rest.dto.response.JournalNoteResponse;
import com.likehang.alphaforge.service.AccountActivityJournalService;
import com.likehang.alphaforge.service.CurrentUserService;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/account-activities/{activityId}/journal-note")
public class AccountActivityJournalController {

    private final AccountActivityJournalService journalService;
    private final CurrentUserService currentUserService;

    public AccountActivityJournalController(AccountActivityJournalService journalService, CurrentUserService currentUserService) {
        this.journalService = journalService;
        this.currentUserService = currentUserService;
    }

    @PutMapping
    public ResponseEntity<JournalNoteResponse> saveJournalNote(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @PathVariable("activityId") UUID activityId,
            @Valid @RequestBody JournalNoteRequest request
    ) {
        JournalNoteResponse result = journalService.saveJournalNote(
                currentUserService.getCurrentUserId(), brokerageAccountId, activityId, request);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
