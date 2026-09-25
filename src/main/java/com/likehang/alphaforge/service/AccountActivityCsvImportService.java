package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvHeaders;
import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvImportResult;
import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvRow;
import com.likehang.alphaforge.model.dto.csv.CsvImportRowError;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import com.likehang.alphaforge.model.mapper.AccountActivityCsvMapper;
import com.likehang.alphaforge.model.mapper.CsvRowMappingException;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountActivityCsvImportService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivityCsvImportService.class);
    private static final String DEFAULT_FILENAME = "account-activity.csv";
    private static final int MAX_FILENAME_LENGTH = 255;

    private final BrokerageAccountRepository brokerageAccountRepository;
    private final ImportBatchRepository importBatchRepository;
    private final AccountActivityRepository accountActivityRepository;
    private final AccountActivityCsvMapper accountActivityCsvMapper;
    private final String defaultBrokerName;
    private final String defaultAccountName;

    public AccountActivityCsvImportService(
            BrokerageAccountRepository brokerageAccountRepository,
            ImportBatchRepository importBatchRepository,
            AccountActivityRepository accountActivityRepository,
            AccountActivityCsvMapper accountActivityCsvMapper,
            @Value("${alphaforge.dev.brokerage-account.broker-name:}") String defaultBrokerName,
            @Value("${alphaforge.dev.brokerage-account.account-name:}") String defaultAccountName
    ) {
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.importBatchRepository = importBatchRepository;
        this.accountActivityRepository = accountActivityRepository;
        this.accountActivityCsvMapper = accountActivityCsvMapper;
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
        byte[] fileBytes = readFileBytes(file);
        String fileHash = sha256Hex(fileBytes);
        UUID brokerageAccountId = brokerageAccount.getId();

        importBatchRepository.findByBrokerageAccount_IdAndFileHash(brokerageAccountId, fileHash)
                .ifPresent(existingBatch -> {
                    throw new CsvImportException("CSV file was already imported as batch: " + existingBatch.getId());
                });

        ImportBatch importBatch = new ImportBatch(brokerageAccount, cleanOriginalFilename(file.getOriginalFilename()));
        importBatch.setFileHash(fileHash);
        importBatch.setStatus(ImportBatchStatus.PROCESSING);
        importBatch.setStartedAt(Instant.now());
        importBatch = importBatchRepository.save(importBatch);

        CsvParseResult parseResult = parseCsv(fileBytes, importBatch, brokerageAccount);
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

    private CsvParseResult parseCsv(byte[] fileBytes, ImportBatch importBatch, BrokerageAccount brokerageAccount) {
        CSVFormat csvFormat = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .get();

        try (
                Reader reader = new InputStreamReader(new ByteArrayInputStream(removeUtf8Bom(fileBytes)), StandardCharsets.UTF_8);
                CSVParser parser = csvFormat.parse(reader)
        ) {
            List<CsvImportRowError> headerErrors = validateRequiredHeaders(parser.getHeaderMap());
            if (!headerErrors.isEmpty()) {
                return new CsvParseResult(0, List.of(), 0, headerErrors);
            }

            List<AccountActivity> activities = new ArrayList<>();
            List<CsvImportRowError> errors = new ArrayList<>();
            int totalRows = 0;

            for (CSVRecord record : parser) {   // CSV Record 本质上可以理解为 当前这一行 + header的对应关系
                totalRows++;
                int rowNumber = Math.toIntExact(record.getRecordNumber() + 1);

                try {
                    AccountActivityCsvRow row = AccountActivityCsvRow.fromColumns(record.toMap());
                    activities.add(accountActivityCsvMapper.toEntity(row, importBatch, brokerageAccount, rowNumber));
                } catch (CsvRowMappingException exception) {
                    errors.add(new CsvImportRowError(
                            exception.getRowNumber(),
                            exception.getColumnName(),
                            exception.getReason()
                    ));
                } catch (IllegalArgumentException exception) {
                    errors.add(new CsvImportRowError(rowNumber, null, exception.getMessage()));
                }
            }

            return new CsvParseResult(totalRows, activities, errors.size(), errors);
        } catch (IOException exception) {
            throw new CsvImportException("Failed to read CSV file", exception);
        }
    }

    private List<CsvImportRowError> validateRequiredHeaders(Map<String, Integer> headerMap) {
        List<CsvImportRowError> errors = new ArrayList<>();

        for (String header : AccountActivityCsvHeaders.requiredHeaders()) {
            if (!headerMap.containsKey(header)) {
                errors.add(new CsvImportRowError(1, header, "missing required header"));
            }
        }

        return errors;
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

    private byte[] readFileBytes(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new CsvImportException("CSV file is required");
        }

        try {
            return file.getBytes();
        } catch (IOException exception) {
            throw new CsvImportException("Failed to read uploaded file", exception);
        }
    }

    private String cleanOriginalFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.trim().isEmpty()) {
            return DEFAULT_FILENAME;
        }

        String cleaned = originalFilename.trim();
        if (cleaned.length() <= MAX_FILENAME_LENGTH) {
            return cleaned;
        }
        return cleaned.substring(cleaned.length() - MAX_FILENAME_LENGTH);
    }

    private byte[] removeUtf8Bom(byte[] fileBytes) {
        if (fileBytes.length >= 3
                && (fileBytes[0] & 0xFF) == 0xEF
                && (fileBytes[1] & 0xFF) == 0xBB
                && (fileBytes[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(fileBytes, 3, fileBytes.length);
        }
        return fileBytes;
    }

    private String sha256Hex(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private record CsvParseResult(
            int totalRows,
            List<AccountActivity> activities,
            int failedRows,
            List<CsvImportRowError> errors
    ) {
    }
}
