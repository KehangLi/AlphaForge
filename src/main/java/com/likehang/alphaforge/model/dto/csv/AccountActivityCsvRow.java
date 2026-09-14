package com.likehang.alphaforge.model.dto.csv;

import java.util.Map;

// record 是一个基础类 用来读取每一行

public record AccountActivityCsvRow(
        String action,
        String timeUtc,
        String isin,
        String ticker,
        String name,
        String notes,
        String id,
        String numberOfShares,
        String pricePerShare,
        String pricePerShareCurrency,
        String exchangeRate,
        String result,
        String resultCurrency,
        String total,
        String totalCurrency,
        String withholdingTax,
        String withholdingTaxCurrency,
        String currencyConversionFee,
        String currencyConversionFeeCurrency,
        String merchantName,
        String merchantCategory
) {

    public static AccountActivityCsvRow fromColumns(Map<String, String> columns) {
        return new AccountActivityCsvRow(
                columns.get(AccountActivityCsvHeaders.ACTION),
                columns.get(AccountActivityCsvHeaders.TIME_UTC),
                columns.get(AccountActivityCsvHeaders.ISIN),
                columns.get(AccountActivityCsvHeaders.TICKER),
                columns.get(AccountActivityCsvHeaders.NAME),
                columns.get(AccountActivityCsvHeaders.NOTES),
                columns.get(AccountActivityCsvHeaders.ID),
                columns.get(AccountActivityCsvHeaders.NUMBER_OF_SHARES),
                columns.get(AccountActivityCsvHeaders.PRICE_PER_SHARE),
                columns.get(AccountActivityCsvHeaders.PRICE_PER_SHARE_CURRENCY),
                columns.get(AccountActivityCsvHeaders.EXCHANGE_RATE),
                columns.get(AccountActivityCsvHeaders.RESULT),
                columns.get(AccountActivityCsvHeaders.RESULT_CURRENCY),
                columns.get(AccountActivityCsvHeaders.TOTAL),
                columns.get(AccountActivityCsvHeaders.TOTAL_CURRENCY),
                columns.get(AccountActivityCsvHeaders.WITHHOLDING_TAX),
                columns.get(AccountActivityCsvHeaders.WITHHOLDING_TAX_CURRENCY),
                columns.get(AccountActivityCsvHeaders.CURRENCY_CONVERSION_FEE),
                columns.get(AccountActivityCsvHeaders.CURRENCY_CONVERSION_FEE_CURRENCY),
                columns.get(AccountActivityCsvHeaders.MERCHANT_NAME),
                columns.get(AccountActivityCsvHeaders.MERCHANT_CATEGORY)
        );
    }
}
