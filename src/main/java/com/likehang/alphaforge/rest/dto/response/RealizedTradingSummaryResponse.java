package com.likehang.alphaforge.rest.dto.response;

import java.util.List;

public record RealizedTradingSummaryResponse(
        long sellRecordCount,
        long includedSellRecordCount,
        long excludedSellRecordCount,
        long missingResultCount,
        long missingResultCurrencyCount,
        List<RealizedCurrencySummaryResponse> currencies
) {
}
