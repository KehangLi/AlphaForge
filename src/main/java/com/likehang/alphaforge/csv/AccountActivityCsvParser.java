package com.likehang.alphaforge.csv;

import com.likehang.alphaforge.csv.dto.AccountActivityCsvHeaders;
import com.likehang.alphaforge.csv.dto.AccountActivityCsvRow;
import com.likehang.alphaforge.exception.CsvImportException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.rest.dto.response.CsvImportRowError;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class AccountActivityCsvParser {

    private final AccountActivityCsvMapper accountActivityCsvMapper;

    public AccountActivityCsvParser(AccountActivityCsvMapper accountActivityCsvMapper) {
        this.accountActivityCsvMapper = accountActivityCsvMapper;
    }

    public AccountActivityCsvParseResult parse(
            byte[] fileBytes,
            ImportBatch importBatch,
            BrokerageAccount brokerageAccount
    ) {
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
                return new AccountActivityCsvParseResult(0, List.of(), 0, headerErrors);
            }

            List<AccountActivity> activities = new ArrayList<>();
            List<CsvImportRowError> errors = new ArrayList<>();
            int totalRows = 0;

            for (CSVRecord record : parser) {
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

            return new AccountActivityCsvParseResult(totalRows, activities, errors.size(), errors);
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

    private byte[] removeUtf8Bom(byte[] fileBytes) {
        if (fileBytes.length >= 3
                && (fileBytes[0] & 0xFF) == 0xEF
                && (fileBytes[1] & 0xFF) == 0xBB
                && (fileBytes[2] & 0xFF) == 0xBF) {
            return Arrays.copyOfRange(fileBytes, 3, fileBytes.length);
        }
        return fileBytes;
    }
}
