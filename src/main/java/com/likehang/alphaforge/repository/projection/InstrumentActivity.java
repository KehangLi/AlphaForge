package com.likehang.alphaforge.repository.projection;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record InstrumentActivity(
        String action,
        String isin,
        String ticker,
        String instrumentName,
        BigDecimal numberOfShares,
        OffsetDateTime occurredAt,
        int rowNumber,
        BigDecimal resultAmount,
        String resultCurrency
) {
    public HoldingActivity toHoldingActivity() {
        return new HoldingActivity(action, isin, ticker, instrumentName, numberOfShares, occurredAt, rowNumber);
    }
}

/*
   The projection between Repository and Service.
   Reducing the amount of data the database query returns to the service.
*/
