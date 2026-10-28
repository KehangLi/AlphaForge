package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ResourceQuotaException;
import com.likehang.alphaforge.exception.UploadTooLargeException;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.AppUserType;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import com.likehang.alphaforge.rest.dto.request.BrokerageAccountCreateRequest;
import com.likehang.alphaforge.support.AbstractPostgresIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(properties = {
        "alphaforge.guest-quotas.max-import-batches=2",
        "alphaforge.guest-quotas.max-activities=3",
        "alphaforge.guest-quotas.max-file-size=256B",
        "alphaforge.guest-quotas.max-rows=2"
})
class GuestQuotaIntegrationTest extends AbstractPostgresIntegrationTest {

    @Autowired AppUserRepository users;
    @Autowired BrokerageAccountRepository accounts;
    @Autowired ImportBatchRepository batches;
    @Autowired AccountActivityRepository activities;
    @Autowired BrokerageAccountService accountService;
    @Autowired AccountActivityCsvImportService imports;
    @Autowired ImportBatchService importBatches;

    @Test
    void concurrentAccountCreationsCannotExceedTheGuestLimit() throws Exception {
        AppUser guest = user(AppUserType.GUEST);
        UUID existing = account(guest, "Existing");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> createAfter(start, guest, "First"));
            var second = executor.submit(() -> createAfter(start, guest, "Second"));
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
        assertThat(accounts.countByUser_Id(guest.getId())).isEqualTo(2);
        accountService.deleteBrokerageAccount(guest.getId(), existing);
        account(guest, "Replacement");
        assertThat(accounts.countByUser_Id(guest.getId())).isEqualTo(2);
    }

    @Test
    void concurrentImportsAcrossAccountsShareOneActivityQuotaAndRollBackRejectedBatch() throws Exception {
        AppUser guest = user(AppUserType.GUEST);
        UUID firstAccount = account(guest, "First");
        UUID secondAccount = account(guest, "Second");
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var first = executor.submit(() -> importAfter(start, guest, firstAccount));
            var second = executor.submit(() -> importAfter(start, guest, secondAccount));
            start.countDown();
            assertThat(first.get() + second.get()).isEqualTo(1);
        }
        assertThat(activities.countByBrokerageAccount_User_Id(guest.getId())).isEqualTo(2);
        assertThat(batches.countByBrokerageAccount_User_Id(guest.getId())).isEqualTo(1);
    }

    @Test
    void failedBatchesCountAcrossAccountsAndDeletingABatchReleasesSpace() {
        AppUser guest = user(AppUserType.GUEST);
        UUID first = account(guest, "First");
        UUID second = account(guest, "Second");
        var failed = imports.importCsv(guest.getId(), first, file("Wrong header\nvalue\n"));
        assertThat(failed.status()).isEqualTo(ImportBatchStatus.FAILED);
        imports.importCsv(guest.getId(), second, file(csv(1, "1")));
        assertThatThrownBy(() -> imports.importCsv(guest.getId(), first, file(csv(1, "2"))))
                .isInstanceOf(ResourceQuotaException.class);
        assertThat(batches.count()).isEqualTo(2);
        importBatches.deleteImportBatch(guest.getId(), first, failed.importBatchId());
        imports.importCsv(guest.getId(), first, file(csv(1, "2")));
        assertThat(batches.count()).isEqualTo(2);
    }

    @Test
    void fileAndRowLimitsRejectBeforeAnyDataIsCommitted() {
        AppUser guest = user(AppUserType.GUEST);
        UUID account = account(guest, "First");
        assertThatThrownBy(() -> imports.importCsv(guest.getId(), account,
                new MockMultipartFile("file", "large.csv", "text/csv", new byte[257])))
                .isInstanceOf(UploadTooLargeException.class);
        assertThatThrownBy(() -> imports.importCsv(guest.getId(), account, file(csv(3, "1"))))
                .isInstanceOf(UploadTooLargeException.class);
        assertThat(batches.count()).isZero();
        assertThat(activities.count()).isZero();
    }

    @Test
    void registeredUsersDoNotInheritGuestResourceLimitsAndOtherGuestsAreIndependent() {
        AppUser guest = user(AppUserType.GUEST);
        account(guest, "First");
        account(guest, "Second");
        assertThatThrownBy(() -> account(guest, "Third")).isInstanceOf(ResourceQuotaException.class);
        AppUser registered = user(AppUserType.REGISTERED);
        account(registered, "First");
        account(registered, "Second");
        UUID third = account(registered, "Third");
        assertThat(imports.importCsv(registered.getId(), third, file(csv(4, "1"))).successRows()).isEqualTo(4);
        account(user(AppUserType.GUEST), "Independent");
    }

    private int createAfter(CountDownLatch start, AppUser guest, String name) throws InterruptedException {
        start.await();
        try { account(guest, name); return 1; }
        catch (ResourceQuotaException exception) { return 0; }
    }

    private int importAfter(CountDownLatch start, AppUser guest, UUID account) throws InterruptedException {
        start.await();
        try { imports.importCsv(guest.getId(), account, file(csv(2, "1"))); return 1; }
        catch (ResourceQuotaException exception) { return 0; }
    }

    private AppUser user(AppUserType type) {
        return users.saveAndFlush(new AppUser(type == AppUserType.GUEST ? null : UUID.randomUUID() + "@example.com", "Test", type));
    }

    private UUID account(AppUser user, String name) {
        return accountService.createBrokerageAccount(user.getId(),
                new BrokerageAccountCreateRequest("Trading 212", name, null, "EUR")).id();
    }

    private MockMultipartFile file(String csv) {
        return new MockMultipartFile("file", "test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8));
    }

    private String csv(int rows, String amount) {
        return "Action,Time (UTC),Total,Currency (Total)\n"
                + ("Interest on cash,2026-10-01 00:00:00+00:00," + amount + ",EUR\n").repeat(rows);
    }
}
