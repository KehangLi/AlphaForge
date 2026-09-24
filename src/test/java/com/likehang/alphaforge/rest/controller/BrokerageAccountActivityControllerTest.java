package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.query.AccountActivityPageResponse;
import com.likehang.alphaforge.model.dto.query.AccountActivityResponse;
import com.likehang.alphaforge.service.AccountActivityQueryService;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerageAccountActivityControllerTest {

    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private final UUID activityId = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private FakeAccountActivityQueryService accountActivityQueryService;
    private FakeCurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        accountActivityQueryService = new FakeAccountActivityQueryService();
        currentUserService = new FakeCurrentUserService(userId);
        BrokerageAccountActivityController controller = new BrokerageAccountActivityController(
                accountActivityQueryService,
                currentUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void listsActivitiesForBrokerageAccount() throws Exception {
        accountActivityQueryService.result = result();

        mockMvc.perform(get("/api/brokerage-accounts/{brokerageAccountId}/account-activities", brokerageAccountId)
                        .param("page", "0")
                        .param("size", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.activities[0].id").value(activityId.toString()))
                .andExpect(jsonPath("$.activities[0].brokerageAccountId").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.activities[0].importBatchId").value(importBatchId.toString()))
                .andExpect(jsonPath("$.activities[0].rowNumber").value(2))
                .andExpect(jsonPath("$.activities[0].externalTransactionId").value("TX-1"))
                .andExpect(jsonPath("$.activities[0].action").value("Limit buy"))
                .andExpect(jsonPath("$.activities[0].ticker").value("NVDA"))
                .andExpect(jsonPath("$.activities[0].totalAmount").value(238.4))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.first").value(true))
                .andExpect(jsonPath("$.last").value(true));

        assertThat(accountActivityQueryService.requestedBrokerageAccountId).isEqualTo(brokerageAccountId);
        assertThat(accountActivityQueryService.requestedUserId).isEqualTo(userId);
        assertThat(accountActivityQueryService.requestedPageable.getPageNumber()).isZero();
        assertThat(accountActivityQueryService.requestedPageable.getPageSize()).isEqualTo(25);
    }

    @Test
    void normalizesPagingInput() throws Exception {
        accountActivityQueryService.result = result();

        mockMvc.perform(get("/api/brokerage-accounts/{brokerageAccountId}/account-activities", brokerageAccountId)
                        .param("page", "-1")
                        .param("size", "500"))
                .andExpect(status().isOk());

        assertThat(accountActivityQueryService.requestedPageable.getPageNumber()).isZero();
        assertThat(accountActivityQueryService.requestedPageable.getPageSize()).isEqualTo(200);
    }

    @Test
    void returnsNotFoundWhenBrokerageAccountDoesNotExist() throws Exception {
        accountActivityQueryService.exception = new ResourceNotFoundException(
                "Brokerage account not found: " + brokerageAccountId
        );

        mockMvc.perform(get("/api/brokerage-accounts/{brokerageAccountId}/account-activities", brokerageAccountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Brokerage account not found: " + brokerageAccountId));
    }

    private AccountActivityPageResponse result() {
        return new AccountActivityPageResponse(
                brokerageAccountId,
                List.of(new AccountActivityResponse(
                        activityId,
                        brokerageAccountId,
                        importBatchId,
                        2,
                        "TX-1",
                        "Limit buy",
                        OffsetDateTime.parse("2026-07-01T13:35:43Z"),
                        "US67066G1040",
                        "NVDA",
                        "NVIDIA",
                        null,
                        new BigDecimal("2.0"),
                        new BigDecimal("119.20"),
                        "USD",
                        new BigDecimal("1.10"),
                        null,
                        null,
                        new BigDecimal("238.40"),
                        "USD",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )),
                0,
                25,
                1,
                1,
                true,
                true
        );
    }

    private static class FakeAccountActivityQueryService extends AccountActivityQueryService {

        private AccountActivityPageResponse result;
        private ResourceNotFoundException exception;
        private UUID requestedUserId;
        private UUID requestedBrokerageAccountId;
        private Pageable requestedPageable;

        private FakeAccountActivityQueryService() {
            super(null, null);
        }

        @Override
        public AccountActivityPageResponse getActivitiesByBrokerageAccount(
                UUID userId,
                UUID brokerageAccountId,
                Pageable pageable
        ) {
            requestedUserId = userId;
            requestedBrokerageAccountId = brokerageAccountId;
            requestedPageable = pageable;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }

    private static class FakeCurrentUserService extends CurrentUserService {

        private final UUID userId;

        private FakeCurrentUserService(UUID userId) {
            super(null, "");
            this.userId = userId;
        }

        @Override
        public UUID getCurrentUserId() {
            return userId;
        }
    }
}
