package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.model.holding.HoldingStatus;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import com.likehang.alphaforge.rest.dto.response.AccountActivityCsvImportResult;
import com.likehang.alphaforge.rest.dto.response.HoldingPageResponse;
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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class HoldingQueryIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String NVDA = "US67066G1040";
    private static final String AAPL = "US0378331005";
    private static final String HEADER = "Action,Time (UTC),ISIN,Ticker,Name,ID,No. of shares,Total,Currency (Total)\n";

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired ImportBatchRepository batches;
    @Autowired AccountActivityRepository activities;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired ImportBatchService batchService;
    @Autowired HoldingQueryService holdings;
    @Autowired JdbcTemplate jdbc;

    private AppUser user;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new AppUser("holdings@example.com", "Holdings test"));
        accountId = account(user, "Main");
    }

    @Test
    void aggregatesTheEntireHistoryAcrossBatchesBeforeFilteringAndPaging() {
        upload(accountId,
                row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY-N", "2.5000000000", "2026-10-01 10:00:00+00:00")
                + row("Market buy", AAPL, "AAPL", "Apple", "BUY-A", "1", "2026-10-01 11:00:00+00:00"));
        upload(accountId,
                row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL-N", "1.125", "2026-10-02 10:00:00+00:00")
                + row("Stop sell", AAPL, "AAPL", "Apple", "SELL-A", "1", "2026-10-02 11:00:00+00:00")
                + row("Dividend (Dividend)", NVDA, "NVDA", "NVIDIA", "", "100", "2026-10-03 10:00:00+00:00")
                + row("Interest on cash", "", "", "", "CASH", "", "2026-10-04 10:00:00+00:00"));

        HoldingPageResponse first = query(accountId, HoldingFilter.ALL, "", 0, 1);
        assertThat(first.totalElements()).isEqualTo(2);
        assertThat(first.totalPages()).isEqualTo(2);
        assertThat(first.holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.ticker()).isEqualTo("AAPL");
            assertThat(holding.status()).isEqualTo(HoldingStatus.CLOSED);
            assertThat(holding.quantity()).isEqualByComparingTo("0");
        });
        assertThat(query(accountId, HoldingFilter.ALL, "", 1, 1).holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("1.375");
            assertThat(holding.totalBoughtQuantity()).isEqualByComparingTo("2.5");
            assertThat(holding.totalSoldQuantity()).isEqualByComparingTo("1.125");
            assertThat(holding.lastTradedAt().toInstant()).isEqualTo(OffsetDateTime.parse("2026-10-02T10:00:00Z").toInstant());
        });
        assertThat(query(accountId, HoldingFilter.OPEN, "  nvidia  ", 0, 10).totalElements()).isEqualTo(1);
        assertThat(query(accountId, HoldingFilter.CLOSED, "US0378", 0, 10).totalElements()).isEqualTo(1);
        assertThat(query(accountId, HoldingFilter.CLOSED, "NVDA", 0, 10).totalElements()).isZero();
        assertThat(query(accountId, HoldingFilter.ALL, "not-a-stock", 0, 10).holdings()).isEmpty();
        assertThat(query(accountId, HoldingFilter.ALL, "%", 0, 10).holdings()).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(AppUserType.class)
    void scopesAllTradesToTheChosenAccountAndUserForGuestsAndRegisteredUsers(AppUserType type) {
        AppUser owner = users.saveAndFlush(new AppUser(
                type == AppUserType.GUEST ? null : "owner@example.com", "Owner", type));
        UUID first = account(owner, "First");
        UUID second = account(owner, "Second");
        String trade = row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-01 10:00:00+00:00");
        imports.importCsv(owner.getId(), first, file(trade));
        imports.importCsv(owner.getId(), second, file(trade.replace(",2,100,", ",9,100,")));

        assertThat(holdings.getHoldingsByBrokerageAccount(owner.getId(), first, HoldingFilter.ALL, "", PageRequest.of(0, 10)).holdings())
                .singleElement().satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("2"));
        assertThatThrownBy(() -> query(first, HoldingFilter.ALL, "", 0, 10)).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> query(UUID.randomUUID(), HoldingFilter.ALL, "", 0, 10)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void includesClosedAndReopenedPositionsAndReflectsBatchDeletionWithoutStaleSnapshots() {
        upload(accountId, row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-01 10:00:00+00:00"));
        var sell = upload(accountId, row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "2", "2026-10-02 10:00:00+00:00"));
        assertThat(query(accountId, HoldingFilter.OPEN, "", 0, 10).holdings()).isEmpty();
        assertThat(query(accountId, HoldingFilter.CLOSED, "", 0, 10).holdings()).hasSize(1);

        var reopen = upload(accountId, row("Stop buy", NVDA, "NVDA", "NVIDIA", "REOPEN", "0.5", "2026-10-03 10:00:00+00:00"));
        assertThat(query(accountId, HoldingFilter.OPEN, "", 0, 10).holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.quantity()).isEqualByComparingTo("0.5");
            assertThat(holding.totalBoughtQuantity()).isEqualByComparingTo("2.5");
        });
        batchService.deleteImportBatch(user.getId(), accountId, reopen.importBatchId());
        assertThat(query(accountId, HoldingFilter.OPEN, "", 0, 10).holdings()).isEmpty();
        batchService.deleteImportBatch(user.getId(), accountId, sell.importBatchId());
        assertThat(query(accountId, HoldingFilter.OPEN, "", 0, 10).holdings()).singleElement()
                .satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("2"));
    }

    @Test
    void preservesImportOrderForSameInstantTradesRegardlessOfRowNumberOrTimezone() {
        upload(accountId, row("Interest on cash", "", "", "", "CASH", "", "2026-09-01 10:00:00+00:00")
                + row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-01 10:00:00+00:00"));
        upload(accountId, row("Limit sell", NVDA, "NVDA", "NVIDIA", "SELL", "1", "2026-10-01 12:00:00+02:00"));
        assertThat(query(accountId, HoldingFilter.OPEN, "", 0, 10).holdings()).singleElement()
                .satisfies(holding -> assertThat(holding.quantity()).isEqualByComparingTo("1"));
    }

    @Test
    void groupsNormalizedIdentifiersAndKeepsLatestMetadataByTradeTimeRatherThanImportTime() {
        upload(accountId, row("Limit buy", NVDA, "NEW", "New name", "NEW", "1", "2026-10-02 10:00:00+00:00"));
        upload(accountId, row("Limit buy", NVDA.toLowerCase(), "OLD", "Old name", "OLD", "2", "2026-10-01 10:00:00+00:00"));
        assertThat(query(accountId, HoldingFilter.ALL, "", 0, 10).holdings()).singleElement().satisfies(holding -> {
            assertThat(holding.instrumentKey()).isEqualTo("ISIN:" + NVDA);
            assertThat(holding.quantity()).isEqualByComparingTo("3");
            assertThat(holding.ticker()).isEqualTo("NEW");
            assertThat(holding.instrumentName()).isEqualTo("New name");
        });
    }

    @Test
    void excludesDividendOnlyInstrumentsAndSupportsTickerOnlyTrades() {
        upload(accountId, row("Dividend (Dividend)", NVDA, "NVDA", "NVIDIA", "", "10", "2026-10-01 10:00:00+00:00")
                + row("Limit buy", "", " aapl ", "Apple", "BUY", "0.1", "2026-10-02 10:00:00+00:00")
                + row("Limit buy", AAPL, "AAPL", "Apple", "BUY-ISIN", "0.2", "2026-10-03 10:00:00+00:00"));
        assertThat(query(accountId, HoldingFilter.ALL, "", 0, 10).holdings()).extracting("instrumentKey")
                .containsExactly("ISIN:" + AAPL, "TICKER:AAPL");
    }

    @Test
    void returnsAnEmptyPageForAnAccountWithNoTradesOrAnOutOfRangePage() {
        assertThat(query(accountId, HoldingFilter.ALL, "", 0, 10).totalElements()).isZero();
        upload(accountId, row("Interest on cash", "", "", "", "CASH", "", "2026-10-01 10:00:00+00:00"));
        assertThat(query(accountId, HoldingFilter.ALL, "", 0, 10).holdings()).isEmpty();
        upload(accountId, row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "2", "2026-10-02 10:00:00+00:00"));
        var result = query(accountId, HoldingFilter.OPEN, "", 1000, 10);
        assertThat(result.holdings()).isEmpty();
        assertThat(result.totalElements()).isEqualTo(1);
    }

    @Test
    void rejectsIncompleteLegacyHistoryInsteadOfPublishingANegativeOrMisleadingHolding() {
        upload(accountId, row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "1", "2026-10-01 10:00:00+00:00"));
        jdbc.update("update account_activity set action = 'Limit sell' where brokerage_account_id = ?", accountId);
        assertThatThrownBy(() -> query(accountId, HoldingFilter.ALL, "", 0, 10))
                .isInstanceOf(ConflictException.class).hasMessageContaining("negative holdings");
        jdbc.update("update account_activity set action = 'Limit buy', number_of_shares = null where brokerage_account_id = ?", accountId);
        assertThatThrownBy(() -> query(accountId, HoldingFilter.ALL, "", 0, 10))
                .isInstanceOf(ConflictException.class).hasMessageContaining("missing number of shares");
    }

    @Test
    void queriesDoNotWriteToTheDatabaseOrCreateHoldingSnapshots() {
        upload(accountId, row("Limit buy", NVDA, "NVDA", "NVIDIA", "BUY", "1", "2026-10-01 10:00:00+00:00"));
        var original = batches.findAll().getFirst();
        query(accountId, HoldingFilter.ALL, "", 0, 10);
        query(accountId, HoldingFilter.OPEN, "NVDA", 0, 1);
        ImportBatch after = batches.findById(original.getId()).orElseThrow();
        assertThat(after.getUpdatedAt()).isEqualTo(original.getUpdatedAt());
        assertThat(batches.count()).isEqualTo(1);
        assertThat(activities.count()).isEqualTo(1);
    }

    private UUID account(AppUser owner, String name) {
        return accounts.saveAndFlush(new BrokerageAccount(owner, "Trading 212", name, "EUR")).getId();
    }

    private AccountActivityCsvImportResult upload(UUID targetAccount, String rows) {
        return imports.importCsv(user.getId(), targetAccount, file(rows));
    }

    private MockMultipartFile file(String rows) {
        return new MockMultipartFile("file", "holdings.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    private HoldingPageResponse query(UUID targetAccount, HoldingFilter status, String search, int page, int size) {
        return holdings.getHoldingsByBrokerageAccount(user.getId(), targetAccount, status, search, PageRequest.of(page, size));
    }

    private String row(String action, String isin, String ticker, String name, String id, String shares, String time) {
        return String.join(",", action, time, isin, ticker, name, id, shares, "100", "EUR") + "\n";
    }
}
