package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.query.AccountActivityPageResponse;
import com.likehang.alphaforge.model.dto.query.AccountActivityResponse;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivityQueryServiceTest {

    private final AccountActivityRepository accountActivityRepository = mock(AccountActivityRepository.class);
    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");
    private final UUID activityId = UUID.fromString("40000000-0000-0000-0000-000000000001");

    private AccountActivityQueryService service;

    @BeforeEach
    void setUp() {
        service = new AccountActivityQueryService(
                accountActivityRepository,
                brokerageAccountRepository
        );
    }

    @Test
    void getsActivitiesForBrokerageAccountOwnedByConfiguredUser() {
        Pageable pageable = PageRequest.of(0, 50);
        AppUser appUser = appUser();
        BrokerageAccount brokerageAccount = brokerageAccount(appUser);
        AccountActivity accountActivity = accountActivity(brokerageAccount);

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        when(accountActivityRepository.findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByOccurredAtDesc(
                userId,
                brokerageAccountId,
                pageable
        )).thenReturn(new PageImpl<>(List.of(accountActivity), pageable, 1));

        AccountActivityPageResponse result = service.getActivitiesByBrokerageAccount(
                userId,
                brokerageAccountId,
                pageable
        );

        assertThat(result.brokerageAccountId()).isEqualTo(brokerageAccountId);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.activities()).singleElement()
                .satisfies(activity -> assertActivity(activity));
    }

    @Test
    void rejectsBrokerageAccountThatIsNotOwnedByConfiguredUser() {
        Pageable pageable = PageRequest.of(0, 50);
        AppUser appUser = appUser();

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getActivitiesByBrokerageAccount(userId, brokerageAccountId, pageable))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Brokerage account not found")
                .hasMessageContaining(brokerageAccountId.toString());

        verify(accountActivityRepository, never())
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByOccurredAtDesc(
                        userId,
                        brokerageAccountId,
                        pageable
                );
    }

    private AppUser appUser() {
        AppUser appUser = new AppUser("dev@alphaforge.local", "Local Dev User");
        appUser.setId(userId);
        return appUser;
    }

    private BrokerageAccount brokerageAccount(AppUser appUser) {
        BrokerageAccount brokerageAccount = new BrokerageAccount(
                appUser,
                "Manual CSV Upload",
                "Default Local Account",
                "USD"
        );
        brokerageAccount.setId(brokerageAccountId);
        return brokerageAccount;
    }

    private AccountActivity accountActivity(BrokerageAccount brokerageAccount) {
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "activity.csv");
        importBatch.setId(importBatchId);

        AccountActivity accountActivity = new AccountActivity();
        accountActivity.setId(activityId);
        accountActivity.setBrokerageAccount(brokerageAccount);
        accountActivity.setImportBatch(importBatch);
        accountActivity.setRowNumber(2);
        accountActivity.setExternalTransactionId("TX-1");
        accountActivity.setAction("Limit buy");
        accountActivity.setOccurredAt(OffsetDateTime.parse("2026-07-01T13:35:43Z"));
        accountActivity.setIsin("US67066G1040");
        accountActivity.setTicker("NVDA");
        accountActivity.setInstrumentName("NVIDIA");
        accountActivity.setNumberOfShares(new BigDecimal("2.0"));
        accountActivity.setPricePerShare(new BigDecimal("119.20"));
        accountActivity.setPricePerShareCurrency("USD");
        accountActivity.setExchangeRate(new BigDecimal("1.10"));
        accountActivity.setTotalAmount(new BigDecimal("238.40"));
        accountActivity.setTotalCurrency("USD");
        return accountActivity;
    }

    private void assertActivity(AccountActivityResponse activity) {
        assertThat(activity.id()).isEqualTo(activityId);
        assertThat(activity.brokerageAccountId()).isEqualTo(brokerageAccountId);
        assertThat(activity.importBatchId()).isEqualTo(importBatchId);
        assertThat(activity.rowNumber()).isEqualTo(2);
        assertThat(activity.externalTransactionId()).isEqualTo("TX-1");
        assertThat(activity.action()).isEqualTo("Limit buy");
        assertThat(activity.ticker()).isEqualTo("NVDA");
        assertThat(activity.totalAmount()).isEqualByComparingTo("238.40");
        assertThat(activity.totalCurrency()).isEqualTo("USD");
    }
}
