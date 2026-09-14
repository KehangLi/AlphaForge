package com.likehang.alphaforge.model.mapper;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvHeaders;
import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvRow;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.model.entity.ImportBatch;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

// 把从csv DTO来的字符串转换成正确类型，组装成Entity

@Component
public class AccountActivityCsvMapper {

    // 列表里放了两个支持的函数
    private static final List<DateTimeFormatter> TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ssXXX"),
            DateTimeFormatter.ISO_OFFSET_DATE_TIME
    );

    public AccountActivity toEntity(
            AccountActivityCsvRow row,
            ImportBatch importBatch,
            BrokerageAccount brokerageAccount,
            int rowNumber
    ) {
        AccountActivity accountActivity = new AccountActivity();
        accountActivity.setImportBatch(importBatch);
        accountActivity.setBrokerageAccount(brokerageAccount);
        accountActivity.setRowNumber(rowNumber);
        accountActivity.setExternalTransactionId(trimToNull(row.id()));
        accountActivity.setAction(requiredString(row.action(), AccountActivityCsvHeaders.ACTION, rowNumber));
        accountActivity.setOccurredAt(parseOffsetDateTime(row.timeUtc(), AccountActivityCsvHeaders.TIME_UTC, rowNumber));
        accountActivity.setIsin(trimToNull(row.isin()));
        accountActivity.setTicker(uppercaseOrNull(row.ticker()));
        accountActivity.setInstrumentName(trimToNull(row.name()));
        accountActivity.setNotes(trimToNull(row.notes()));
        accountActivity.setNumberOfShares(parseBigDecimal(row.numberOfShares(), AccountActivityCsvHeaders.NUMBER_OF_SHARES, rowNumber));
        accountActivity.setPricePerShare(parseBigDecimal(row.pricePerShare(), AccountActivityCsvHeaders.PRICE_PER_SHARE, rowNumber));
        accountActivity.setPricePerShareCurrency(uppercaseOrNull(row.pricePerShareCurrency()));
        accountActivity.setExchangeRate(parseBigDecimal(row.exchangeRate(), AccountActivityCsvHeaders.EXCHANGE_RATE, rowNumber));
        accountActivity.setResultAmount(parseBigDecimal(row.result(), AccountActivityCsvHeaders.RESULT, rowNumber));
        accountActivity.setResultCurrency(uppercaseOrNull(row.resultCurrency()));
        accountActivity.setTotalAmount(requiredBigDecimal(row.total(), AccountActivityCsvHeaders.TOTAL, rowNumber));
        accountActivity.setTotalCurrency(requiredCurrency(row.totalCurrency(), AccountActivityCsvHeaders.TOTAL_CURRENCY, rowNumber));
        accountActivity.setWithholdingTax(parseBigDecimal(row.withholdingTax(), AccountActivityCsvHeaders.WITHHOLDING_TAX, rowNumber));
        accountActivity.setWithholdingTaxCurrency(uppercaseOrNull(row.withholdingTaxCurrency()));
        accountActivity.setCurrencyConversionFee(parseBigDecimal(row.currencyConversionFee(), AccountActivityCsvHeaders.CURRENCY_CONVERSION_FEE, rowNumber));
        accountActivity.setCurrencyConversionFeeCurrency(uppercaseOrNull(row.currencyConversionFeeCurrency()));
        accountActivity.setMerchantName(trimToNull(row.merchantName()));
        accountActivity.setMerchantCategory(trimToNull(row.merchantCategory()));
        accountActivity.setRawRowHash(hashRawRow(row));
        return accountActivity;
    }

    private static String requiredString(String value, String columnName, int rowNumber) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new CsvRowMappingException(rowNumber, columnName, "value is required");
        }
        return trimmed;
    }

    private static BigDecimal requiredBigDecimal(String value, String columnName, int rowNumber) {
        BigDecimal parsed = parseBigDecimal(value, columnName, rowNumber);
        if (parsed == null) {
            throw new CsvRowMappingException(rowNumber, columnName, "value is required");
        }
        return parsed;
    }

    private static String requiredCurrency(String value, String columnName, int rowNumber) {
        String currency = uppercaseOrNull(value);
        if (currency == null) {
            throw new CsvRowMappingException(rowNumber, columnName, "value is required");
        }
        return currency;
    }

    private static OffsetDateTime parseOffsetDateTime(String value, String columnName, int rowNumber) {
        String trimmed = requiredString(value, columnName, rowNumber);

        for (DateTimeFormatter formatter : TIME_FORMATTERS) {
            try {
                return OffsetDateTime.parse(trimmed, formatter);
            } catch (DateTimeParseException ignored) {
            }
        }

        throw new CsvRowMappingException(rowNumber, columnName, "invalid timestamp: " + trimmed);
    }

    private static BigDecimal parseBigDecimal(String value, String columnName, int rowNumber) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }

        try {
            return new BigDecimal(trimmed.replace(",", ""));
        } catch (NumberFormatException exception) {
            throw new CsvRowMappingException(rowNumber, columnName, "invalid decimal: " + trimmed, exception);
        }
    }

    private static String uppercaseOrNull(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }

    // 判断这行是否被导入过 排查重复导入
    private static String hashRawRow(AccountActivityCsvRow row) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(canonicalRawRow(row).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    // 这行的字端按照固定顺序拼接成一个字符串
    private static String canonicalRawRow(AccountActivityCsvRow row) {
        return String.join(
                "\u001F",
                nullToEmpty(row.action()),
                nullToEmpty(row.timeUtc()),
                nullToEmpty(row.isin()),
                nullToEmpty(row.ticker()),
                nullToEmpty(row.name()),
                nullToEmpty(row.notes()),
                nullToEmpty(row.id()),
                nullToEmpty(row.numberOfShares()),
                nullToEmpty(row.pricePerShare()),
                nullToEmpty(row.pricePerShareCurrency()),
                nullToEmpty(row.exchangeRate()),
                nullToEmpty(row.result()),
                nullToEmpty(row.resultCurrency()),
                nullToEmpty(row.total()),
                nullToEmpty(row.totalCurrency()),
                nullToEmpty(row.withholdingTax()),
                nullToEmpty(row.withholdingTaxCurrency()),
                nullToEmpty(row.currencyConversionFee()),
                nullToEmpty(row.currencyConversionFeeCurrency()),
                nullToEmpty(row.merchantName()),
                nullToEmpty(row.merchantCategory())
        );
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

/*
    CSV parser:
    读文件，把每一行变成 Map

    CSV DTO:
    承接这一行原始字符串

    Mapper:
    把字符串转换成正确类型，组装成 Entity

    Repository:
    把 Entity 存进数据库
 */
