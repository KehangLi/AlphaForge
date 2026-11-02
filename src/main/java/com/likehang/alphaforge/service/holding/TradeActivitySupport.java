package com.likehang.alphaforge.service.holding;

import com.likehang.alphaforge.exception.BadRequestException;

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
        String normalizedAction = normalizeIdentifier(action);
        if (normalizedAction == null) {
            return null;
        }
        boolean isBuy = normalizedAction.contains("BUY");
        boolean isSell = normalizedAction.contains("SELL");
        if (!isBuy && !isSell) {
            return null;
        }
        if (shares == null) {
            throw new BadRequestException("Trade row is missing number of shares: row " + rowNumber);
        }
        BigDecimal quantity = shares.setScale(10, RoundingMode.HALF_UP).abs();
        if (quantity.signum() == 0) {
            return null;
        }
        return isSell ? quantity.negate() : quantity;
    }

    public static String normalizeIdentifier(String value) {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase(Locale.ROOT);
    }
}

/*
 If a method only depends on its input parameters and doesn't use instance state,
 it's a good candidate to be declared static.
*/