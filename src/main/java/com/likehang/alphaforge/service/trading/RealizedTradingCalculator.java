package com.likehang.alphaforge.service.trading;

import com.likehang.alphaforge.repository.projection.InstrumentActivity;
import com.likehang.alphaforge.rest.dto.response.RealizedCurrencySummaryResponse;
import com.likehang.alphaforge.rest.dto.response.RealizedTradingSummaryResponse;
import com.likehang.alphaforge.service.holding.TradeActivitySupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Component
public class RealizedTradingCalculator {

    public RealizedTradingSummaryResponse calculate(List<InstrumentActivity> history) {
        Map<String, CurrencyTotals> totals = new TreeMap<>();
        long sellCount = 0;
        long missingResult = 0;
        long missingCurrency = 0;
        for (InstrumentActivity activity : history) {
            String action = TradeActivitySupport.normalizeIdentifier(activity.action());
            if (action == null || !action.contains("SELL")) {
                continue;
            }
            sellCount++;
            if (activity.resultAmount() == null) {
                missingResult++;
                continue;
            }
            String currency = TradeActivitySupport.normalizeIdentifier(activity.resultCurrency());
            if (currency == null) {
                missingCurrency++;
                continue;
            }
            // Result is broker-reported. Do not derive it from Total or subtract fees again.
            totals.computeIfAbsent(currency, ignored -> new CurrencyTotals()).add(activity.resultAmount());
        }
        long excluded = missingResult + missingCurrency;
        List<RealizedCurrencySummaryResponse> currencies = totals.entrySet().stream()
                .map(entry -> entry.getValue().toResponse(entry.getKey())).toList();
        return new RealizedTradingSummaryResponse(sellCount, sellCount - excluded, excluded,
                missingResult, missingCurrency, currencies);
    }

    private static final class CurrencyTotals {

        private BigDecimal profit = BigDecimal.ZERO;
        private BigDecimal loss = BigDecimal.ZERO;
        private long profitable;
        private long losing;
        private long breakeven;

        private void add(BigDecimal result) {
            if (result.signum() > 0) {
                profit = profit.add(result);
                profitable++;
            } else if (result.signum() < 0) {
                loss = loss.add(result);
                losing++;
            } else {
                breakeven++;
            }
        }

        private RealizedCurrencySummaryResponse toResponse(String currency) {
            return new RealizedCurrencySummaryResponse(currency, profit.add(loss), profit, loss,
                    profitable, losing, breakeven);
        }
    }
}
