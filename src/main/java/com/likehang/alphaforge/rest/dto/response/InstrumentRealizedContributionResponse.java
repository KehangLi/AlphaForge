package com.likehang.alphaforge.rest.dto.response;

public record InstrumentRealizedContributionResponse(
        String instrumentKey,
        String isin,
        String ticker,
        String instrumentName,
        String currency,
        RealizedTradingSummaryResponse realizedSummary
) {
}
