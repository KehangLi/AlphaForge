package com.likehang.alphaforge.model.dto.query;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountActivityResponse(
        UUID id,
        UUID brokerageAccountId,
        UUID importBatchId,
        int rowNumber,
        String externalTransactionId,
        String action,
        OffsetDateTime occurredAt,
        String isin,
        String ticker,
        String instrumentName,
        String notes,
        BigDecimal numberOfShares,
        BigDecimal pricePerShare,
        String pricePerShareCurrency,
        BigDecimal exchangeRate,
        BigDecimal resultAmount,
        String resultCurrency,
        BigDecimal totalAmount,
        String totalCurrency,
        BigDecimal withholdingTax,
        String withholdingTaxCurrency,
        BigDecimal currencyConversionFee,
        String currencyConversionFeeCurrency,
        String merchantName,
        String merchantCategory
) {
}
