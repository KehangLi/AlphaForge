package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.model.holding.HoldingStatus;
import com.likehang.alphaforge.rest.dto.response.HoldingPageResponse;
import com.likehang.alphaforge.rest.dto.response.HoldingResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.HoldingQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerageAccountHoldingControllerTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final HoldingQueryService service = mock(HoldingQueryService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(currentUser.getCurrentUserId()).thenReturn(userId);
        mvc = MockMvcBuilders.standaloneSetup(new BrokerageAccountHoldingController(service, currentUser))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void returnsHoldingsForTheSessionUserWithDefaultOpenFilterAndTenRowsPerPage() throws Exception {
        when(service.getHoldingsByBrokerageAccount(userId, accountId, HoldingFilter.OPEN, "", PageRequest.of(0, 10)))
                .thenReturn(result());
        mvc.perform(get(path()).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.brokerageAccountId").value(accountId.toString()))
                .andExpect(jsonPath("$.holdings[0].instrumentKey").value("ISIN:US67066G1040"))
                .andExpect(jsonPath("$.holdings[0].quantity").value(3))
                .andExpect(jsonPath("$.holdings[0].totalBoughtQuantity").value(5))
                .andExpect(jsonPath("$.holdings[0].totalSoldQuantity").value(2))
                .andExpect(jsonPath("$.holdings[0].status").value("OPEN"))
                .andExpect(jsonPath("$.totalElements").value(1));
        verify(service).getHoldingsByBrokerageAccount(userId, accountId, HoldingFilter.OPEN, "", PageRequest.of(0, 10));
    }

    @Test
    void passesFiltersAndClampsPagination() throws Exception {
        when(service.getHoldingsByBrokerageAccount(eq(userId), eq(accountId), any(), anyString(), any())).thenReturn(result());
        mvc.perform(get(path()).param("status", "ALL").param("search", "nvda").param("page", "-1").param("size", "500"))
                .andExpect(status().isOk());
        verify(service).getHoldingsByBrokerageAccount(userId, accountId, HoldingFilter.ALL, "nvda", PageRequest.of(0, 200));
        mvc.perform(get(path()).param("status", "CLOSED").param("page", "1001").param("size", "0"))
                .andExpect(status().isOk());
        verify(service).getHoldingsByBrokerageAccount(userId, accountId, HoldingFilter.CLOSED, "", PageRequest.of(1000, 10));
    }

    @Test
    void rejectsInvalidFiltersPagingAndAccountIds() throws Exception {
        mvc.perform(get(path()).param("status", "INVALID")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("page", "abc")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/brokerage-accounts/not-a-uuid/holdings")).andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void returnsNotFoundForAnAccountNotOwnedByTheCurrentUser() throws Exception {
        when(service.getHoldingsByBrokerageAccount(eq(userId), eq(accountId), any(), anyString(), any()))
                .thenThrow(new ResourceNotFoundException("Brokerage account not found"));
        mvc.perform(get(path())).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void returnsConflictForInvalidImportedHistory() throws Exception {
        when(service.getHoldingsByBrokerageAccount(eq(userId), eq(accountId), any(), anyString(), any()))
                .thenThrow(new ConflictException("Imported history creates negative holdings"));
        mvc.perform(get(path())).andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Imported history creates negative holdings"));
    }

    private String path() {
        return "/api/brokerage-accounts/" + accountId + "/holdings";
    }

    private HoldingPageResponse result() {
        return new HoldingPageResponse(accountId, List.of(new HoldingResponse("ISIN:US67066G1040", "US67066G1040",
                "NVDA", "NVIDIA", new BigDecimal("3"), new BigDecimal("5"), new BigDecimal("2"),
                OffsetDateTime.parse("2026-10-01T10:00:00Z"), HoldingStatus.OPEN)), 0, 10, 1, 1, true, true);
    }
}
