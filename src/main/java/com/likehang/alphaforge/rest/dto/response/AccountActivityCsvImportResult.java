package com.likehang.alphaforge.rest.dto.response;

import com.likehang.alphaforge.model.entity.ImportBatchStatus;

import java.util.List;
import java.util.UUID;

public record AccountActivityCsvImportResult(
        UUID importBatchId,
        UUID brokerageAccountId,
        String originalFilename,
        String fileHash,
        ImportBatchStatus status,
        int totalRows,
        int successRows,
        int failedRows,
        List<CsvImportRowError> errors
) {
}
