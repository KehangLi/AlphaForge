package com.likehang.alphaforge.service.holding;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.model.holding.HoldingStatus;
import com.likehang.alphaforge.repository.projection.HoldingActivity;
import com.likehang.alphaforge.rest.dto.response.HoldingResponse;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class HoldingCalculator {

    // Repository queries supply history in chronological order, including deterministic ties.
    public List<HoldingResponse> calculate(List<HoldingActivity> history) {
        Map<String, Position> positions = new HashMap<>();
        for (HoldingActivity activity : history) {
            BigDecimal change;
            String instrumentKey;
            try {
                change = TradeActivitySupport.shareChange(activity.action(), activity.numberOfShares(), activity.rowNumber());
                if (change == null) {
                    continue;
                }
                instrumentKey = TradeActivitySupport.requiredInstrumentKey(activity.isin(), activity.ticker(), activity.rowNumber());
            } catch (BadRequestException exception) {
                throw new ConflictException("Imported history cannot be used to calculate holdings. " + exception.getMessage());
            }
            positions.computeIfAbsent(instrumentKey, Position::new).apply(activity, change);
        }
        return positions.values().stream().map(Position::toResponse).toList();
    }

    private static final class Position {

        private final String instrumentKey;
        private String isin;
        private String ticker;
        private String instrumentName;
        private BigDecimal bought = BigDecimal.ZERO;
        private BigDecimal sold = BigDecimal.ZERO;
        private OffsetDateTime lastTradedAt;

        private Position(String instrumentKey) {
            this.instrumentKey = instrumentKey;
        }

        private void apply(HoldingActivity activity, BigDecimal change) {
            if (change.signum() > 0) {
                bought = bought.add(change);
            } else {
                sold = sold.add(change.abs());
            }
            if (bought.compareTo(sold) < 0) {
                throw new ConflictException("Imported history creates negative holdings for " + instrumentKey
                        + " at CSV row " + activity.rowNumber() + ". Correct the trading history before calculating holdings.");
            }
            String nextIsin = TradeActivitySupport.normalizeIdentifier(activity.isin());
            String nextTicker = TradeActivitySupport.normalizeIdentifier(activity.ticker());
            if (nextIsin != null) {
                isin = nextIsin;
            }
            if (nextTicker != null) {
                ticker = nextTicker;
            }
            if (activity.instrumentName() != null && !activity.instrumentName().isBlank()) {
                instrumentName = activity.instrumentName().trim();
            }
            lastTradedAt = activity.occurredAt();
        }

        private HoldingResponse toResponse() {
            BigDecimal quantity = bought.subtract(sold);
            return new HoldingResponse(instrumentKey, isin, ticker, instrumentName, quantity, bought, sold,
                    lastTradedAt, quantity.signum() > 0 ? HoldingStatus.OPEN : HoldingStatus.CLOSED);
        }
    }
}
