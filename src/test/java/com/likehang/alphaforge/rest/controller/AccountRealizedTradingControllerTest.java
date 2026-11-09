package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.response.AccountRealizedTradingSummaryResponse;
import com.likehang.alphaforge.rest.dto.response.RealizedTradingSummaryResponse;
import com.likehang.alphaforge.service.AccountRealizedTradingQueryService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AccountRealizedTradingControllerTest {

    private final UUID user = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private final AccountRealizedTradingQueryService service = mock(AccountRealizedTradingQueryService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(currentUser.getCurrentUserId()).thenReturn(user);
        mvc = MockMvcBuilders.standaloneSetup(new AccountRealizedTradingController(service, currentUser))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void usesTheSessionIdentityAndDefaultsToAllDatesAndTenContributionsPerPage() throws Exception {
        when(service.getSummary(user, account, null, null, null, PageRequest.of(0, 10))).thenReturn(result());
        mvc.perform(get(path()).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.brokerageAccountId").value(account.toString()))
                .andExpect(jsonPath("$.realizedSummary.sellRecordCount").value(0))
                .andExpect(jsonPath("$.contributions").isEmpty());
        verify(service).getSummary(user, account, null, null, null, PageRequest.of(0, 10));
    }

    @Test
    void parsesDateFiltersAndClampsPagination() throws Exception {
        when(service.getSummary(eq(user), eq(account), any(), any(), any(), any())).thenReturn(result());
        mvc.perform(get(path()).param("from", "2026-10-01").param("to", "2026-10-31")
                        .param("currency", "EUR").param("page", "-1").param("size", "500"))
                .andExpect(status().isOk());
        verify(service).getSummary(user, account, LocalDate.parse("2026-10-01"), LocalDate.parse("2026-10-31"),
                "EUR", PageRequest.of(0, 200));
        mvc.perform(get(path()).param("page", "1001").param("size", "0")).andExpect(status().isOk());
        verify(service).getSummary(user, account, null, null, null, PageRequest.of(1000, 10));
    }

    @Test
    void rejectsMalformedDatesAndPathOrPagingParameters() throws Exception {
        mvc.perform(get(path()).param("from", "not-a-date")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("to", "2026-02-30")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("page", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/brokerage-accounts/not-a-uuid/realized-trading-summary")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returnsNotFoundWhenTheAccountIsUnavailable() throws Exception {
        when(service.getSummary(eq(user), eq(account), any(), any(), any(), any()))
                .thenThrow(new ResourceNotFoundException("Brokerage account not found"));
        mvc.perform(get(path())).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    private String path() {
        return "/api/brokerage-accounts/" + account + "/realized-trading-summary";
    }

    private AccountRealizedTradingSummaryResponse result() {
        return new AccountRealizedTradingSummaryResponse(account, null, null, null,
                new RealizedTradingSummaryResponse(0, 0, 0, 0, 0, List.of()), 0, List.of(), 0, 10, 0, 0, true, true);
    }
}
