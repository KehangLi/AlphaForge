package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.response.InstrumentDetailResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.InstrumentQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerageAccountInstrumentControllerTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final InstrumentQueryService service = mock(InstrumentQueryService.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        when(currentUser.getCurrentUserId()).thenReturn(userId);
        mvc = MockMvcBuilders.standaloneSetup(new BrokerageAccountInstrumentController(service, currentUser))
                .setControllerAdvice(new RestExceptionHandler()).build();
    }

    @Test
    void passesTheSessionIdentityAndPathKeyWithDefaultPagination() throws Exception {
        when(service.getInstrumentByBrokerageAccount(eq(userId), eq(accountId), eq("ISIN:US67066G1040"), any()))
                .thenReturn(result());
        mvc.perform(get(path()).param("userId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.instrumentKey").value("ISIN:US67066G1040"))
                .andExpect(jsonPath("$.ticker").value("NVDA"));
        verify(service).getInstrumentByBrokerageAccount(userId, accountId, "ISIN:US67066G1040", PageRequest.of(0, 10));
    }

    @Test
    void clampsPaginationLikeTheHoldingEndpoint() throws Exception {
        when(service.getInstrumentByBrokerageAccount(eq(userId), eq(accountId), any(), any())).thenReturn(result());
        mvc.perform(get(path()).param("page", "-1").param("size", "500")).andExpect(status().isOk());
        verify(service).getInstrumentByBrokerageAccount(userId, accountId, "ISIN:US67066G1040", PageRequest.of(0, 200));
        mvc.perform(get(path()).param("page", "1001").param("size", "0")).andExpect(status().isOk());
        verify(service).getInstrumentByBrokerageAccount(userId, accountId, "ISIN:US67066G1040", PageRequest.of(1000, 10));
    }

    @Test
    void rejectsMalformedAccountOrPaginationParameters() throws Exception {
        mvc.perform(get("/api/brokerage-accounts/not-a-uuid/instruments/ISIN:US67066G1040")).andExpect(status().isBadRequest());
        mvc.perform(get(path()).param("page", "abc")).andExpect(status().isBadRequest());
    }

    @Test
    void returnsNotFoundForAnUnavailableInstrument() throws Exception {
        when(service.getInstrumentByBrokerageAccount(eq(userId), eq(accountId), any(), any()))
                .thenThrow(new ResourceNotFoundException("Instrument not found in brokerage account"));
        mvc.perform(get(path())).andExpect(status().isNotFound()).andExpect(jsonPath("$.status").value(404));
    }

    private String path() {
        return "/api/brokerage-accounts/" + accountId + "/instruments/ISIN:US67066G1040";
    }

    private InstrumentDetailResponse result() {
        return new InstrumentDetailResponse(accountId, "ISIN:US67066G1040", "US67066G1040", "NVDA", "NVIDIA",
                null, List.of(), 0, 10, 1, 1, true, true);
    }
}
