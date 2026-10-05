package com.likehang.alphaforge.model.dto.query;

import java.util.List;
import java.util.UUID;

public record ImportBatchPageResponse(
        UUID brokerageAccountId,
        List<ImportBatchResponse> importBatches,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
