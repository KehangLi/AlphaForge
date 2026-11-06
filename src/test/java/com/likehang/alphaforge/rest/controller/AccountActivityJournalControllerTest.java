package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.request.JournalNoteRequest;
import com.likehang.alphaforge.rest.dto.response.JournalNoteResponse;
import com.likehang.alphaforge.service.AccountActivityJournalService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountActivityJournalControllerTest {

    private final UUID user = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private final UUID activity = UUID.randomUUID();
    private final AccountActivityJournalService service = mock(AccountActivityJournalService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(currentUser.getCurrentUserId()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new AccountActivityJournalController(service, currentUser))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void savesTheNoteUsingSessionIdentityAndReturnsNoStoreResponse() throws Exception {
        when(service.saveJournalNote(eq(user), eq(account), eq(activity), any()))
                .thenReturn(new JournalNoteResponse(activity, "Review", Instant.parse("2026-11-05T10:00:00Z")));
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"journalNote\":\"Review\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.activityId").value(activity.toString()))
                .andExpect(jsonPath("$.journalNote").value("Review"));
        verify(service).saveJournalNote(user, account, activity, new JournalNoteRequest("Review"));
    }

    @Test
    void acceptsAnEmptyStringToClearTheNote() throws Exception {
        when(service.saveJournalNote(eq(user), eq(account), eq(activity), any()))
                .thenReturn(new JournalNoteResponse(activity, null, null));
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"journalNote\":\"\"}"))
                .andExpect(status().isOk());
        verify(service).saveJournalNote(user, account, activity, new JournalNoteRequest(""));
    }

    @Test
    void rejectsMissingNullOversizedAndInvalidJsonBeforeCallingTheService() throws Exception {
        for (String body : new String[]{"{}", "{\"journalNote\":null}", "{\"journalNote\":\"" + "x".repeat(5001) + "\"}", "bad-json"}) {
            mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(service);
    }

    @Test
    void returns404ForAnotherUsersActivity() throws Exception {
        when(service.saveJournalNote(eq(user), eq(account), eq(activity), any()))
                .thenThrow(new ResourceNotFoundException("Account activity not found"));
        mvc.perform(put(path()).contentType(MediaType.APPLICATION_JSON).content("{\"journalNote\":\"Review\"}"))
                .andExpect(status().isNotFound());
    }

    private String path() {
        return "/api/brokerage-accounts/" + account + "/account-activities/" + activity + "/journal-note";
    }
}
