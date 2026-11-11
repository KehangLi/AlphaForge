package com.likehang.alphaforge.model.activity;

import java.util.Arrays;
import java.util.Optional;

public enum AccountActivityAction {

    MARKET_BUY("Market buy", 1),
    MARKET_SELL("Market sell", -1),
    LIMIT_BUY("Limit buy", 1),
    LIMIT_SELL("Limit sell", -1),
    STOP_BUY("Stop buy", 1),
    STOP_SELL("Stop sell", -1),
    STOP_LIMIT_BUY("Stop limit buy", 1),
    STOP_LIMIT_SELL("Stop limit sell", -1),
    DIVIDEND("Dividend (Dividend)", 0),
    INTEREST_ON_CASH("Interest on cash", 0),
    CARD_DEBIT("Card debit", 0),
    RESULT_ADJUSTMENT("Result adjustment", 0);

    private final String csvValue;
    private final int shareDirection;

    AccountActivityAction(String csvValue, int shareDirection) {
        this.csvValue = csvValue;
        this.shareDirection = shareDirection;
    }

    public boolean isTrade() {
        return shareDirection != 0;
    }

    public boolean isSell() {
        return shareDirection < 0;
    }

    public static Optional<AccountActivityAction> fromCsvValue(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        return Arrays.stream(values()).filter(action -> action.csvValue.equalsIgnoreCase(trimmed)).findFirst();
    }
}
