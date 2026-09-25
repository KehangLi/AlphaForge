package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvImportResult;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.model.mapper.AccountActivityCsvMapper;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountActivityCsvImportServiceTest {

    private final BrokerageAccountRepository brokerageAccountRepository = mock(BrokerageAccountRepository.class);
    private final ImportBatchRepository importBatchRepository = mock(ImportBatchRepository.class);
    private final AccountActivityRepository accountActivityRepository = mock(AccountActivityRepository.class);
    private final AccountActivityCsvMapper accountActivityCsvMapper = new AccountActivityCsvMapper();

    private final UUID userId = UUID.fromString("10000000-0000-0000-0000-000000000001");
    private final UUID brokerageAccountId = UUID.fromString("20000000-0000-0000-0000-000000000001");
    private final UUID importBatchId = UUID.fromString("30000000-0000-0000-0000-000000000001");

    private AccountActivityCsvImportService service;
    private BrokerageAccount brokerageAccount;

    @BeforeEach
    void setUp() {
        service = new AccountActivityCsvImportService(
                brokerageAccountRepository,
                importBatchRepository,
                accountActivityRepository,
                accountActivityCsvMapper,
                "Manual CSV Upload",
                "Default Local Account"
        );

        AppUser appUser = new AppUser("dev@alphaforge.local", "Local Dev User");
        appUser.setId(userId);

        brokerageAccount = new BrokerageAccount(appUser, "Manual CSV Upload", "Default Local Account", "USD");
        brokerageAccount.setId(brokerageAccountId);

        when(importBatchRepository.findByBrokerageAccount_IdAndFileHash(eq(brokerageAccountId), anyString()))
                .thenReturn(Optional.empty());
        when(importBatchRepository.save(any(ImportBatch.class))).thenAnswer(invocation -> {
            ImportBatch importBatch = invocation.getArgument(0);
            if (importBatch.getId() == null) {
                importBatch.setId(importBatchId);
            }
            return importBatch;
        });
        when(accountActivityRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void importsCsvForBrokerageAccount() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));

        AccountActivityCsvImportResult result = service.importCsv(userId, brokerageAccountId, file(validCsv()));

        assertThat(result.importBatchId()).isEqualTo(importBatchId);
        assertThat(result.brokerageAccountId()).isEqualTo(brokerageAccountId);
        assertThat(result.originalFilename()).isEqualTo("activity.csv");
        assertThat(result.fileHash()).hasSize(64);
        assertThat(result.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(2);
        assertThat(result.failedRows()).isZero();
        assertThat(result.errors()).isEmpty();

        verify(accountActivityRepository).saveAll(any());
    }

    @Test
    void importsCsvForConfiguredDefaultAccount() {
        when(brokerageAccountRepository.findByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                userId,
                "Manual CSV Upload",
                "Default Local Account"
        )).thenReturn(Optional.of(brokerageAccount));

        AccountActivityCsvImportResult result = service.importCsvForConfiguredDefaultAccount(userId, file(validCsv()));

        assertThat(result.status()).isEqualTo(ImportBatchStatus.COMPLETED);
        assertThat(result.successRows()).isEqualTo(2);
    }

    @Test
    void completesWithErrorsWhenSomeRowsAreInvalid() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));

        AccountActivityCsvImportResult result = service.importCsv(userId, brokerageAccountId, file(csvWithInvalidRow()));

        assertThat(result.status()).isEqualTo(ImportBatchStatus.COMPLETED_WITH_ERRORS);
        assertThat(result.totalRows()).isEqualTo(2);
        assertThat(result.successRows()).isEqualTo(1);
        assertThat(result.failedRows()).isEqualTo(1);
        assertThat(result.errors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.rowNumber()).isEqualTo(3);
                    assertThat(error.columnName()).isEqualTo("Total");
                    assertThat(error.message()).contains("invalid decimal");
                });

        verify(accountActivityRepository).saveAll(any());
    }

    @Test
    void failsImportWhenRequiredHeaderIsMissing() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));

        AccountActivityCsvImportResult result = service.importCsv(userId, brokerageAccountId, file(csvWithoutTotalHeader()));

        assertThat(result.status()).isEqualTo(ImportBatchStatus.FAILED);
        assertThat(result.totalRows()).isZero();
        assertThat(result.successRows()).isZero();
        assertThat(result.failedRows()).isZero();
        assertThat(result.errors()).singleElement()
                .satisfies(error -> {
                    assertThat(error.rowNumber()).isEqualTo(1);
                    assertThat(error.columnName()).isEqualTo("Total");
                    assertThat(error.message()).isEqualTo("missing required header");
                });

        verify(accountActivityRepository, never()).saveAll(any());
    }

    @Test
    void rejectsDuplicateFileForSameBrokerageAccount() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.of(brokerageAccount));
        ImportBatch existingBatch = new ImportBatch(brokerageAccount, "activity.csv");
        existingBatch.setId(importBatchId);
        when(importBatchRepository.findByBrokerageAccount_IdAndFileHash(eq(brokerageAccountId), anyString()))
                .thenReturn(Optional.of(existingBatch));

        assertThatThrownBy(() -> service.importCsv(userId, brokerageAccountId, file(validCsv())))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("already imported")
                .hasMessageContaining(importBatchId.toString());

        verify(importBatchRepository, never()).save(any());
        verify(accountActivityRepository, never()).saveAll(any());
    }

    @Test
    void rejectsBrokerageAccountThatIsNotOwnedByUser() {
        when(brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.importCsv(userId, brokerageAccountId, file(validCsv())))
                .isInstanceOf(CsvImportException.class)
                .hasMessageContaining("Brokerage account not found")
                .hasMessageContaining(brokerageAccountId.toString());

        verify(importBatchRepository, never()).save(any());
        verify(accountActivityRepository, never()).saveAll(any());
    }

    private MockMultipartFile file(String csv) {
        return new MockMultipartFile(
                "file",
                "activity.csv",
                "text/csv",
                csv.getBytes(StandardCharsets.UTF_8)
        );
    }

    private String validCsv() {
        return String.join("\n", List.of(
                csvHeader(),
                "Interest on cash,2026-07-01 01:05:30+00:00,,,,Interest on cash,019f1b35-ac33-79d5-a7d9-994b1cfaa727,,,,,,,0.03,EUR,,,,,,",
                "Limit buy,2026-07-01 13:35:43+00:00,US21873S1087,CRWV,CoreWeave,,EOF53510842405,1.0000000000,88.4900000000,USD,1.13901403,,,77.81,EUR,,,0.12,EUR,,"
        ));
    }

    private String csvWithInvalidRow() {
        List<String> rows = new ArrayList<>();
        rows.add(csvHeader());
        rows.add("Limit buy,2026-07-01 13:35:43+00:00,US21873S1087,CRWV,CoreWeave,,EOF53510842405,1.0000000000,88.4900000000,USD,1.13901403,,,77.81,EUR,,,0.12,EUR,,");
        rows.add("Limit buy,2026-07-01 13:38:31+00:00,NL0009805522,NBIS,Nebius Group,,EOF53510845198,0.5000000000,235.5500000000,USD,1.13869283,,,not-a-number,EUR,,,0.16,EUR,,");
        return String.join("\n", rows);
    }

    private String csvWithoutTotalHeader() {
        return String.join("\n", List.of(
                "Action,Time (UTC),Currency (Total)",
                "Interest on cash,2026-07-01 01:05:30+00:00,EUR"
        ));
    }

    private String csvHeader() {
        return "Action,Time (UTC),ISIN,Ticker,Name,Notes,ID,No. of shares,Price / share,Currency (Price / share),Exchange rate,Result,Currency (Result),Total,Currency (Total),Withholding tax,Currency (Withholding tax),Currency conversion fee,Currency (Currency conversion fee),Merchant name,Merchant category";
    }
}

// The Process of Test: Arrange -> Act -> Assert -> Verify
