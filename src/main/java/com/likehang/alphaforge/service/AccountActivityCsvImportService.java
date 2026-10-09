package com.likehang.alphaforge.service;

import com.likehang.alphaforge.csv.AccountActivityCsvParseResult;
import com.likehang.alphaforge.csv.AccountActivityCsvParser;
import com.likehang.alphaforge.csv.CsvImportFileSupport;
import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.rest.dto.response.AccountActivityCsvImportResult;
import com.likehang.alphaforge.rest.dto.response.CsvImportRowError;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AccountActivityCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivityCsvImportService.class);

    private final BrokerageAccountRepository brokerageAccountRepository;
    private final ImportBatchRepository importBatchRepository;
    private final AccountActivityRepository accountActivityRepository;
    private final AccountActivityCsvParser accountActivityCsvParser;
    private final CsvImportFileSupport csvImportFileSupport;
    private final String defaultBrokerName;
    private final String defaultAccountName;

    public AccountActivityCsvImportService(
            BrokerageAccountRepository brokerageAccountRepository,
            ImportBatchRepository importBatchRepository,
            AccountActivityRepository accountActivityRepository,
            AccountActivityCsvParser accountActivityCsvParser,
            CsvImportFileSupport csvImportFileSupport,
            @Value("${alphaforge.dev.brokerage-account.broker-name:}") String defaultBrokerName,
            @Value("${alphaforge.dev.brokerage-account.account-name:}") String defaultAccountName
    ) {
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.importBatchRepository = importBatchRepository;
        this.accountActivityRepository = accountActivityRepository;
        this.accountActivityCsvParser = accountActivityCsvParser;
        this.csvImportFileSupport = csvImportFileSupport;
        this.defaultBrokerName = defaultBrokerName.trim();
        this.defaultAccountName = defaultAccountName.trim();
    }

    @Transactional
    public AccountActivityCsvImportResult importCsvForConfiguredDefaultAccount(UUID userId, MultipartFile file) {
        BrokerageAccount brokerageAccount = findConfiguredDefaultAccount(userId);
        return importCsvForBrokerageAccount(brokerageAccount, file);
    }

    @Transactional
    public AccountActivityCsvImportResult importCsv(UUID userId, UUID brokerageAccountId, MultipartFile file) {
        if (userId == null) {
            throw new CsvImportException("User id is required");
        }

        if (brokerageAccountId == null) {
            throw new CsvImportException("Brokerage account id is required");
        }

        BrokerageAccount brokerageAccount = brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new CsvImportException("Brokerage account not found: " + brokerageAccountId));
        return importCsvForBrokerageAccount(brokerageAccount, file);
    }

    private AccountActivityCsvImportResult importCsvForBrokerageAccount(BrokerageAccount brokerageAccount, MultipartFile file) {
        byte[] fileBytes = csvImportFileSupport.readFileBytes(file);
        String fileHash = csvImportFileSupport.sha256Hex(fileBytes);
        UUID brokerageAccountId = brokerageAccount.getId();

        importBatchRepository.findByBrokerageAccount_IdAndFileHash(brokerageAccountId, fileHash)
                .ifPresent(existingBatch -> {
                    throw new CsvImportException("CSV file was already imported as batch: " + existingBatch.getId());
                });

        ImportBatch importBatch = new ImportBatch(
                brokerageAccount,
                csvImportFileSupport.cleanOriginalFilename(file.getOriginalFilename())
        );
        importBatch.setFileHash(fileHash);
        importBatch.setStatus(ImportBatchStatus.PROCESSING);
        importBatch.setStartedAt(Instant.now());
        importBatch = importBatchRepository.save(importBatch);

        AccountActivityCsvParseResult parseResult = accountActivityCsvParser.parse(fileBytes, importBatch, brokerageAccount);
        if (!parseResult.activities().isEmpty()) {
            accountActivityRepository.saveAll(parseResult.activities());
        }

        int totalRows = parseResult.totalRows();
        int failedRows = parseResult.failedRows();
        int successRows = parseResult.activities().size();

        importBatch.setTotalRows(totalRows);
        importBatch.setSuccessRows(successRows);
        importBatch.setFailedRows(failedRows);
        importBatch.setStatus(resolveStatus(totalRows, successRows, failedRows, parseResult.errors()));
        importBatch.setCompletedAt(Instant.now());
        importBatch = importBatchRepository.save(importBatch);

        log.info(
                "CSV import completed: batch={}, account={}, status={}, totalRows={}, successRows={}, failedRows={}",
                importBatch.getId(),
                brokerageAccountId,
                importBatch.getStatus(),
                totalRows,
                successRows,
                failedRows
        );

        return toResult(importBatch, brokerageAccountId, parseResult.errors());
    }

    private BrokerageAccount findConfiguredDefaultAccount(UUID userId) {
        if (userId == null) {
            throw new CsvImportException("User id is required");
        }

        if (defaultBrokerName.isBlank() || defaultAccountName.isBlank()) {
            throw new CsvImportException("Default import account is not configured");
        }

        return brokerageAccountRepository
                .findByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                        userId,
                        defaultBrokerName,
                        defaultAccountName
                )
                .orElseThrow(() -> new CsvImportException("Default brokerage account not found"));
    }

    private ImportBatchStatus resolveStatus(
            int totalRows,
            int successRows,
            int failedRows,
            List<CsvImportRowError> errors
    ) {
        if (successRows == 0 && failedRows > 0) {
            return ImportBatchStatus.FAILED;
        }
        if (totalRows == 0 && !errors.isEmpty()) {
            return ImportBatchStatus.FAILED;
        }
        if (failedRows > 0) {
            return ImportBatchStatus.COMPLETED_WITH_ERRORS;
        }
        return ImportBatchStatus.COMPLETED;
    }

    private AccountActivityCsvImportResult toResult(
            ImportBatch importBatch,
            UUID brokerageAccountId,
            List<CsvImportRowError> errors
    ) {
        return new AccountActivityCsvImportResult(
                importBatch.getId(),
                brokerageAccountId,
                importBatch.getOriginalFilename(),
                importBatch.getFileHash(),
                importBatch.getStatus(),
                importBatch.getTotalRows(),
                importBatch.getSuccessRows(),
                importBatch.getFailedRows(),
                List.copyOf(errors)
        );
    }
}
