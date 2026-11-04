package com.likehang.alphaforge.rest.dto.response;

import java.util.List;
import java.util.UUID;

public record InstrumentDetailResponse(
        UUID brokerageAccountId,
        String instrumentKey,
        String isin,
        String ticker,
        String instrumentName,
        HoldingResponse holding,
        List<AccountActivityResponse> activities,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
