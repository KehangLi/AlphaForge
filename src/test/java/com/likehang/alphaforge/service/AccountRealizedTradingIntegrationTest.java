package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.rest.dto.response.AccountRealizedTradingSummaryResponse;
import com.likehang.alphaforge.rest.dto.response.InstrumentRealizedContributionResponse;
import com.likehang.alphaforge.service.trading.RealizedTradingCalculator;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;

@SpringBootTest
class AccountRealizedTradingIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NVDA = "US67066G1040";
    private static final String AAPL = "US0378331005";
    private static final String HEADER = "Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Result,Currency (Result),Total,Currency (Total),Currency conversion fee,Currency (Currency conversion fee)\n";

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired ImportBatchService batches;
    @Autowired BrokerageAccountService accountService;
    @Autowired AccountRealizedTradingQueryService summaries;
    @MockitoSpyBean RealizedTradingCalculator calculator;

    private AppUser user;
    private UUID account;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new AppUser("account-summary@example.com", "Summary test"));
        account = account(user, "Main");
    }

    @Test
    void reconcilesContributionTotalsAndCoverageWithTheAccountOverviewAcrossCurrencies() {
        String rows = row("Limit buy", NVDA, "NVDA", "BUY-N", "100", "99", "EUR", "2026-09-01T10:00:00Z")
                + row("Limit buy", AAPL, "AAPL", "BUY-A", "100", "", "", "2026-09-01T10:01:00Z")
                + row("Limit buy", "", "FALLBACK", "BUY-T", "100", "", "", "2026-09-01T10:02:00Z")
                + row("Limit sell", NVDA, "NVDA", "PROFIT", "1", "10", "EUR", "2026-10-01T00:00:00Z")
                + row("Stop sell", NVDA, "NVDA", "LOSS", "1", "-3", "EUR", "2026-10-31T23:59:59.999999Z")
                + row("Limit sell", NVDA, "NVDA", "ZERO", "1", "0", "EUR", "2026-10-05T10:00:00Z")
                + row("Limit sell", NVDA, "NVDA", "MISSING", "1", "", "EUR", "2026-10-06T10:00:00Z")
                + row("Limit sell", NVDA, "NVDA", "USD", "1", "5", "USD", "2026-10-07T10:00:00Z")
                + row("Limit sell", AAPL, "AAPL", "AAPL", "1", "-2", "EUR", "2026-10-02T10:00:00Z")
                + row("Limit sell", "", "", "UNKNOWN", "1", "4", "EUR", "2026-10-03T10:00:00Z")
                + row("Limit sell", "", "", "NO-CURRENCY", "1", "5", "", "2026-10-04T10:00:00Z")
                + row("Limit sell", "", "FALLBACK", "FALLBACK", "1", "2", "EUR", "2026-10-08T10:00:00Z")
                + row("Limit sell", NVDA, "NVDA", "NEITHER", "1", "", "", "2026-10-09T10:00:00Z")
                + row("Dividend (Dividend)", NVDA, "NVDA", "DIV", "100", "50", "EUR", "2026-10-10T10:00:00Z")
                + row("Result adjustment", "", "", "ADJ", "", "200", "EUR", "2026-10-11T10:00:00Z")
                + row("Interest on cash", "", "", "CASH", "", "20", "EUR", "2026-10-12T10:00:00Z");
        upload(rows);
        var result = query("2026-10-01", "2026-10-31", null, 0, 200);
        assertThat(result.realizedSummary().sellRecordCount()).isEqualTo(10);
        assertThat(result.realizedSummary().includedSellRecordCount()).isEqualTo(7);
        assertThat(result.realizedSummary().missingResultCount()).isEqualTo(2);
        assertThat(result.realizedSummary().missingResultCurrencyCount()).isEqualTo(1);
        assertThat(result.unidentifiedSellRecordCount()).isEqualTo(2);
        assertThat(result.totalElements()).isEqualTo(7);
        assertThat(result.realizedSummary().currencies()).extracting("currency").containsExactly("EUR", "USD");
        assertThat(result.realizedSummary().currencies().getFirst().totalResult()).isEqualByComparingTo("11");
        assertThat(result.realizedSummary().currencies().getLast().totalResult()).isEqualByComparingTo("5");
        for (var total : result.realizedSummary().currencies()) {
            BigDecimal contributed = result.contributions().stream()
                    .flatMap(contribution -> contribution.realizedSummary().currencies().stream())
                    .filter(summary -> summary.currency().equals(total.currency()))
                    .map(summary -> summary.totalResult()).reduce(BigDecimal.ZERO, BigDecimal::add);
            assertThat(contributed).isEqualByComparingTo(total.totalResult());
        }
        assertThat(result.contributions().stream().mapToLong(row -> row.realizedSummary().sellRecordCount()).sum()).isEqualTo(10);
        assertThat(result.contributions().stream().mapToLong(row -> row.realizedSummary().excludedSellRecordCount()).sum()).isEqualTo(3);
        assertThat(result.contributions().getFirst().instrumentKey()).isEqualTo("ISIN:" + NVDA);
        assertThat(result.contributions().get(1).instrumentKey()).isNull();
        var filtered = query("2026-10-01", "2026-10-31", "eur", 1, 1);
        assertThat(filtered.currency()).isEqualTo("EUR");
        assertThat(filtered.totalElements()).isEqualTo(4);
        assertThat(filtered.realizedSummary()).isEqualTo(result.realizedSummary());
        assertThat(filtered.contributions()).singleElement().satisfies(row -> assertThat(row.instrumentKey()).isNull());
        var beyond = query("2026-10-01", "2026-10-31", null, 1000, 10);
        assertThat(beyond.contributions()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(7);
        assertThat(beyond.realizedSummary()).isEqualTo(result.realizedSummary());
    }

    @Test
    void filtersOnSaleInstantUsingInclusiveUtcDaysAndHandlesUnboundedDates() {
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "100", "", "", "2026-09-01T10:00:00Z")
                + sale("BEFORE", "100", "2026-09-30T23:59:59.999999Z")
                + sale("START", "1", "2026-10-01T00:00:00Z")
                + sale("LOCAL-OCT", "100", "2026-10-01T01:00:00+02:00")
                + sale("END", "2", "2026-10-31T23:59:59.999999Z")
                + sale("LOCAL-NOV", "4", "2026-11-01T00:30:00+02:00")
                + sale("AFTER", "100", "2026-11-01T00:00:00Z"));
        assertTotal(query("2026-10-01", "2026-10-31", null, 0, 10), "7", 3);
        assertTotal(query("2026-10-01", null, null, 0, 10), "107", 4);
        assertTotal(query(null, "2026-10-31", null, 0, 10), "207", 5);
        assertTotal(query(null, null, null, 0, 10), "307", 6);
        assertTotal(query("2026-10-01", "2026-10-01", null, 0, 10), "1", 1);
        assertTotal(query(null, "9999-12-31", null, 0, 10), "307", 6);
    }

    @Test
    void supportsLeapDaysAndSingleSidedDateFilters() {
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "100", "", "", "2024-01-01T00:00:00Z")
                + sale("LEAP", "3", "2024-02-29T23:59:59.999999Z")
                + sale("MARCH", "5", "2024-03-01T00:00:00Z"));
        assertTotal(query("2024-02-29", "2024-02-29", null, 0, 10), "3", 1);
        assertTotal(query("2024-03-01", null, null, 0, 10), "5", 1);
    }

    @ParameterizedTest
    @EnumSource(AppUserType.class)
    void scopesResultsToTheSelectedAccountAndItsOwner(AppUserType type) {
        AppUser owner = users.saveAndFlush(new AppUser(type == AppUserType.GUEST ? null : "owner@example.com", "Owner", type));
        UUID first = account(owner, "First");
        UUID second = account(owner, "Second");
        String buys = row("Limit buy", NVDA, "NVDA", "BUY", "100", "", "", "2026-09-01T10:00:00Z");
        imports.importCsv(owner.getId(), first, file(buys + sale("SELL", "2", "2026-10-01T10:00:00Z")));
        imports.importCsv(owner.getId(), second, file(buys + sale("SELL", "99", "2026-10-01T10:00:00Z")));
        var result = summaries.getSummary(owner.getId(), first, null, null, null, PageRequest.of(0, 10));
        assertTotal(result, "2", 1);
        assertThatThrownBy(() -> summaries.getSummary(user.getId(), first, null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> summaries.getSummary(user.getId(), UUID.randomUUID(), null, null, null, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void usesIsinAcrossTickerChangesAndKeepsTickerOnlyOrDifferentIsinRowsSeparate() {
        upload(row("Limit buy", NVDA, "OLD", "BUY", "100", "", "", "2026-09-01T10:00:00Z")
                + row("Limit buy", "", "NEW", "BUY-FALLBACK", "100", "", "", "2026-09-01T10:01:00Z")
                + row("Limit buy", AAPL, "NEW", "BUY-OTHER", "100", "", "", "2026-09-01T10:02:00Z"));
        upload(row("Limit sell", NVDA, "NEW", "NEWER", "1", "10", "EUR", "2026-10-10T10:00:00Z"));
        upload(row("Limit sell", NVDA.toLowerCase(), "OLD", "OLDER", "1", "2", "EUR", "2026-10-02T10:00:00Z")
                + row("Limit sell", "", "NEW", "FALLBACK", "1", "3", "EUR", "2026-10-03T10:00:00Z")
                + row("Limit sell", AAPL, "NEW", "OTHER", "1", "5", "EUR", "2026-10-04T10:00:00Z"));
        var result = query(null, null, null, 0, 10);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.contributions()).extracting("instrumentKey").containsExactly("ISIN:" + NVDA, "ISIN:" + AAPL, "TICKER:NEW");
        assertThat(result.contributions().getFirst().ticker()).isEqualTo("NEW");
        assertThat(result.contributions().getFirst().instrumentName()).isEqualTo("NEW name");
    }

    @Test
    void emptyBuyOnlyAndUnmatchedPeriodsReturnAnEmptyOverviewAndContributionList() {
        assertThat(query(null, null, null, 0, 10).realizedSummary().sellRecordCount()).isZero();
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "100", "99", "EUR", "2026-09-01T10:00:00Z")
                + row("Dividend (Dividend)", NVDA, "NVDA", "DIV", "100", "9", "EUR", "2026-10-01T10:00:00Z"));
        var result = query(null, null, null, 0, 10);
        assertThat(result.contributions()).isEmpty();
        assertThat(result.realizedSummary().currencies()).isEmpty();
        upload(sale("SELL", "5", "2026-10-02T10:00:00Z"));
        var unmatched = query("2026-11-01", null, null, 0, 10);
        assertThat(unmatched.realizedSummary().currencies()).isEmpty();
        assertThat(unmatched.totalElements()).isZero();
    }

    @Test
    void missingResultDoesNotBecomeZeroButAnActualZeroIsIncluded() {
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "100", "", "", "2026-09-01T10:00:00Z")
                + row("Limit buy", AAPL, "AAPL", "BUY-A", "100", "", "", "2026-09-01T10:01:00Z")
                + row("Limit sell", NVDA, "NVDA", "MISSING", "1", "", "EUR", "2026-10-01T10:00:00Z")
                + row("Limit sell", AAPL, "AAPL", "ZERO", "1", "0", "EUR", "2026-10-02T10:00:00Z"));
        var rows = query(null, null, null, 0, 10).contributions();
        assertThat(rows).extracting("ticker").containsExactly("AAPL", "NVDA");
        assertThat(rows.getFirst().realizedSummary().currencies()).singleElement().satisfies(summary -> {
            assertThat(summary.totalResult()).isEqualByComparingTo("0");
            assertThat(summary.breakevenSellCount()).isEqualTo(1);
        });
        assertThat(rows.getLast().realizedSummary().currencies()).isEmpty();
        assertThat(rows.getLast().realizedSummary().missingResultCount()).isEqualTo(1);
    }

    @Test
    void deletedAndReimportedBatchesAreReflectedAndClosedPositionsStillContribute() {
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "1", "", "", "2026-09-01T10:00:00Z"));
        String sell = sale("SELL", "7", "2026-10-01T10:00:00Z");
        var batch = imports.importCsv(user.getId(), account, file(sell));
        assertTotal(query(null, null, null, 0, 10), "7", 1);
        batches.deleteImportBatch(user.getId(), account, batch.importBatchId());
        assertThat(query(null, null, null, 0, 10).realizedSummary().sellRecordCount()).isZero();
        upload(sell);
        assertTotal(query(null, null, null, 0, 10), "7", 1);
        accountService.deleteBrokerageAccount(user.getId(), account);
        assertThatThrownBy(() -> query(null, null, null, 0, 10)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void summaryAndContributionsRemainConsistentWhenDeletionCommitsDuringTheRead() throws Exception {
        upload(row("Limit buy", NVDA, "NVDA", "BUY", "100", "", "", "2026-09-01T10:00:00Z"));
        var batch = imports.importCsv(user.getId(), account, file(sale("SELL", "7", "2026-10-01T10:00:00Z")));
        CountDownLatch read = new CountDownLatch(1);
        CountDownLatch resume = new CountDownLatch(1);
        AtomicBoolean first = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (first.compareAndSet(true, false)) {
                read.countDown();
                if (!resume.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for deletion");
                }
            }
            return invocation.callRealMethod();
        }).when(calculator).calculate(anyList());
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> query(null, null, null, 0, 10));
            try {
                assertThat(read.await(10, TimeUnit.SECONDS)).isTrue();
                batches.deleteImportBatch(user.getId(), account, batch.importBatchId());
            } finally {
                resume.countDown();
            }
            var result = pending.get(10, TimeUnit.SECONDS);
            assertTotal(result, "7", 1);
            assertThat(result.contributions()).singleElement().satisfies(row ->
                    assertThat(row.realizedSummary().currencies().getFirst().totalResult()).isEqualByComparingTo("7"));
        }
        assertThat(query(null, null, null, 0, 10).contributions()).isEmpty();
    }

    private UUID account(AppUser owner, String name) {
        return accounts.saveAndFlush(new BrokerageAccount(owner, "Trading 212", name, "EUR")).getId();
    }

    private void upload(String rows) {
        imports.importCsv(user.getId(), account, file(rows));
    }

    private MockMultipartFile file(String rows) {
        return new MockMultipartFile("file", "summary.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    private AccountRealizedTradingSummaryResponse query(String from, String to, String currency, int page, int size) {
        return summaries.getSummary(user.getId(), account, from == null ? null : LocalDate.parse(from),
                to == null ? null : LocalDate.parse(to), currency, PageRequest.of(page, size));
    }

    private void assertTotal(AccountRealizedTradingSummaryResponse response, String expected, long sellCount) {
        assertThat(response.realizedSummary().sellRecordCount()).isEqualTo(sellCount);
        assertThat(response.realizedSummary().currencies()).singleElement()
                .satisfies(summary -> assertThat(summary.totalResult()).isEqualByComparingTo(expected));
    }

    private String sale(String id, String result, String time) {
        return row("Limit sell", NVDA, "NVDA", id, "1", result, "EUR", time);
    }

    private String row(String action, String isin, String ticker, String id, String shares, String result, String currency, String time) {
        return String.join(",", action, time, isin, ticker, ticker + " name", id, shares,
                result, currency, "100", "EUR", "0.25", "EUR") + "\n";
    }
}
