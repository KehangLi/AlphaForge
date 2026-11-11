package com.likehang.alphaforge.service.holding;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.model.activity.AccountActivityAction;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;

public final class TradeActivitySupport {

    private TradeActivitySupport() {
    }

    public static String instrumentKey(String isin, String ticker) {
        String normalizedIsin = normalizeIdentifier(isin);
        if (normalizedIsin != null) {
            return "ISIN:" + normalizedIsin;
        }
        String normalizedTicker = normalizeIdentifier(ticker);
        return normalizedTicker == null ? null : "TICKER:" + normalizedTicker;
    }

    public static BigDecimal shareChange(String action, BigDecimal shares, int rowNumber) {
        AccountActivityAction activityAction = AccountActivityAction.fromCsvValue(action)
                .orElseThrow(() -> new BadRequestException("Unsupported or missing Action at CSV row " + rowNumber));
        if (!activityAction.isTrade()) {
            return null;
        }
        if (shares == null) {
            throw new BadRequestException("Trade row is missing number of shares: row " + rowNumber);
        }
        BigDecimal quantity = shares.setScale(10, RoundingMode.HALF_UP).abs();
        if (quantity.signum() == 0) {
            return null;
        }
        return activityAction.isSell() ? quantity.negate() : quantity;
    }

    public static String requiredInstrumentKey(String isin, String ticker, int rowNumber) {
        String key = instrumentKey(isin, ticker);
        if (key == null) {
            throw new BadRequestException("Trade row is missing ISIN and ticker at CSV row " + rowNumber
                    + ". Correct the trading history before calculating holdings.");
        }
        return key;
    }

    public static String normalizeIdentifier(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }

    public static String normalizeInstrumentKey(String value) {
        if (value == null || !value.contains(":")) {
            throw new BadRequestException("Instrument key must use ISIN:<value> or TICKER:<value>");
        }
        int separator = value.indexOf(':');
        String type = normalizeIdentifier(value.substring(0, separator));
        String identifier = normalizeIdentifier(value.substring(separator + 1));
        int maxLength = switch (type == null ? "" : type) {
            case "ISIN" -> 12;
            case "TICKER" -> 40;
            default -> throw new BadRequestException("Instrument key must use ISIN:<value> or TICKER:<value>");
        };
        if (identifier == null || identifier.length() > maxLength) {
            throw new BadRequestException("Instrument identifier must contain 1-" + maxLength + " characters");
        }
        return type + ":" + identifier;
    }
}

/*
 If a method only depends on its input parameters and doesn't use instance state,
 it's a good candidate to be declared static.
*/
