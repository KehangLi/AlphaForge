package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.rest.dto.request.JournalNoteRequest;
import com.likehang.alphaforge.rest.dto.response.InstrumentDetailResponse;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InstrumentReviewIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String KEY = "ISIN:US67066G1040";
    private static final String HEADER = "Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Currency conversion fee,Currency (Currency conversion fee)\n";

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired AccountActivityRepository activities;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired InstrumentQueryService instruments;
    @Autowired AccountActivityQueryService accountActivities;
    @Autowired AccountActivityJournalService journals;
    @Autowired ImportBatchService batches;
    @Autowired BrokerageAccountService accountService;
    @Autowired JdbcTemplate jdbc;

    private AppUser user;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        user = users.saveAndFlush(new AppUser("review@example.com", "Review test"));
        accountId = account(user, "Main");
    }

    @Test
    void aggregatesAllSellResultsByCurrencyAndReportsMissingDataIndependentOfPage() {
        String csv = row("Limit buy", "BUY", "100", "99", "EUR", 1)
                + row("Limit sell", "PROFIT", "1", "10", "EUR", 2)
                + row("Stop sell", "LOSS", "1", "-4", "EUR", 3)
                + row("Limit sell", "ZERO", "1", "0", "EUR", 4)
                + row("Limit sell", "USD", "1", "5", "USD", 5)
                + row("Limit sell", "NO-RESULT", "1", "", "EUR", 6)
                + row("Limit sell", "NO-CURRENCY", "1", "7", "", 7)
                + row("Limit sell", "NEITHER", "1", "", "", 8)
                + row("Dividend (Dividend)", "DIV", "100", "50", "EUR", 9)
                + row("Result adjustment", "ADJ", "", "20", "EUR", 10)
                + row("Limit buy", "OTHER-BUY", "10", "", "", 11).replace("US67066G1040,NVDA,NVIDIA", "US0378331005,AAPL,Apple")
                + row("Limit sell", "OTHER-SELL", "1", "999", "EUR", 12).replace("US67066G1040,NVDA,NVIDIA", "US0378331005,AAPL,Apple");
        imports.importCsv(user.getId(), accountId, file(csv));
        var first = detail(0, 1);
        assertThat(first.realizedSummary().sellRecordCount()).isEqualTo(7);
        assertThat(first.realizedSummary().includedSellRecordCount()).isEqualTo(4);
        assertThat(first.realizedSummary().excludedSellRecordCount()).isEqualTo(3);
        assertThat(first.realizedSummary().missingResultCount()).isEqualTo(2);
        assertThat(first.realizedSummary().missingResultCurrencyCount()).isEqualTo(1);
        assertThat(first.realizedSummary().currencies()).extracting("currency").containsExactly("EUR", "USD");
        var eur = first.realizedSummary().currencies().getFirst();
        assertThat(eur.totalResult()).isEqualByComparingTo("6");
        assertThat(eur.totalProfit()).isEqualByComparingTo("10");
        assertThat(eur.totalLoss()).isEqualByComparingTo("-4");
        assertThat(eur.breakevenSellCount()).isEqualTo(1);
        assertThat(first.realizedSummary().currencies().getLast().totalResult()).isEqualByComparingTo("5");
        assertThat(first.holding().quantity()).isEqualByComparingTo("93");
        assertThat(first.totalElements()).isEqualTo(10);
        assertThat(detail(5, 1).realizedSummary()).isEqualTo(first.realizedSummary());
        var outOfRange = detail(100, 10);
        assertThat(outOfRange.activities()).isEmpty();
        assertThat(outOfRange.realizedSummary()).isEqualTo(first.realizedSummary());
    }

    @Test
    void returnsZeroSellCoverageForBuyOnlyAndDividendOnlyHistory() {
        imports.importCsv(user.getId(), accountId, file(row("Dividend (Dividend)", "DIV", "5", "", "", 1)));
        var dividendOnly = detail(0, 10);
        assertThat(dividendOnly.holding()).isNull();
        assertThat(dividendOnly.realizedSummary().sellRecordCount()).isZero();
        assertThat(dividendOnly.realizedSummary().currencies()).isEmpty();
        imports.importCsv(user.getId(), accountId, file(row("Limit buy", "BUY", "5", "", "", 2)));
        assertThat(detail(0, 10).realizedSummary().currencies()).isEmpty();
    }

    @Test
    void savesEditsAndClearsNotesWithoutChangingImportedContentOrDuplicateDetection() {
        String csv = row("Limit buy", "BUY", "5", "", "", 1);
        imports.importCsv(user.getId(), accountId, file(csv));
        var original = detail(0, 10).activities().getFirst();
        var persisted = activities.findById(original.id()).orElseThrow();
        String rawHash = persisted.getRawRowHash();
        var saved = journals.saveJournalNote(user.getId(), accountId, original.id(), new JournalNoteRequest("Entry reason\nExit plan"));
        var row = detail(0, 10).activities().getFirst();
        assertThat(row.journalNote()).isEqualTo("Entry reason\nExit plan");
        assertThat(row.journalNoteUpdatedAt()).isNotNull();
        assertThat(row.journalNoteUpdatedAt()).isEqualTo(saved.journalNoteUpdatedAt());
        assertThat(row.notes()).isEqualTo("Broker note");
        assertThat(row.importFilename()).isEqualTo("review.csv");
        assertThat(row.rowNumber()).isEqualTo(2);
        assertThat(row.importBatchId()).isEqualTo(original.importBatchId());
        assertThat(row.resultAmount()).isEqualTo(original.resultAmount());
        assertThat(row.currencyConversionFee()).isEqualByComparingTo("0.25");
        var again = journals.saveJournalNote(user.getId(), accountId, original.id(), new JournalNoteRequest("Entry reason\nExit plan"));
        assertThat(again.journalNoteUpdatedAt()).isEqualTo(row.journalNoteUpdatedAt());
        assertThat(saved.journalNote()).isEqualTo(again.journalNote());
        assertThat(accountActivities.getActivitiesByBrokerageAccount(user.getId(), accountId, PageRequest.of(0, 10))
                .activities().getFirst().journalNote()).isEqualTo(row.journalNote());
        journals.saveJournalNote(user.getId(), accountId, original.id(), new JournalNoteRequest("Revised review"));
        assertThat(detail(0, 10).activities().getFirst().journalNote()).isEqualTo("Revised review");
        journals.saveJournalNote(user.getId(), accountId, original.id(), new JournalNoteRequest(""));
        var cleared = activities.findById(original.id()).orElseThrow();
        assertThat(cleared.getJournalNote()).isNull();
        assertThat(cleared.getJournalNoteUpdatedAt()).isNotNull();
        assertThat(cleared.getNotes()).isEqualTo("Broker note");
        assertThat(cleared.getRawRowHash()).isEqualTo(rawHash);
        assertThatThrownBy(() -> imports.importCsv(user.getId(), accountId, file(csv)))
                .isInstanceOf(CsvImportException.class).hasMessageContaining("already imported");
    }

    @ParameterizedTest
    @EnumSource(AppUserType.class)
    void scopesJournalWritesToTheActivityOwnerAndSelectedAccount(AppUserType type) {
        AppUser owner = users.saveAndFlush(new AppUser(type == AppUserType.GUEST ? null : "owner@example.com", "Owner", type));
        UUID first = account(owner, "First");
        UUID second = account(owner, "Second");
        imports.importCsv(owner.getId(), first, file(row("Limit buy", "BUY", "2", "", "", 1)));
        UUID id = activities.findByBrokerageAccount_Id(first).getFirst().getId();
        journals.saveJournalNote(owner.getId(), first, id, new JournalNoteRequest("Owner's note"));
        assertThatThrownBy(() -> journals.saveJournalNote(user.getId(), first, id, new JournalNoteRequest("Foreign user")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> journals.saveJournalNote(owner.getId(), second, id, new JournalNoteRequest("Wrong account")))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThat(activities.findById(id).orElseThrow().getJournalNote()).isEqualTo("Owner's note");
    }

    @Test
    void deletionRemovesNotesAndUpdatesSummaryAndReimportDoesNotRestoreOldNotes() {
        imports.importCsv(user.getId(), accountId, file(row("Limit buy", "BUY", "2", "", "", 1)));
        String sellCsv = row("Limit sell", "SELL", "1", "10", "EUR", 2);
        var batch = imports.importCsv(user.getId(), accountId, file(sellCsv));
        UUID sell = detail(0, 10).activities().getFirst().id();
        journals.saveJournalNote(user.getId(), accountId, sell, new JournalNoteRequest("Review sell"));
        assertThat(detail(0, 10).realizedSummary().sellRecordCount()).isEqualTo(1);
        batches.deleteImportBatch(user.getId(), accountId, batch.importBatchId());
        assertThat(activities.existsById(sell)).isFalse();
        assertThat(detail(0, 10).realizedSummary().sellRecordCount()).isZero();
        assertThatThrownBy(() -> journals.saveJournalNote(user.getId(), accountId, sell, new JournalNoteRequest("Stale editor")))
                .isInstanceOf(ResourceNotFoundException.class);
        imports.importCsv(user.getId(), accountId, file(sellCsv));
        assertThat(detail(0, 10).activities().getFirst().journalNote()).isNull();
        accountService.deleteBrokerageAccount(user.getId(), accountId);
        assertThat(activities.count()).isZero();
    }

    @Test
    void concurrentJournalEditAndBatchDeletionCannotResurrectTheActivity() throws Exception {
        var batch = imports.importCsv(user.getId(), accountId, file(row("Limit buy", "BUY", "2", "", "", 1)));
        UUID id = detail(0, 10).activities().getFirst().id();
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var edit = executor.submit(() -> {
                start.await();
                try { journals.saveJournalNote(user.getId(), accountId, id, new JournalNoteRequest("Concurrent note")); }
                catch (ResourceNotFoundException ignored) { /* Deletion acquired the user lock first. */ }
                return true;
            });
            var delete = executor.submit(() -> {
                start.await();
                batches.deleteImportBatch(user.getId(), accountId, batch.importBatchId());
                return true;
            });
            start.countDown();
            assertThat(edit.get(10, TimeUnit.SECONDS)).isTrue();
            assertThat(delete.get(10, TimeUnit.SECONDS)).isTrue();
        }
        assertThat(activities.existsById(id)).isFalse();
    }

    @Test
    void acceptsMaximumNoteLengthAndDatabaseAlsoRejectsOversizedDirectWrites() {
        imports.importCsv(user.getId(), accountId, file(row("Limit buy", "BUY", "2", "", "", 1)));
        UUID id = detail(0, 10).activities().getFirst().id();
        journals.saveJournalNote(user.getId(), accountId, id, new JournalNoteRequest("x".repeat(5000)));
        assertThat(activities.findById(id).orElseThrow().getJournalNote()).hasSize(5000);
        assertThatThrownBy(() -> jdbc.update("update account_activity set journal_note = ? where id = ?", "x".repeat(5001), id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private UUID account(AppUser owner, String name) {
        return accounts.saveAndFlush(new BrokerageAccount(owner, "Trading 212", name, "EUR")).getId();
    }

    private InstrumentDetailResponse detail(int page, int size) {
        return instruments.getInstrumentByBrokerageAccount(user.getId(), accountId, KEY, PageRequest.of(page, size));
    }

    private MockMultipartFile file(String rows) {
        return new MockMultipartFile("file", "review.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8));
    }

    private String row(String action, String id, String shares, String result, String currency, int day) {
        return String.join(",", action, "2026-10-%02d 10:00:00+00:00".formatted(day), "US67066G1040", "NVDA", "NVIDIA",
                "Broker note", id, shares, "100", "USD", "1.1", result, currency, "100", "EUR", "0.25", "EUR") + "\n";
    }
}
