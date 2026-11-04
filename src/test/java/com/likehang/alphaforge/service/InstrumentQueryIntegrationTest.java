package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.model.holding.HoldingStatus;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.rest.dto.response.AccountActivityCsvImportResult;
import com.likehang.alphaforge.rest.dto.response.InstrumentDetailResponse;
import com.likehang.alphaforge.service.holding.HoldingCalculator;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.nio.charset.StandardCharsets;
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
class InstrumentQueryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NVDA = "US67066G1040";
    private static final String AAPL = "US0378331005";
    private static final String KEY = "ISIN:" + NVDA;
    private static final String HEADER = "Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Total,Currency (Total)\n";

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired ImportBatchService batchService;
    @Autowired InstrumentQueryService instruments;
    @Autowired HoldingQueryService holdings;
    @Autowired JdbcTemplate jdbc;
    @MockitoSpyBean HoldingCalculator calculator;

    private AppUser user;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new AppUser("instrument@example.com", "Instrument test"));
        accountId = account(user, "Main");
    }

    @Test
    void returnsOneInstrumentsFullSummaryAlongsideItsPagedTradesAndDividends() {
        upload(row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "5", "2026-10-01 10:00:00+00:00")
                + row("Limit buy", AAPL, "AAPL", "Apple", "OTHER", "9", "2026-10-01 11:00:00+00:00"));
        upload(row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "2", "2026-10-02 10:00:00+00:00")
                + row("Dividend (Dividend)", NVDA, "NVDA", "NVIDIA", "", "50", "2026-10-03 10:00:00+00:00"));

        var first = detail(KEY, 0, 1);
        assertThat(first.instrumentName()).isEqualTo("NVIDIA");
        assertThat(first.holding().quantity()).isEqualByComparingTo("3");
        assertThat(first.holding().totalBoughtQuantity()).isEqualByComparingTo("5");
        assertThat(first.holding().totalSoldQuantity()).isEqualByComparingTo("2");
        assertThat(first.totalElements()).isEqualTo(3);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();
        assertThat(first.activities()).singleElement().satisfies(activity -> {
            assertThat(activity.action()).isEqualTo("Dividend (Dividend)");
            assertThat(activity.isin()).isEqualTo(NVDA);
            assertThat(activity.importBatchId()).isNotNull();
        });
        var second = detail(KEY, 1, 1);
        assertThat(second.holding()).isEqualTo(first.holding());
        assertThat(second.activities()).singleElement().satisfies(activity -> assertThat(activity.externalTransactionId()).isEqualTo("SELL"));
        var third = detail(KEY, 2, 1);
        assertThat(third.last()).isTrue();
        assertThat(third.activities()).singleElement().satisfies(activity -> assertThat(activity.externalTransactionId()).isEqualTo("BUY"));
        var beyond = detail(KEY, 1000, 10);
        assertThat(beyond.activities()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(3);
        assertThat(beyond.holding()).isEqualTo(first.holding());
        assertThat(holdings.getHoldingsByBrokerageAccount(user.getId(), accountId, HoldingFilter.ALL, "NVDA", PageRequest.of(0, 10))
                .holdings()).containsExactly(first.holding());
    }

    @Test
    void retainsClosedInstrumentsAndReflectsDeletionAndReimport() {
        var buy = upload(row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-01 10:00:00+00:00"));
        String sellCsv = row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "2", "2026-10-02 10:00:00+00:00");
        var sell = upload(sellCsv);
        assertThat(detail(KEY, 0, 10).holding().status()).isEqualTo(HoldingStatus.CLOSED);
        batchService.deleteImportBatch(user.getId(), accountId, sell.importBatchId());
        assertThat(detail(KEY, 0, 10).holding().quantity()).isEqualByComparingTo("2");
        var importedAgain = upload(sellCsv);
        assertThat(detail(KEY, 0, 10).holding().status()).isEqualTo(HoldingStatus.CLOSED);
        batchService.deleteImportBatch(user.getId(), accountId, importedAgain.importBatchId());
        batchService.deleteImportBatch(user.getId(), accountId, buy.importBatchId());
        assertThatThrownBy(() -> detail(KEY, 0, 10)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void groupsIsinHistoryAcrossTickerChangesAndBackfilledImports() {
        upload(row("Limit buy", NVDA, "NEW", "New name", "NEW", "1", "2026-10-02 10:00:00+00:00"));
        upload(row("Limit buy", NVDA.toLowerCase(), "OLD", "Old name", "OLD", "2", "2026-10-01 10:00:00+00:00"));
        var detail = detail(" isin:us67066g1040 ", 0, 10);
        assertThat(detail.instrumentKey()).isEqualTo(KEY);
        assertThat(detail.ticker()).isEqualTo("NEW");
        assertThat(detail.instrumentName()).isEqualTo("New name");
        assertThat(detail.holding().quantity()).isEqualByComparingTo("3");
        assertThat(detail.activities()).extracting("externalTransactionId").containsExactly("NEW", "OLD");
        assertThatThrownBy(() -> detail("TICKER:OLD", 0, 10)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void tickerFallbackDoesNotMergeIsinBackedRowsSharingTheTicker() {
        upload(row("Limit buy", "", " aapl ", "Apple fallback", "FALLBACK", "1", "2026-10-01 10:00:00+00:00")
                + row("Limit buy", AAPL, "AAPL", "Apple", "ISIN", "10", "2026-10-02 10:00:00+00:00")
                + row("Limit buy", NVDA, "AAPL", "Different instrument", "OTHER", "20", "2026-10-03 10:00:00+00:00"));
        var fallback = detail("ticker:aapl", 0, 10);
        assertThat(fallback.isin()).isNull();
        assertThat(fallback.totalElements()).isEqualTo(1);
        assertThat(fallback.holding().quantity()).isEqualByComparingTo("1");
        assertThat(detail("ISIN:" + AAPL, 0, 10).holding().quantity()).isEqualByComparingTo("10");
        assertThat(detail(KEY, 0, 10).holding().quantity()).isEqualByComparingTo("20");
    }

    @Test
    void dividendOnlyInstrumentsHaveActivityAndMetadataButNoInferredPosition() {
        upload(row("Dividend (Dividend)", NVDA, "NVDA", "NVIDIA", "", "2", "2026-10-01 10:00:00+00:00"));
        var detail = detail(KEY, 0, 10);
        assertThat(detail.holding()).isNull();
        assertThat(detail.instrumentName()).isEqualTo("NVIDIA");
        assertThat(detail.totalElements()).isEqualTo(1);
        assertThat(detail.activities()).hasSize(1);
    }

    @Test
    void ordersEqualInstantTradesByImportSequenceBeforeCsvRowNumber() {
        upload(row("Interest on cash", "", "", "", "CASH", "", "2026-09-01 10:00:00+00:00")
                + row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-01 10:00:00+00:00"));
        upload(row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "1", "2026-10-01 12:00:00+02:00"));
        var detail = detail(KEY, 0, 1);
        assertThat(detail.holding().quantity()).isEqualByComparingTo("1");
        assertThat(detail.activities()).singleElement().satisfies(activity -> assertThat(activity.externalTransactionId()).isEqualTo("SELL"));
        assertThat(detail(KEY, 1, 1).activities()).singleElement().satisfies(activity -> assertThat(activity.externalTransactionId()).isEqualTo("BUY"));
    }

    @ParameterizedTest
    @EnumSource(AppUserType.class)
    void isolatesAccountsAndUsersForRegisteredAndGuestSessions(AppUserType type) {
        AppUser owner = users.saveAndFlush(new AppUser(type == AppUserType.GUEST ? null : "owner@example.com", "Owner", type));
        UUID first = account(owner, "First");
        UUID second = account(owner, "Second");
        imports.importCsv(owner.getId(), first, file(row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "1", "2026-10-01 10:00:00+00:00")));
        imports.importCsv(owner.getId(), second, file(row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "9", "2026-10-01 10:00:00+00:00")));
        var result = instruments.getInstrumentByBrokerageAccount(owner.getId(), first, KEY, PageRequest.of(0, 10));
        assertThat(result.holding().quantity()).isEqualByComparingTo("1");
        assertThat(result.totalElements()).isEqualTo(1);
        assertThatThrownBy(() -> instruments.getInstrumentByBrokerageAccount(user.getId(), first, KEY, PageRequest.of(0, 10)))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsBrokenSelectedHistoryWithoutReadingUnrelatedInstruments() {
        upload(row("Limit buy", NVDA, "NVDA", "NVIDIA", "GOOD", "2", "2026-10-01 10:00:00+00:00")
                + row("Limit buy", AAPL, "AAPL", "Apple", "BAD", "1", "2026-10-02 10:00:00+00:00"));
        jdbc.update("update account_activity set action = 'Limit sell' where external_transaction_id = 'BAD'");
        assertThat(detail(KEY, 0, 10).holding().quantity()).isEqualByComparingTo("2");
        assertThatThrownBy(() -> detail("ISIN:" + AAPL, 0, 10)).isInstanceOf(ConflictException.class);
        jdbc.update("update account_activity set number_of_shares = null where external_transaction_id = 'GOOD'");
        assertThatThrownBy(() -> detail(KEY, 0, 10)).isInstanceOf(ConflictException.class);
    }

    @Test
    void summaryAndPageUseOneSnapshotWhenABatchIsDeletedDuringTheQuery() throws Exception {
        upload(row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "5", "2026-10-01 10:00:00+00:00"));
        var sell = upload(row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "2", "2026-10-02 10:00:00+00:00"));
        CountDownLatch historyRead = new CountDownLatch(1);
        CountDownLatch continueQuery = new CountDownLatch(1);
        AtomicBoolean firstCall = new AtomicBoolean(true);
        doAnswer(invocation -> {
            if (firstCall.compareAndSet(true, false)) {
                historyRead.countDown();
                if (!continueQuery.await(10, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Timed out waiting for deletion");
                }
            }
            return invocation.callRealMethod();
        }).when(calculator).calculate(anyList());

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var pending = executor.submit(() -> detail(KEY, 0, 1));
            try {
                assertThat(historyRead.await(10, TimeUnit.SECONDS)).isTrue();
                batchService.deleteImportBatch(user.getId(), accountId, sell.importBatchId());
            } finally {
                continueQuery.countDown();
            }
            var result = pending.get(10, TimeUnit.SECONDS);
            assertThat(result.holding().quantity()).isEqualByComparingTo("3");
            assertThat(result.totalElements()).isEqualTo(2);
            assertThat(result.activities()).singleElement().satisfies(activity -> assertThat(activity.externalTransactionId()).isEqualTo("SELL"));
        }
        var nextQuery = detail(KEY, 0, 10);
        assertThat(nextQuery.holding().quantity()).isEqualByComparingTo("5");
        assertThat(nextQuery.totalElements()).isEqualTo(1);
    }

    private UUID account(AppUser owner, String name) {
        return accounts.saveAndFlush(new BrokerageAccount(owner, "Trading 212", name, "EUR")).getId();
    }

    private AccountActivityCsvImportResult upload(String rows) {
        return imports.importCsv(user.getId(), accountId, file(rows));
    }

    private MockMultipartFile file(String rows) {
        return new MockMultipartFile("file", "instrument.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    private InstrumentDetailResponse detail(String key, int page, int size) {
        return instruments.getInstrumentByBrokerageAccount(user.getId(), accountId, key, PageRequest.of(page, size));
    }

    private String row(String action, String isin, String ticker, String name, String id, String shares, String time) {
        return String.join(",", action, time, isin, ticker, name, id, shares, "100", "EUR") + "\n";
    }
}
