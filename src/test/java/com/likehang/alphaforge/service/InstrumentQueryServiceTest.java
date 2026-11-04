package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.projection.HoldingActivity;
import com.likehang.alphaforge.service.holding.HoldingCalculator;
import com.likehang.alphaforge.service.mapper.AccountActivityResponseMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentQueryServiceTest {

    private static final String KEY = "ISIN:US67066G1040";
    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final BrokerageAccountRepository accounts = mock(BrokerageAccountRepository.class);
    private final AccountActivityRepository activities = mock(AccountActivityRepository.class);
    private final InstrumentQueryService service = new InstrumentQueryService(
            accounts, activities, new HoldingCalculator(), new AccountActivityResponseMapper());

    @BeforeEach
    void setUp() {
        when(accounts.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(new BrokerageAccount()));
    }

    @Test
    void normalizesTheKeyAndCalculatesTheSummaryFromAllHistoryDespiteAnEmptyRequestedPage() {
        when(activities.findInstrumentHistory(userId, accountId, KEY)).thenReturn(List.of(
                history("Limit buy", "5", 1), history("Limit sell", "2", 2), history("Dividend (Dividend)", "50", 3)));
        PageRequest pageable = PageRequest.of(5, 1);
        when(activities.findInstrumentActivities(userId, accountId, KEY, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 3));
        var result = service.getInstrumentByBrokerageAccount(userId, accountId, " isin:us67066g1040 ", pageable);
        assertThat(result.instrumentKey()).isEqualTo(KEY);
        assertThat(result.holding().quantity()).isEqualByComparingTo("3");
        assertThat(result.holding().totalBoughtQuantity()).isEqualByComparingTo("5");
        assertThat(result.holding().lastTradedAt()).isEqualTo(history("Limit sell", "2", 2).occurredAt());
        assertThat(result.activities()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.page()).isEqualTo(5);
    }

    @Test
    void mapsThePageToTheExistingActivityContract() {
        when(activities.findInstrumentHistory(userId, accountId, KEY)).thenReturn(List.of(history("Limit buy", "5", 1)));
        BrokerageAccount account = new BrokerageAccount();
        account.setId(accountId);
        ImportBatch batch = new ImportBatch(account, "test.csv");
        batch.setId(UUID.randomUUID());
        AccountActivity activity = new AccountActivity();
        activity.setId(UUID.randomUUID());
        activity.setBrokerageAccount(account);
        activity.setImportBatch(batch);
        activity.setAction("Limit buy");
        activity.setNumberOfShares(new BigDecimal("5"));
        activity.setResultAmount(new BigDecimal("12.50"));
        activity.setResultCurrency("EUR");
        activity.setNotes("Original export note");
        PageRequest pageable = PageRequest.of(0, 10);
        when(activities.findInstrumentActivities(userId, accountId, KEY, pageable))
                .thenReturn(new PageImpl<>(List.of(activity), pageable, 1));
        var result = service.getInstrumentByBrokerageAccount(userId, accountId, KEY, pageable);
        assertThat(result.activities()).singleElement().satisfies(row -> {
            assertThat(row.id()).isEqualTo(activity.getId());
            assertThat(row.importBatchId()).isEqualTo(batch.getId());
            assertThat(row.brokerageAccountId()).isEqualTo(accountId);
            assertThat(row.resultAmount()).isEqualByComparingTo("12.50");
            assertThat(row.notes()).isEqualTo("Original export note");
        });
    }

    @Test
    void returnsInstrumentMetadataButNoInventedHoldingForDividendOnlyHistory() {
        when(activities.findInstrumentHistory(userId, accountId, KEY)).thenReturn(List.of(history("Dividend (Dividend)", "5", 1)));
        PageRequest pageable = PageRequest.of(0, 10);
        when(activities.findInstrumentActivities(userId, accountId, KEY, pageable))
                .thenReturn(new PageImpl<>(List.of(), pageable, 1));
        var result = service.getInstrumentByBrokerageAccount(userId, accountId, KEY, pageable);
        assertThat(result.instrumentName()).isEqualTo("NVIDIA");
        assertThat(result.ticker()).isEqualTo("NVDA");
        assertThat(result.holding()).isNull();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "NVDA", "NAME:NVIDIA", "ISIN:", "TICKER:  ", "ISIN:1234567890123", "TICKER:12345678901234567890123456789012345678901"})
    void rejectsMalformedOrOversizedInstrumentKeys(String key) {
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, accountId, key, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        verify(activities, never()).findInstrumentHistory(any(), any(), any());
    }

    @Test
    void checksOwnershipBeforeLookingUpInstrumentHistory() {
        when(accounts.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, accountId, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Brokerage account");
        verify(activities, never()).findInstrumentHistory(any(), any(), any());
    }

    @Test
    void distinguishesAnUnknownInstrumentFromAnEmptyPage() {
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, accountId, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class).hasMessageContaining("Instrument not found");
        verify(activities, never()).findInstrumentActivities(any(), any(), any(), any());
    }

    @Test
    void rejectsInconsistentHistoryAndMissingRequestInputs() {
        when(activities.findInstrumentHistory(userId, accountId, KEY)).thenReturn(List.of(history("Limit sell", "1", 1)));
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, accountId, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(ConflictException.class);
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(null, accountId, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, null, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getInstrumentByBrokerageAccount(userId, accountId, KEY, Pageable.unpaged()))
                .isInstanceOf(BadRequestException.class);
    }

    private HoldingActivity history(String action, String shares, int day) {
        return new HoldingActivity(action, "US67066G1040", "NVDA", "NVIDIA", new BigDecimal(shares),
                OffsetDateTime.parse("2026-10-%02dT10:00:00Z".formatted(day)), day + 1);
    }
}
