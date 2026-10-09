package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.request.BrokerageAccountCreateRequest;
import com.likehang.alphaforge.rest.dto.response.BrokerageAccountListResponse;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BrokerageAccountServiceTest {

    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);
    private final AppUserRepository appUserRepository = mock(AppUserRepository.class);
    private final AccountActivityRepository accountActivityRepository = mock(AccountActivityRepository.class);
    private final ImportBatchRepository importBatchRepository = mock(ImportBatchRepository.class);

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    private BrokerageAccountService service;

    @BeforeEach
    void setUp() {
        service = new BrokerageAccountService(
                brokerageAccountRepository,
                appUserRepository,
                accountActivityRepository,
                importBatchRepository
        );
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
    void rejectsMissingUserIdWhenListingAccounts() {
        assertThatThrownBy(() -> service.getBrokerageAccountList(null))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("User id is required");

        verify(brokerageAccountRepository, never()).findByUser_IdOrderByCreatedAtDesc(userId);
    }

    @Test
    void createsBrokerageAccountForUser() {
        AppUser appUser = appUser();
        BrokerageAccountCreateRequest request = new BrokerageAccountCreateRequest(
                " Trading 212 ",
                " Main Account ",
                " ****7890 ",
                " usd "
        );

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser));
        when(brokerageAccountRepository.existsByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                userId,
                "Trading 212",
                "Main Account"
        )).thenReturn(false);
        when(brokerageAccountRepository.save(any(BrokerageAccount.class)))
                .thenAnswer(invocation -> {
                    BrokerageAccount brokerageAccount = invocation.getArgument(0);
                    brokerageAccount.setId(brokerageAccountId);
                    return brokerageAccount;
                });

        BrokerageAccountListResponse result = service.createBrokerageAccount(userId, request);

        assertThat(result.id()).isEqualTo(brokerageAccountId);
        assertThat(result.brokerName()).isEqualTo("Trading 212");
        assertThat(result.accountName()).isEqualTo("Main Account");
        assertThat(result.accountNumberMasked()).isEqualTo("****7890");
        assertThat(result.baseCurrency()).isEqualTo("USD");
    }

    @Test
    void rejectsDuplicateBrokerageAccountForUser() {
        BrokerageAccountCreateRequest request = new BrokerageAccountCreateRequest(
                "Trading 212",
                "Main Account",
                null,
                "USD"
        );

        when(appUserRepository.findById(userId)).thenReturn(Optional.of(appUser()));
        when(brokerageAccountRepository.existsByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                userId,
                "Trading 212",
                "Main Account"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.createBrokerageAccount(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Brokerage account already exists: Trading 212 / Main Account");

        verify(brokerageAccountRepository, never()).save(any(BrokerageAccount.class));
    }

    @Test
    void rejectsInvalidBaseCurrencyWhenCreatingAccount() {
        BrokerageAccountCreateRequest request = new BrokerageAccountCreateRequest(
                "Trading 212",
                "Main Account",
                null,
                "US"
        );

        assertThatThrownBy(() -> service.createBrokerageAccount(userId, request))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Base currency must be a 3-letter code");

        verify(appUserRepository, never()).findById(userId);
        verify(brokerageAccountRepository, never()).save(any(BrokerageAccount.class));
    }

    @Test
    void deletesBrokerageAccountAndItsImportedData() {
        BrokerageAccount brokerageAccount = brokerageAccount();

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        when(accountActivityRepository.deleteByBrokerageAccount_Id(brokerageAccountId)).thenReturn(12L);
        when(importBatchRepository.deleteByBrokerageAccount_Id(brokerageAccountId)).thenReturn(3L);

        service.deleteBrokerageAccount(userId, brokerageAccountId);

        InOrder inOrder = inOrder(accountActivityRepository, importBatchRepository, brokerageAccountRepository);
        inOrder.verify(accountActivityRepository).deleteByBrokerageAccount_Id(brokerageAccountId);
        inOrder.verify(importBatchRepository).deleteByBrokerageAccount_Id(brokerageAccountId);
        inOrder.verify(brokerageAccountRepository).delete(brokerageAccount);
    }

    @Test
    void rejectsBrokerageAccountThatIsNotOwnedByUserWhenDeleting() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteBrokerageAccount(userId, brokerageAccountId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Brokerage account not found: " + brokerageAccountId);

        verify(accountActivityRepository, never()).deleteByBrokerageAccount_Id(brokerageAccountId);
        verify(importBatchRepository, never()).deleteByBrokerageAccount_Id(brokerageAccountId);
        verify(brokerageAccountRepository, never()).delete(any(BrokerageAccount.class));
    }

    private AppUser appUser() {
        AppUser appUser = new AppUser("dev@alphaforge.local", "Local Dev User");
        appUser.setId(userId);
        return appUser;
    }

    private BrokerageAccount brokerageAccount() {
        BrokerageAccount brokerageAccount = new BrokerageAccount(
                appUser(),
                "Trading 212",
                "Main Account",
                "USD"
        );
        brokerageAccount.setId(brokerageAccountId);
        brokerageAccount.setAccountNumberMasked("****7890");
        return brokerageAccount;
    }
}
