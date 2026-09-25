package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerageAccountListServiceTest {

    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private BrokerageAccountListService service;

    @BeforeEach
    void setUp() {
        service = new BrokerageAccountListService(brokerageAccountRepository);
    }

    @Test
    void getsBrokerageAccountsForUser() {
        when(brokerageAccountRepository.findByUser_IdOrderByCreatedAtDesc(userId))
                .thenReturn(List.of(brokerageAccount()));

        List<BrokerageAccountListResponse> result = service.getBrokerageAccountList(userId);

        assertThat(result).singleElement()
                .satisfies(account -> {
                    assertThat(account.id()).isEqualTo(brokerageAccountId);
                    assertThat(account.brokerName()).isEqualTo("Trading 212");
                    assertThat(account.accountName()).isEqualTo("Main Account");
                    assertThat(account.accountNumberMasked()).isEqualTo("****7890");
                    assertThat(account.baseCurrency()).isEqualTo("USD");
                });
    }

    @Test
    void rejectsMissingUserId() {
        assertThatThrownBy(() -> service.getBrokerageAccountList(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User id is required");

        verify(brokerageAccountRepository, never()).findByUser_IdOrderByCreatedAtDesc(userId);
    }

    private BrokerageAccount brokerageAccount() {
        AppUser appUser = new AppUser("dev@alphaforge.local", "Local Dev User");
        appUser.setId(userId);

        BrokerageAccount brokerageAccount = new BrokerageAccount(
                appUser,
                "Trading 212",
                "Main Account",
                "USD"
        );
        brokerageAccount.setId(brokerageAccountId);
        brokerageAccount.setAccountNumberMasked("****7890");
        return brokerageAccount;
    }
}
