package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.projection.InstrumentActivity;
import com.likehang.alphaforge.rest.dto.response.AccountRealizedTradingSummaryResponse;
import com.likehang.alphaforge.service.trading.RealizedTradingCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.time.LocalDate;
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

class AccountRealizedTradingQueryServiceTest {

    private final UUID user = UUID.randomUUID();
    private final UUID account = UUID.randomUUID();
    private final BrokerageAccountRepository accounts = mock(BrokerageAccountRepository.class);
    private final AccountActivityRepository activities = mock(AccountActivityRepository.class);
    private final AccountRealizedTradingQueryService service = new AccountRealizedTradingQueryService(
            accounts, activities, new RealizedTradingCalculator());

    @BeforeEach
    void setUp() {
        when(accounts.findByIdAndUser_Id(account, user)).thenReturn(Optional.of(new BrokerageAccount()));
    }

    @Test
    void retainsTheCompleteOverviewWhilePagingAndFilteringInstrumentCurrencyContributions() {
        when(activities.findRealizedTradingActivities(user, account, null, null)).thenReturn(List.of(
                sale("US67066G1040", "NVDA", "20", "EUR"), sale("US67066G1040", "NVDA", "-5", "EUR"),
                sale("US67066G1040", "NVDA", "100", "USD"), sale("US0378331005", "AAPL", "-3", "EUR")));
        var first = query(null, null, null, 0, 1);
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.realizedSummary().sellRecordCount()).isEqualTo(4);
        assertThat(first.realizedSummary().currencies()).extracting("currency").containsExactly("EUR", "USD");
        assertThat(first.realizedSummary().currencies().getFirst().totalResult()).isEqualByComparingTo("12");
        assertThat(first.contributions()).singleElement().satisfies(row -> {
            assertThat(row.instrumentKey()).isEqualTo("ISIN:US67066G1040");
            assertThat(row.currency()).isEqualTo("EUR");
            assertThat(row.realizedSummary().currencies().getFirst().totalResult()).isEqualByComparingTo("15");
        });
        var second = query(null, null, null, 1, 1);
        assertThat(second.realizedSummary()).isEqualTo(first.realizedSummary());
        assertThat(second.contributions()).singleElement().satisfies(row -> assertThat(row.ticker()).isEqualTo("AAPL"));
        var usd = query(null, null, " usd ", 0, 10);
        assertThat(usd.currency()).isEqualTo("USD");
        assertThat(usd.totalElements()).isEqualTo(1);
        assertThat(usd.realizedSummary()).isEqualTo(first.realizedSummary());
        assertThat(usd.contributions()).singleElement().satisfies(row -> assertThat(row.currency()).isEqualTo("USD"));
        assertThat(query(null, null, "GBP", 0, 10).contributions()).isEmpty();
        assertThat(query(null, null, null, 1000, 10).totalElements()).isEqualTo(3);
        assertThat(query(null, null, null, 1000, 10).contributions()).isEmpty();
    }

    @Test
    void keepsMissingDataAndUnknownInstrumentsVisibleAndOrdersUnavailableResultsLast() {
        when(activities.findRealizedTradingActivities(user, account, null, null)).thenReturn(List.of(
                sale("US67066G1040", "NVDA", null, "EUR"), sale("US0378331005", "AAPL", "0", "EUR"),
                sale(null, null, "3", "EUR"), sale(null, null, null, null), sale(null, "FALLBACK", "4", null)));
        var result = query(null, null, null, 0, 10);
        assertThat(result.realizedSummary().sellRecordCount()).isEqualTo(5);
        assertThat(result.realizedSummary().includedSellRecordCount()).isEqualTo(2);
        assertThat(result.realizedSummary().missingResultCount()).isEqualTo(2);
        assertThat(result.realizedSummary().missingResultCurrencyCount()).isEqualTo(1);
        assertThat(result.unidentifiedSellRecordCount()).isEqualTo(2);
        assertThat(result.contributions()).extracting("currency").containsExactly("EUR", "EUR", "EUR", null, null);
        assertThat(result.contributions().getFirst().instrumentKey()).isNull();
        assertThat(result.contributions().getFirst().instrumentName()).isEqualTo("Unknown instrument");
        assertThat(result.contributions().get(1).ticker()).isEqualTo("AAPL");
        assertThat(result.contributions().get(2).realizedSummary().currencies()).isEmpty();
        assertThat(result.contributions().stream().mapToLong(row -> row.realizedSummary().sellRecordCount()).sum())
                .isEqualTo(result.realizedSummary().sellRecordCount());
    }

    @Test
    void normalizesIdentifiersAndRetainsLatestNonblankSaleMetadata() {
        when(activities.findRealizedTradingActivities(user, account, null, null)).thenReturn(List.of(
                sale(" us67066g1040 ", "OLD", "10", " eur "),
                sale("US67066G1040", "NEW", "2", "EUR"),
                sale("US67066G1040", null, "3", "EUR"),
                sale(null, "NEW", "7", "EUR")));
        var rows = query(null, null, null, 0, 10).contributions();
        assertThat(rows).hasSize(2);
        assertThat(rows.getFirst().instrumentKey()).isEqualTo("ISIN:US67066G1040");
        assertThat(rows.getFirst().ticker()).isEqualTo("NEW");
        assertThat(rows.getFirst().realizedSummary().currencies().getFirst().totalResult()).isEqualByComparingTo("15");
        assertThat(rows.getLast().instrumentKey()).isEqualTo("TICKER:NEW");
    }

    @Test
    void convertsInclusiveDateSelectionsToUtcHalfOpenBounds() {
        LocalDate from = LocalDate.parse("2026-10-01");
        LocalDate to = LocalDate.parse("2026-10-31");
        query(from, to, null, 0, 10);
        verify(activities).findRealizedTradingActivities(user, account,
                OffsetDateTime.parse("2026-10-01T00:00:00Z"), OffsetDateTime.parse("2026-11-01T00:00:00Z"));
        query(from, null, null, 0, 10);
        verify(activities).findRealizedTradingActivities(user, account, OffsetDateTime.parse("2026-10-01T00:00:00Z"), null);
        query(null, to, null, 0, 10);
        verify(activities).findRealizedTradingActivities(user, account, null, OffsetDateTime.parse("2026-11-01T00:00:00Z"));
    }

    @Test
    void returnsAnEmptyAccountWithoutInventingCurrencyTotals() {
        var result = query(null, null, null, 0, 10);
        assertThat(result.realizedSummary().sellRecordCount()).isZero();
        assertThat(result.realizedSummary().currencies()).isEmpty();
        assertThat(result.contributions()).isEmpty();
        assertThat(result.totalElements()).isZero();
        assertThat(result.first()).isTrue();
        assertThat(result.last()).isTrue();
    }

    @Test
    void rejectsMissingOrForeignAccountBeforeReadingFinancialData() {
        when(accounts.findByIdAndUser_Id(account, user)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> query(null, null, null, 0, 10)).isInstanceOf(ResourceNotFoundException.class);
        verify(activities, never()).findRealizedTradingActivities(any(), any(), any(), any());
    }

    @Test
    void rejectsInvalidDatesCurrencyAndRequestInputs() {
        assertThatThrownBy(() -> query(LocalDate.parse("2026-11-01"), LocalDate.parse("2026-10-01"), null, 0, 10))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> query(LocalDate.MIN, null, null, 0, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> query(null, LocalDate.MAX, null, 0, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> query(null, null, "EURO", 0, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> query(null, null, "12$", 0, 10)).isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getSummary(null, account, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getSummary(user, null, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> service.getSummary(user, account, null, null, null, Pageable.unpaged()))
                .isInstanceOf(BadRequestException.class);
        verify(activities, never()).findRealizedTradingActivities(any(), any(), any(), any());
    }

    private AccountRealizedTradingSummaryResponse query(LocalDate from, LocalDate to, String currency, int page, int size) {
        return service.getSummary(user, account, from, to, currency, PageRequest.of(page, size));
    }

    private InstrumentActivity sale(String isin, String ticker, String result, String currency) {
        return new InstrumentActivity("Limit sell", isin, ticker, null, BigDecimal.ONE,
                OffsetDateTime.parse("2026-10-01T10:00:00Z"), 2,
                result == null ? null : new BigDecimal(result), currency);
    }
}
