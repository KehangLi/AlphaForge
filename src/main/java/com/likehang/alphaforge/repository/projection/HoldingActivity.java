package com.likehang.alphaforge.repository.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record HoldingActivity(
        String action,
        String isin,
        String ticker,
        String instrumentName,
        BigDecimal numberOfShares,
        OffsetDateTime occurredAt,
        int rowNumber
) {
}
