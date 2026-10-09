package com.likehang.alphaforge.rest.dto.response;

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
