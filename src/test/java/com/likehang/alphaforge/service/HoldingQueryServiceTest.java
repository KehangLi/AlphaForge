package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.model.holding.HoldingStatus;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.projection.HoldingActivity;
import com.likehang.alphaforge.rest.dto.response.HoldingPageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

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

class HoldingQueryServiceTest {

    private final UUID userId = UUID.randomUUID();
    private final UUID accountId = UUID.randomUUID();
    private final BrokerageAccountRepository accounts = mock(BrokerageAccountRepository.class);
    private final AccountActivityRepository activities = mock(AccountActivityRepository.class);
    private final HoldingQueryService service = new HoldingQueryService(accounts, activities);

    @BeforeEach
    void setUp() {
        when(accounts.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.of(new BrokerageAccount()));
    }

    @Test
    void aggregatesFractionalBuysAndSellsWithoutCountingDividendsOrCash() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", "US67066G1040", "NVDA", "2.5", 1),
                activity("Market buy", "US67066G1040", "NVDA", "0.125", 2),
                activity("Stop sell", "US67066G1040", "NVDA", "-1.25", 3),
                activity("Dividend (Dividend)", "US67066G1040", "NVDA", "50", 4),
                activity("Interest on cash", null, null, null, 5),
                activity("Card debit", null, null, null, 6),
                activity("Limit buy", "US67066G1040", "NVDA", "0", 7)
        ));

        HoldingPageResponse result = query(HoldingFilter.OPEN, "", 0, 10);

        assertThat(result.brokerageAccountId()).isEqualTo(accountId);
        assertThat(result.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.instrumentKey()).isEqualTo("ISIN:US67066G1040");
            assertThat(holding.quantity()).isEqualByComparingTo("1.375");
            assertThat(holding.totalBoughtQuantity()).isEqualByComparingTo("2.625");
            assertThat(holding.totalSoldQuantity()).isEqualByComparingTo("1.25");
            assertThat(holding.lastTradedAt()).isEqualTo(OffsetDateTime.parse("2026-10-03T10:00:00Z"));
            assertThat(holding.status()).isEqualTo(HoldingStatus.OPEN);
        });
    }

    @Test
    void retainsClosedPositionsAndAccumulatesAllTradingCycles() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", "US67066G1040", "NVDA", "2", 1),
                activity("Limit sell", "US67066G1040", "NVDA", "2", 2),
                activity("Limit buy", "US67066G1040", "NVDA", "3", 3)
        ));
        assertThat(query(HoldingFilter.CLOSED, "", 0, 10).holdings()).isEmpty();
        assertThat(query(HoldingFilter.OPEN, "", 0, 10).holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("3");
            assertThat(holding.totalBoughtQuantity()).isEqualByComparingTo("5");
            assertThat(holding.totalSoldQuantity()).isEqualByComparingTo("2");
        });
    }

    @Test
    void filtersSearchAndStatusBeforePaginatingAggregatedInstruments() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", "US67066G1040", "NVDA", "2", 1),
                activity("Limit buy", "US0378331005", "AAPL", "1", 2),
                activity("Limit sell", "US0378331005", "AAPL", "1", 3)
        ));
        HoldingPageResponse first = query(HoldingFilter.ALL, "", 0, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();
        assertThat(first.holdings()).singleElement().satisfies(holding -> assertThat(holding.ticker()).isEqualTo("AAPL"));
        HoldingPageResponse second = query(HoldingFilter.ALL, "", 1, 1);
        assertThat(second.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.ticker()).isEqualTo("NVDA");
            assertThat(holding.quantity()).isEqualByComparingTo("2");
        });
        assertThat(second.last()).isTrue();
        assertThat(query(HoldingFilter.CLOSED, "", 0, 10).holdings()).singleElement()
                .satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("0"));
        for (String search : List.of("  nvda  ", "NVDA Inc", "US67066")) {
            assertThat(query(HoldingFilter.ALL, search, 0, 10).holdings()).singleElement()
                    .satisfies(holding -> assertThat(holding.ticker()).isEqualTo("NVDA"));
        }
        assertThat(query(HoldingFilter.OPEN, "AAPL", 0, 10).holdings()).isEmpty();
    }

    @Test
    void usesIsinAcrossTickerChangesAndKeepsLatestNonblankTradeMetadata() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", " us67066g1040 ", "OLD", "2", 1),
                activity("Limit buy", "US67066G1040", " nvda ", "1", 2),
                new HoldingActivity("Limit sell", "US67066G1040", null, null,
                        new BigDecimal("1"), OffsetDateTime.parse("2026-10-03T10:00:00Z"), 4)
        ));
        assertThat(query(HoldingFilter.ALL, "", 0, 10).holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.isin()).isEqualTo("US67066G1040");
            assertThat(holding.ticker()).isEqualTo("NVDA");
            assertThat(holding.instrumentName()).isEqualTo("nvda  Inc");
            assertThat(holding.quantity()).isEqualByComparingTo("2");
        });
    }

    @Test
    void keepsTickerFallbackSeparateFromIsinAndDoesNotMergeDifferentIsinsByTicker() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", null, " nvda ", "1", 1),
                activity("Limit buy", "US67066G1040", "NVDA", "2", 2),
                activity("Limit buy", "US0378331005", "NVDA", "3", 3),
                activity("Limit buy", null, null, "4", 4),
                activity("Dividend (Dividend)", "US88160R1014", "TSLA", "9", 5)
        ));
        assertThat(query(HoldingFilter.ALL, "", 0, 10).holdings()).extracting("instrumentKey")
                .containsExactly("ISIN:US0378331005", "ISIN:US67066G1040", "TICKER:NVDA");
    }

    @Test
    void returnsEmptyPagesWithAccurateTotals() {
        assertThat(query(HoldingFilter.ALL, "", 0, 10).totalElements()).isZero();
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", "US67066G1040", "NVDA", "2", 1)));
        HoldingPageResponse result = query(HoldingFilter.OPEN, "", 5, 10);
        assertThat(result.holdings()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.page()).isEqualTo(5);
        assertThat(result.last()).isTrue();
    }

    @Test
    void rejectsLegacyHistoryThatWasNegativeEvenIfTheFinalBalanceIsPositive() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit sell", "US67066G1040", "NVDA", "1", 1),
                activity("Limit buy", "US67066G1040", "NVDA", "2", 2)));
        assertThatThrownBy(() -> query(HoldingFilter.ALL, "", 0, 10))
                .isInstanceOf(ConflictException.class).hasMessageContaining("negative holdings");
    }

    @Test
    void rejectsMissingTradeQuantityInsteadOfReturningAMisleadingPosition() {
        when(activities.findHoldingActivities(userId, accountId)).thenReturn(List.of(
                activity("Limit buy", "US67066G1040", "NVDA", null, 1)));
        assertThatThrownBy(() -> query(HoldingFilter.ALL, "", 0, 10))
                .isInstanceOf(ConflictException.class).hasMessageContaining("missing number of shares");
    }

    @Test
    void rejectsAnotherUsersOrMissingAccountBeforeReadingHistory() {
        when(accounts.findByIdAndUser_Id(accountId, userId)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> query(HoldingFilter.ALL, "", 0, 10))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(activities, never()).findHoldingActivities(userId, accountId);
    }

    @Test
    void rejectsMissingIdentityAndOversizedSearch() {
        assertThatThrownBy(() -> service.getHoldingsByBrokerageAccount(null, accountId, HoldingFilter.OPEN, "", PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("User id");
        assertThatThrownBy(() -> service.getHoldingsByBrokerageAccount(userId, null, HoldingFilter.OPEN, "", PageRequest.of(0, 10)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("Brokerage account id");
        assertThatThrownBy(() -> query(HoldingFilter.OPEN, "x".repeat(121), 0, 10))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("120");
        verify(activities, never()).findHoldingActivities(userId, accountId);
    }

    private HoldingPageResponse query(HoldingFilter status, String search, int page, int size) {
        return service.getHoldingsByBrokerageAccount(userId, accountId, status, search, PageRequest.of(page, size));
    }

    private HoldingActivity activity(String action, String isin, String ticker, String shares, int day) {
        return new HoldingActivity(action, isin, ticker, ticker == null ? null : ticker + " Inc",
                shares == null ? null : new BigDecimal(shares),
                OffsetDateTime.parse("2026-10-%02dT10:00:00Z".formatted(day)), day + 1);
    }
}
