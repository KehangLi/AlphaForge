package com.likehang.alphaforge.model.dto.csv;

import java.util.List;

//负责管理列名 csv 表格列名发生改变了 直接这里改就行

public final class AccountActivityCsvHeaders {

    public static final String ACTION = "Action";
    public static final String TIME_UTC = "Time (UTC)";
    public static final String ISIN = "ISIN";
    public static final String TICKER = "Ticker";
    public static final String NAME = "Name";
    public static final String NOTES = "Notes";
    public static final String ID = "ID";
    public static final String NUMBER_OF_SHARES = "No. of shares";
    public static final String PRICE_PER_SHARE = "Price / share";
    public static final String PRICE_PER_SHARE_CURRENCY = "Currency (Price / share)";
    public static final String EXCHANGE_RATE = "Exchange rate";
    public static final String RESULT = "Result";
    public static final String RESULT_CURRENCY = "Currency (Result)";
    public static final String TOTAL = "Total";
    public static final String TOTAL_CURRENCY = "Currency (Total)";
    public static final String WITHHOLDING_TAX = "Withholding tax";
    public static final String WITHHOLDING_TAX_CURRENCY = "Currency (Withholding tax)";
    public static final String CURRENCY_CONVERSION_FEE = "Currency conversion fee";
    public static final String CURRENCY_CONVERSION_FEE_CURRENCY = "Currency (Currency conversion fee)";
    public static final String MERCHANT_NAME = "Merchant name";
    public static final String MERCHANT_CATEGORY = "Merchant category";

    private static final List<String> ALL_HEADERS = List.of(
            ACTION,
            TIME_UTC,
            ISIN,
            TICKER,
            NAME,
            NOTES,
            ID,
            NUMBER_OF_SHARES,
            PRICE_PER_SHARE,
            PRICE_PER_SHARE_CURRENCY,
            EXCHANGE_RATE,
            RESULT,
            RESULT_CURRENCY,
            TOTAL,
            TOTAL_CURRENCY,
            WITHHOLDING_TAX,
            WITHHOLDING_TAX_CURRENCY,
            CURRENCY_CONVERSION_FEE,
            CURRENCY_CONVERSION_FEE_CURRENCY,
            MERCHANT_NAME,
            MERCHANT_CATEGORY
    );

    private static final List<String> REQUIRED_HEADERS = List.of(
            ACTION,
            TIME_UTC,
            TOTAL,
            TOTAL_CURRENCY
    );

    private AccountActivityCsvHeaders() {
    }

    public static List<String> allHeaders() {
        return ALL_HEADERS;
    }

    public static List<String> requiredHeaders() {
        return REQUIRED_HEADERS;
    }
}
