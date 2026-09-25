package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.service.BrokerageAccountListService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerageAccountControllerTest {

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private FakeBrokerageAccountListService brokerageAccountListService;
    private FakeCurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        brokerageAccountListService = new FakeBrokerageAccountListService();
        currentUserService = new FakeCurrentUserService(userId);
        BrokerageAccountController controller = new BrokerageAccountController(
                brokerageAccountListService,
                currentUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void listsBrokerageAccountsForCurrentUser() throws Exception {
        brokerageAccountListService.result = List.of(response());

        mockMvc.perform(get("/api/brokerage-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$[0].brokerName").value("Trading 212"))
                .andExpect(jsonPath("$[0].accountName").value("Main Account"))
                .andExpect(jsonPath("$[0].accountNumberMasked").value("****7890"))
                .andExpect(jsonPath("$[0].baseCurrency").value("USD"));

        assertThat(brokerageAccountListService.requestedUserId).isEqualTo(userId);
    }

    @Test
    void returnsEmptyListWhenUserHasNoBrokerageAccounts() throws Exception {
        brokerageAccountListService.result = List.of();

        mockMvc.perform(get("/api/brokerage-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(brokerageAccountListService.requestedUserId).isEqualTo(userId);
    }

    private BrokerageAccountListResponse response() {
        return new BrokerageAccountListResponse(
                brokerageAccountId,
                "Trading 212",
                "Main Account",
                "****7890",
                "USD"
        );
    }

    private static class FakeBrokerageAccountListService extends BrokerageAccountListService {

        private List<BrokerageAccountListResponse> result;
        private UUID requestedUserId;

        private FakeBrokerageAccountListService() {
            super(null);
        }

        @Override
        public List<BrokerageAccountListResponse> getBrokerageAccountList(UUID userId) {
            requestedUserId = userId;
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
