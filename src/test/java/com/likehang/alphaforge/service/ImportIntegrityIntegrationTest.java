package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import com.likehang.alphaforge.rest.dto.response.AccountActivityCsvImportResult;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ImportIntegrityIntegrationTest extends AbstractPostgresIntegrationTest {

    private static final String HEADER = "Action,Time (UTC),ISIN,Ticker,ID,No. of shares,Total,Currency (Total)\n";
    private static final String BUY = "Limit buy,2026-10-01 10:00:00+00:00,US67066G1040,NVDA,BUY-1,2,300,EUR\n";
    private static final String SELL = "Limit sell,2026-10-02 10:00:00+00:00,US67066G1040,NVDA,SELL-1,1,160,EUR\n";

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired ImportBatchRepository batches;
    @Autowired AccountActivityRepository activities;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired ImportBatchService batchService;
    @Autowired BrokerageAccountService accountService;
    @Autowired JdbcTemplate jdbc;

    private UUID userId;
    private UUID accountId;

    @BeforeEach
    void setUp() {
        AppUser user = users.saveAndFlush(new AppUser("integrity@example.com", "Integrity test"));
        userId = user.getId();
        accountId = accounts.saveAndFlush(new BrokerageAccount(user, "Trading 212", "Main", "EUR")).getId();
    }

    @Test
    void overlappingExportRollsBackTheWholeBatchIncludingNewRows() {
        upload(accountId, BUY);
        assertThatThrownBy(() -> upload(accountId, SELL + BUY))
                .isInstanceOf(ConflictException.class).hasMessageContaining("CSV row 3");
        assertThat(batches.count()).isEqualTo(1);
        assertThat(activities.count()).isEqualTo(1);
    }

    @Test
    void repeatedIdWithChangedContentIsAConflictAndNeverOverwrites() {
        upload(accountId, BUY);
        assertThatThrownBy(() -> upload(accountId, BUY.replace(",300,", ",301,")))
                .isInstanceOf(ConflictException.class).hasMessageContaining("Conflicting transaction ID");
        assertThat(activities.findByBrokerageAccount_Id(accountId)).singleElement()
                .satisfies(activity -> assertThat(activity.getTotalAmount()).isEqualByComparingTo("300"));
        assertThat(batches.count()).isEqualTo(1);
    }

    @Test
    void noIdDividendOverlapAndInFileDuplicatesAreRejected() {
        String dividend = "Dividend (Dividend),2026-10-03 10:00:00+00:00,US67066G1040,NVDA,,2,1,EUR\n";
        upload(accountId, dividend);
        assertThatThrownBy(() -> upload(accountId, dividend + "\n"))
                .isInstanceOf(ConflictException.class).hasMessageContaining("without a transaction ID");
        assertThatThrownBy(() -> upload(accountId, BUY + BUY))
                .isInstanceOf(ConflictException.class).hasMessageContaining("same CSV file");
        assertThat(batches.count()).isEqualTo(1);
        assertThat(activities.count()).isEqualTo(1);
    }

    @Test
    void distinctIdsAtTheSameTimestampAndTheSameIdInAnotherAccountAreAllowed() {
        upload(accountId, BUY + BUY.replace("BUY-1", "BUY-2"));
        UUID otherAccount = accounts.saveAndFlush(new BrokerageAccount(
                users.findById(userId).orElseThrow(), "Trading 212", "Other", "EUR")).getId();
        upload(otherAccount, BUY);
        assertThat(activities.count()).isEqualTo(3);
    }

    @Test
    void deletionUsesImportOrderEvenForBackfilledHistoryAndAcrossPages() {
        var first = upload(accountId, BUY);
        var sell = upload(accountId, SELL);
        var backfill = upload(accountId, BUY.replace("2026-10-01", "2026-09-01").replace("BUY-1", "BUY-OLD"));
        // Timestamps can tie; the database-generated sequence remains authoritative.
        jdbc.update("update import_batch set created_at = '2026-10-29T00:00:00Z'");
        var newestPage = batchService.getImportBatchesByBrokerageAccount(userId, accountId, PageRequest.of(0, 1));
        assertThat(newestPage.importBatches()).singleElement().satisfies(batch -> {
            assertThat(batch.id()).isEqualTo(backfill.importBatchId());
            assertThat(batch.deletable()).isTrue();
        });
        var olderPage = batchService.getImportBatchesByBrokerageAccount(userId, accountId, PageRequest.of(1, 1));
        assertThat(olderPage.importBatches()).singleElement().satisfies(batch -> assertThat(batch.deletable()).isFalse());
        assertThatThrownBy(() -> batchService.deleteImportBatch(userId, accountId, first.importBatchId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("latest");
        assertThat(activities.count()).isEqualTo(3);
        batchService.deleteImportBatch(userId, accountId, backfill.importBatchId());
        batchService.deleteImportBatch(userId, accountId, sell.importBatchId());
        batchService.deleteImportBatch(userId, accountId, first.importBatchId());
        assertThat(batches.count()).isZero();
        assertThat(activities.count()).isZero();
    }

    @Test
    void rejectsDeletingLegacyBackfillThatWouldLeaveASellWithoutItsBuy() {
        var first = upload(accountId, BUY);
        var latest = upload(accountId, SELL);
        // Simulate legacy data whose last import supplied missing buy history.
        jdbc.update("update account_activity set import_batch_id = ? where external_transaction_id = 'BUY-1'", latest.importBatchId());
        jdbc.update("update account_activity set import_batch_id = ? where external_transaction_id = 'SELL-1'", first.importBatchId());
        assertThatThrownBy(() -> batchService.deleteImportBatch(userId, accountId, latest.importBatchId()))
                .isInstanceOf(ConflictException.class).hasMessageContaining("invalid holdings");
        assertThat(activities.count()).isEqualTo(2);
        assertThat(batches.count()).isEqualTo(2);
    }

    @Test
    void sameInstantAcrossTimezonesRetainsPriorBatchOrderBeforeFileRowOrder() {
        upload(accountId, "Interest on cash,2026-09-01 10:00:00+00:00,,,CASH-1,,1,EUR\n" + BUY);
        // The existing buy is row 3; the later-imported sell is row 2 at the same instant.
        upload(accountId, SELL.replace("2026-10-02 10:00:00+00:00", "2026-10-01 12:00:00+02:00"));
        assertThat(activities.count()).isEqualTo(3);
    }

    @Test
    void concurrentOverlappingImportsCanOnlyCommitOnceForRegisteredUsers() throws Exception {
        int committed = race(() -> acceptOverlap(BUY), () -> acceptOverlap(BUY + "\n"));
        assertThat(committed).isEqualTo(1);
        assertThat(batches.count()).isEqualTo(1);
        assertThat(activities.count()).isEqualTo(1);
    }

    @Test
    void concurrentSellImportAndBuyBatchDeletionCannotLeaveAnOrphanSell() throws Exception {
        var buy = upload(accountId, BUY);
        int committed = race(() -> {
            try { upload(accountId, SELL); return 1; }
            catch (BadRequestException exception) { return 0; }
        }, () -> {
            try { batchService.deleteImportBatch(userId, accountId, buy.importBatchId()); return 1; }
            catch (ConflictException exception) { return 0; }
        });
        assertThat(committed).isEqualTo(1);
        long remaining = activities.count();
        assertThat(remaining).isIn(0L, 2L);
        assertThat(batches.count()).isEqualTo(remaining);
    }

    @Test
    void concurrentAccountDeletionAndImportFinishWithoutForeignKeyErrors() throws Exception {
        race(() -> {
            try { upload(accountId, BUY); return 1; }
            catch (CsvImportException exception) { return 0; }
        }, () -> { accountService.deleteBrokerageAccount(userId, accountId); return 1; });
        assertThat(accounts.existsById(accountId)).isFalse();
        assertThat(batches.count()).isZero();
        assertThat(activities.count()).isZero();
    }

    @Test
    void databaseConstraintsAlsoProtectDirectWrites() {
        var batch = upload(accountId, BUY);
        assertThatThrownBy(() -> jdbc.update("""
                insert into account_activity (id, import_batch_id, brokerage_account_id, row_number,
                    external_transaction_id, action, occurred_at, total_amount, total_currency, created_at)
                values (?, ?, ?, 3, ' BUY-1 ', 'Limit buy', now(), 300, 'EUR', now())
                """, UUID.randomUUID(), batch.importBatchId(), accountId))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("""
                insert into import_batch (id, brokerage_account_id, original_filename, file_hash, status,
                    total_rows, success_rows, failed_rows, created_at, updated_at)
                values (?, ?, 'duplicate.csv', ?, 'COMPLETED', 1, 1, 0, now(), now())
                """, UUID.randomUUID(), accountId, batch.fileHash()))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private int acceptOverlap(String rows) {
        try { upload(accountId, rows); return 1; }
        catch (ConflictException exception) { return 0; }
    }

    private AccountActivityCsvImportResult upload(UUID targetAccount, String rows) {
        return imports.importCsv(userId, targetAccount, new MockMultipartFile(
                "file", "activities.csv", "text/csv", (HEADER + rows).getBytes(StandardCharsets.UTF_8)));
    }

    private int race(Callable<Integer> first, Callable<Integer> second) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var a = executor.submit(() -> { start.await(); return first.call(); });
            var b = executor.submit(() -> { start.await(); return second.call(); });
            start.countDown();
            return a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS);
        }
    }
}
