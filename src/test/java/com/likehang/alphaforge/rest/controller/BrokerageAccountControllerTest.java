package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.command.BrokerageAccountCreateRequest;
import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.service.BadRequestException;
import com.likehang.alphaforge.service.BrokerageAccountService;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.ResourceNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BrokerageAccountControllerTest {

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private FakeBrokerageAccountService brokerageAccountService;
    private FakeCurrentUserService currentUserService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        brokerageAccountService = new FakeBrokerageAccountService();
        currentUserService = new FakeCurrentUserService(userId);
        BrokerageAccountController controller = new BrokerageAccountController(
                brokerageAccountService,
                currentUserService
        );
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new RestExceptionHandler())
                .build();
    }

    @Test
    void listsBrokerageAccountsForCurrentUser() throws Exception {
        brokerageAccountService.listResult = List.of(response());

        mockMvc.perform(get("/api/brokerage-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$[0].brokerName").value("Trading 212"))
                .andExpect(jsonPath("$[0].accountName").value("Main Account"))
                .andExpect(jsonPath("$[0].accountNumberMasked").value("****7890"))
                .andExpect(jsonPath("$[0].baseCurrency").value("USD"));

        assertThat(brokerageAccountService.requestedListUserId).isEqualTo(userId);
    }

    @Test
    void returnsEmptyListWhenUserHasNoBrokerageAccounts() throws Exception {
        brokerageAccountService.listResult = List.of();

        mockMvc.perform(get("/api/brokerage-accounts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());

        assertThat(brokerageAccountService.requestedListUserId).isEqualTo(userId);
    }

    @Test
    void createsBrokerageAccountForCurrentUser() throws Exception {
        brokerageAccountService.createResult = response();

        mockMvc.perform(post("/api/brokerage-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brokerName": "Trading 212",
                                  "accountName": "Main Account",
                                  "accountNumberMasked": "****7890",
                                  "baseCurrency": "USD"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(brokerageAccountId.toString()))
                .andExpect(jsonPath("$.brokerName").value("Trading 212"))
                .andExpect(jsonPath("$.accountName").value("Main Account"))
                .andExpect(jsonPath("$.accountNumberMasked").value("****7890"))
                .andExpect(jsonPath("$.baseCurrency").value("USD"));

        assertThat(brokerageAccountService.requestedCreateUserId).isEqualTo(userId);
        assertThat(brokerageAccountService.requestedCreateRequest.brokerName()).isEqualTo("Trading 212");
        assertThat(brokerageAccountService.requestedCreateRequest.accountName()).isEqualTo("Main Account");
        assertThat(brokerageAccountService.requestedCreateRequest.accountNumberMasked()).isEqualTo("****7890");
        assertThat(brokerageAccountService.requestedCreateRequest.baseCurrency()).isEqualTo("USD");
    }

    @Test
    void returnsBadRequestWhenCreateRequestIsInvalid() throws Exception {
        brokerageAccountService.createException = new BadRequestException("Base currency is required");

        mockMvc.perform(post("/api/brokerage-accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "brokerName": "Trading 212",
                                  "accountName": "Main Account"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Base currency is required"));
    }

    @Test
    void deletesBrokerageAccount() throws Exception {
        mockMvc.perform(delete("/api/brokerage-accounts/{brokerageAccountId}", brokerageAccountId))
                .andExpect(status().isNoContent());

        assertThat(brokerageAccountService.deleteCalled).isTrue();
        assertThat(brokerageAccountService.requestedDeleteUserId).isEqualTo(userId);
        assertThat(brokerageAccountService.requestedDeleteBrokerageAccountId).isEqualTo(brokerageAccountId);
    }

    @Test
    void returnsNotFoundWhenDeletingMissingBrokerageAccount() throws Exception {
        brokerageAccountService.deleteException = new ResourceNotFoundException(
                "Brokerage account not found: " + brokerageAccountId
        );

        mockMvc.perform(delete("/api/brokerage-accounts/{brokerageAccountId}", brokerageAccountId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Brokerage account not found: " + brokerageAccountId));
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

    private static class FakeBrokerageAccountService extends BrokerageAccountService {

        private List<BrokerageAccountListResponse> listResult;
        private BrokerageAccountListResponse createResult;
        private BadRequestException createException;
        private ResourceNotFoundException deleteException;
        private UUID requestedListUserId;
        private UUID requestedCreateUserId;
        private BrokerageAccountCreateRequest requestedCreateRequest;
        private boolean deleteCalled;
        private UUID requestedDeleteUserId;
        private UUID requestedDeleteBrokerageAccountId;

        private FakeBrokerageAccountService() {
            super(null, null, null, null);
        }

        @Override
        public List<BrokerageAccountListResponse> getBrokerageAccountList(UUID userId) {
            requestedListUserId = userId;
            return listResult;
        }

        @Override
        public BrokerageAccountListResponse createBrokerageAccount(
                UUID userId,
                BrokerageAccountCreateRequest request
        ) {
            requestedCreateUserId = userId;
            requestedCreateRequest = request;

            if (createException != null) {
                throw createException;
            }

            return createResult;
        }

        @Override
        public void deleteBrokerageAccount(UUID userId, UUID brokerageAccountId) {
            deleteCalled = true;
            requestedDeleteUserId = userId;
            requestedDeleteBrokerageAccountId = brokerageAccountId;

            if (deleteException != null) {
                throw deleteException;
            }
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
