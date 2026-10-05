package com.likehang.alphaforge.model.dto.query;

import com.likehang.alphaforge.model.entity.ImportBatchStatus;

import java.time.Instant;
import java.util.UUID;

public record ImportBatchResponse(
        UUID id,
        UUID brokerageAccountId,
        String originalFilename,
        String fileHash,
        ImportBatchStatus status,
        int totalRows,
        int successRows,
        int failedRows,
        Instant startedAt,
        Instant completedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
