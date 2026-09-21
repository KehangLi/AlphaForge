package com.likehang.alphaforge.model.dto.csv;

import com.likehang.alphaforge.model.entity.ImportBatchStatus;

import java.util.List;
import java.util.UUID;

// record是只装数据的类
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
