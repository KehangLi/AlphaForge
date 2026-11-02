package com.likehang.alphaforge.rest.dto.response;

import com.likehang.alphaforge.model.holding.HoldingStatus;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HoldingResponse(
        String instrumentKey,
        String isin,
        String ticker,
        String instrumentName,
        BigDecimal quantity,
        BigDecimal totalBoughtQuantity,
        BigDecimal totalSoldQuantity,
        OffsetDateTime lastTradedAt,
        HoldingStatus status
) {
}
