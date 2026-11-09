package com.likehang.alphaforge.rest.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record AccountRealizedTradingSummaryResponse(
        UUID brokerageAccountId,
        LocalDate from,
        LocalDate to,
        String currency,
        RealizedTradingSummaryResponse realizedSummary,
        long unidentifiedSellRecordCount,
        List<InstrumentRealizedContributionResponse> contributions,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {
}
