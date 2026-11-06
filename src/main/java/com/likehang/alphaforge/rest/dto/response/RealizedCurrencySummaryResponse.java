package com.likehang.alphaforge.rest.dto.response;

import java.math.BigDecimal;

public record RealizedCurrencySummaryResponse(
        String currency,
        BigDecimal totalResult,
        BigDecimal totalProfit,
        BigDecimal totalLoss,
        long profitableSellCount,
        long losingSellCount,
        long breakevenSellCount
) {
}
