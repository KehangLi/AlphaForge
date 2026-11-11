package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.service.holding.TradeActivitySupport;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountActivityHoldingValidationService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private final AccountActivityRepository accountActivityRepository;

    public AccountActivityHoldingValidationService(AccountActivityRepository accountActivityRepository) {
        this.accountActivityRepository = accountActivityRepository;
    }

    public void validateNoNegativeHoldings(UUID brokerageAccountId, List<AccountActivity> incomingActivities) {
        List<AccountActivity> activities = new ArrayList<>(
                accountActivityRepository.findByBrokerageAccount_Id(brokerageAccountId)
        );
        activities.addAll(incomingActivities);
        validate(activities);
    }

    public void validateAfterRemovingBatch(UUID brokerageAccountId, UUID importBatchId) {
        List<AccountActivity> remaining = new ArrayList<>(accountActivityRepository
                .findByBrokerageAccount_Id(brokerageAccountId).stream()
                .filter(activity -> !importBatchId.equals(activity.getImportBatch().getId()))
                .toList());
        validate(remaining);
    }

    private void validate(List<AccountActivity> activities) {
        activities.sort(activityOrder());

        Map<String, BigDecimal> holdings = new HashMap<>();
        for (AccountActivity activity : activities) {
            BigDecimal shareChange = TradeActivitySupport.shareChange(
                    activity.getAction(), activity.getNumberOfShares(), activity.getRowNumber());
            if (shareChange == null) {
                continue;
            }

            String instrumentKey = TradeActivitySupport.requiredInstrumentKey(
                    activity.getIsin(), activity.getTicker(), activity.getRowNumber());

            BigDecimal updatedShares = holdings.getOrDefault(instrumentKey, ZERO).add(shareChange);
            if (updatedShares.compareTo(ZERO) < 0) {
                throw new BadRequestException(buildNegativeHoldingMessage(activity, updatedShares));
            }
            holdings.put(instrumentKey, updatedShares);
        }
    }

    private Comparator<AccountActivity> activityOrder() {
        return Comparator
                .comparing((AccountActivity activity) -> activity.getOccurredAt().toInstant()
                        .plusNanos(500).truncatedTo(ChronoUnit.MICROS))
                .thenComparingLong(activity -> activity.getImportBatch() == null ? 0
                        : activity.getImportBatch().getImportSequence())
                .thenComparingInt(AccountActivity::getRowNumber);
    }

    private String buildNegativeHoldingMessage(AccountActivity activity, BigDecimal updatedShares) {
        return "Import would create negative holdings for "
                + describeInstrument(activity)
                + " at row "
                + activity.getRowNumber()
                + ": "
                + updatedShares.stripTrailingZeros().toPlainString()
                + " shares. Add earlier buy history or an opening position before importing this file.";
    }

    private String describeInstrument(AccountActivity activity) {
        String ticker = normalizeUpper(activity.getTicker());
        String isin = normalizeUpper(activity.getIsin());
        String name = trimToNull(activity.getInstrumentName());

        if (ticker != null && isin != null) {
            return ticker + " (" + isin + ")";
        }
        if (ticker != null) {
            return ticker;
        }
        if (isin != null) {
            return isin;
        }
        if (name != null) {
            return name;
        }
        return "unknown instrument";
    }

    private String normalizeUpper(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            return null;
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed;
    }
}
