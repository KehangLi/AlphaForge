package com.likehang.alphaforge.service;

import com.likehang.alphaforge.repository.projection.InstrumentActivity;
import com.likehang.alphaforge.service.trading.RealizedTradingCalculator;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RealizedTradingCalculatorTest {

    private final RealizedTradingCalculator calculator = new RealizedTradingCalculator();

    @Test
    void groupsResultsByCurrencyAndCountsWinsLossesAndRealZerosExactly() {
        var result = calculator.calculate(List.of(
                activity("Limit sell", "10.1250", " eur "), activity("Stop sell", "-3.0000", "EUR"),
                activity("Market sell", "0", "EUR"), activity("Limit sell", "5", "USD"),
                activity("Limit sell", "0.0001", "EUR"), activity("Limit sell", "-0.0001", "EUR")));
        assertThat(result.sellRecordCount()).isEqualTo(6);
        assertThat(result.includedSellRecordCount()).isEqualTo(6);
        assertThat(result.excludedSellRecordCount()).isZero();
        assertThat(result.currencies()).extracting("currency").containsExactly("EUR", "USD");
        var eur = result.currencies().getFirst();
        assertThat(eur.totalResult()).isEqualByComparingTo("7.1250");
        assertThat(eur.totalProfit()).isEqualByComparingTo("10.1251");
        assertThat(eur.totalLoss()).isEqualByComparingTo("-3.0001");
        assertThat(eur.profitableSellCount()).isEqualTo(2);
        assertThat(eur.losingSellCount()).isEqualTo(2);
        assertThat(eur.breakevenSellCount()).isEqualTo(1);
        assertThat(result.currencies().getLast().totalResult()).isEqualByComparingTo("5");
    }

    @Test
    void countsMissingResultsSeparatelyFromMissingCurrencyAndNeverTreatsThemAsZero() {
        var result = calculator.calculate(List.of(
                activity("Limit sell", null, "EUR"), activity("Limit sell", null, null),
                activity("Stop sell", "3", " "), activity("Market sell", "2", null),
                activity("Limit sell", "0", "EUR")));
        assertThat(result.sellRecordCount()).isEqualTo(5);
        assertThat(result.includedSellRecordCount()).isEqualTo(1);
        assertThat(result.excludedSellRecordCount()).isEqualTo(4);
        assertThat(result.missingResultCount()).isEqualTo(2);
        assertThat(result.missingResultCurrencyCount()).isEqualTo(2);
        assertThat(result.currencies()).singleElement().satisfies(currency -> {
            assertThat(currency.totalResult()).isEqualByComparingTo("0");
            assertThat(currency.breakevenSellCount()).isEqualTo(1);
        });
    }

    @Test
    void excludesBuyDividendCashAndAdjustmentsEvenWhenTheyContainAResult() {
        var result = calculator.calculate(List.of(
                activity("Limit buy", "10", "EUR"), activity("Dividend (Dividend)", "5", "EUR"),
                activity("Interest on cash", "1", "EUR"), activity("Result adjustment", "2", "EUR"),
                activity("Card debit", "-2", "EUR"), activity(null, "1", "EUR")));
        assertThat(result.sellRecordCount()).isZero();
        assertThat(result.currencies()).isEmpty();
        assertThat(calculator.calculate(List.of()).currencies()).isEmpty();
    }

    @Test
    void allMissingResultsRemainUncoveredWithoutInventingACurrencyTotal() {
        var result = calculator.calculate(List.of(activity("Limit sell", null, null)));
        assertThat(result.sellRecordCount()).isEqualTo(1);
        assertThat(result.includedSellRecordCount()).isZero();
        assertThat(result.currencies()).isEmpty();
    }

    private InstrumentActivity activity(String action, String result, String currency) {
        return new InstrumentActivity(action, "US67066G1040", "NVDA", "NVIDIA", BigDecimal.ONE,
                null, 2, result == null ? null : new BigDecimal(result), currency);
    }
}
