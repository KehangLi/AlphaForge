package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.response.ImportBatchPageResponse;
import com.likehang.alphaforge.rest.dto.response.ImportBatchResponse;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ImportBatchServiceTest {

    private final ImportBatchRepository importBatchRepository = mock(ImportBatchRepository.class);
    private final AccountActivityRepository accountActivityRepository = mock(AccountActivityRepository.class);
    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private ImportBatchService service;

    @BeforeEach
    void setUp() {
        service = new ImportBatchService(
                importBatchRepository,
                accountActivityRepository,
                brokerageAccountRepository
        );
    }

    @Test
    void getsImportBatchesForBrokerageAccountOwnedByUser() {
        Pageable pageable = PageRequest.of(0, 50);
        BrokerageAccount brokerageAccount = brokerageAccount();
        ImportBatch importBatch = importBatch(brokerageAccount);

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        when(importBatchRepository.findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByCreatedAtDesc(
                userId,
                brokerageAccountId,
                pageable
        )).thenReturn(new PageImpl<>(List.of(importBatch), pageable, 1));

        ImportBatchPageResponse result = service.getImportBatchesByBrokerageAccount(
                userId,
                brokerageAccountId,
                pageable
        );

        assertThat(result.brokerageAccountId()).isEqualTo(brokerageAccountId);
        assertThat(result.totalElements()).isEqualTo(1);
        assertThat(result.importBatches()).singleElement()
                .satisfies(this::assertImportBatch);
    }

    @Test
    void rejectsBrokerageAccountThatIsNotOwnedByUserWhenListingBatches() {
        Pageable pageable = PageRequest.of(0, 50);

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getImportBatchesByBrokerageAccount(
                userId,
                brokerageAccountId,
                pageable
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Brokerage account not found")
                .hasMessageContaining(brokerageAccountId.toString());

        verify(importBatchRepository, never())
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByCreatedAtDesc(
                        userId,
                        brokerageAccountId,
                        pageable
                );
    }

    @Test
    void deletesImportBatchAndItsAccountActivities() {
        BrokerageAccount brokerageAccount = brokerageAccount();
        ImportBatch importBatch = importBatch(brokerageAccount);

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        when(importBatchRepository.findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndId(
                userId,
                brokerageAccountId,
                importBatchId
        )).thenReturn(Optional.of(importBatch));
        when(accountActivityRepository.deleteByImportBatch_Id(importBatchId)).thenReturn(3L);

        service.deleteImportBatch(userId, brokerageAccountId, importBatchId);

        InOrder inOrder = inOrder(accountActivityRepository, importBatchRepository);
        inOrder.verify(accountActivityRepository).deleteByImportBatch_Id(importBatchId);
        inOrder.verify(importBatchRepository).delete(importBatch);
    }

    @Test
    void rejectsMissingImportBatchWhenDeleting() {
        BrokerageAccount brokerageAccount = brokerageAccount();

        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        when(importBatchRepository.findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndId(
                userId,
                brokerageAccountId,
                importBatchId
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.deleteImportBatch(userId, brokerageAccountId, importBatchId))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("Import batch not found")
                .hasMessageContaining(importBatchId.toString());

        verify(accountActivityRepository, never()).deleteByImportBatch_Id(importBatchId);
        verify(importBatchRepository, never()).delete(any(ImportBatch.class));
    }

    private BrokerageAccount brokerageAccount() {
        AppUser appUser = new AppUser("dev@alphaforge.local", "Local Dev User");
        appUser.setId(userId);

        BrokerageAccount brokerageAccount = new BrokerageAccount(
                appUser,
                "Trading 212",
                "Main Account",
                "USD"
        );
        brokerageAccount.setId(brokerageAccountId);
        return brokerageAccount;
    }

    private ImportBatch importBatch(BrokerageAccount brokerageAccount) {
        ImportBatch importBatch = new ImportBatch(brokerageAccount, "activity.csv");
        importBatch.setId(importBatchId);
        importBatch.setFileHash("a".repeat(64));
        importBatch.setStatus(ImportBatchStatus.COMPLETED);
        importBatch.setTotalRows(10);
        importBatch.setSuccessRows(9);
        importBatch.setFailedRows(1);
        importBatch.setStartedAt(Instant.parse("2026-07-01T10:15:30Z"));
        importBatch.setCompletedAt(Instant.parse("2026-07-01T10:15:40Z"));
        importBatch.setCreatedAt(Instant.parse("2026-07-01T10:15:30Z"));
        importBatch.setUpdatedAt(Instant.parse("2026-07-01T10:15:40Z"));
        return importBatch;
    }

    private void assertImportBatch(ImportBatchResponse importBatch) {
        assertThat(importBatch.id()).isEqualTo(importBatchId);
        assertThat(importBatch.brokerageAccountId()).isEqualTo(brokerageAccountId);
        assertThat(importBatch.originalFilename()).isEqualTo("activity.csv");
        assertThat(importBatch.fileHash()).isEqualTo("a".repeat(64));
        assertThat(importBatch.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(importBatch.totalRows()).isEqualTo(10);
        assertThat(importBatch.successRows()).isEqualTo(9);
        assertThat(importBatch.failedRows()).isEqualTo(1);
        assertThat(importBatch.startedAt()).isEqualTo(Instant.parse("2026-07-01T10:15:30Z"));
        assertThat(importBatch.completedAt()).isEqualTo(Instant.parse("2026-07-01T10:15:40Z"));
    }
}
