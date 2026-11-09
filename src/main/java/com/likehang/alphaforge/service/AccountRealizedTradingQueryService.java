package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.projection.InstrumentActivity;
import com.likehang.alphaforge.rest.dto.response.AccountRealizedTradingSummaryResponse;
import com.likehang.alphaforge.rest.dto.response.InstrumentRealizedContributionResponse;
import com.likehang.alphaforge.rest.dto.response.RealizedTradingSummaryResponse;
import com.likehang.alphaforge.service.holding.TradeActivitySupport;
import com.likehang.alphaforge.service.trading.RealizedTradingCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class AccountRealizedTradingQueryService {

    private static final Logger log = LoggerFactory.getLogger(AccountRealizedTradingQueryService.class);

    private final BrokerageAccountRepository accounts;
    private final AccountActivityRepository activities;
    private final RealizedTradingCalculator calculator;

    public AccountRealizedTradingQueryService(BrokerageAccountRepository accounts, AccountActivityRepository activities,
                                             RealizedTradingCalculator calculator) {
        this.accounts = accounts;
        this.activities = activities;
        this.calculator = calculator;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public AccountRealizedTradingSummaryResponse getSummary(
            UUID userId, UUID brokerageAccountId, LocalDate from, LocalDate to, String currency, Pageable pageable
    ) {
        if (userId == null || brokerageAccountId == null) {
            throw new BadRequestException("User and brokerage account ids are required");
        }
        accounts.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Brokerage account not found: " + brokerageAccountId));
        validateDate(from);
        validateDate(to);
        if (from != null && to != null && from.isAfter(to)) {
            throw new BadRequestException("From date must not be after to date");
        }
        if (pageable == null || pageable.isUnpaged()) {
            throw new BadRequestException("A paged request is required");
        }
        String selectedCurrency = TradeActivitySupport.normalizeIdentifier(currency);
        if (selectedCurrency != null && !selectedCurrency.matches("[A-Z]{3}")) {
            throw new BadRequestException("Currency must be a three-letter code such as EUR");
        }
        OffsetDateTime fromInclusive = from == null ? null : from.atStartOfDay().atOffset(ZoneOffset.UTC);
        OffsetDateTime toExclusive = to == null ? null : to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.UTC);
        List<InstrumentActivity> history = activities.findRealizedTradingActivities(
                userId, brokerageAccountId, fromInclusive, toExclusive);

        RealizedTradingSummaryResponse summary = calculator.calculate(history);
        Map<ContributionKey, List<InstrumentActivity>> grouped = new HashMap<>();
        long unidentified = 0;
        for (InstrumentActivity activity : history) {
            String key = TradeActivitySupport.instrumentKey(activity.isin(), activity.ticker());
            if (key == null) {
                unidentified++;
            }
            String resultCurrency = TradeActivitySupport.normalizeIdentifier(activity.resultCurrency());
            grouped.computeIfAbsent(new ContributionKey(key, resultCurrency), ignored -> new ArrayList<>()).add(activity);
        }
        // The account overview includes all currencies; filters/pagination only change contribution rows.
        List<InstrumentRealizedContributionResponse> contributions = grouped.entrySet().stream()
                .filter(entry -> selectedCurrency == null || selectedCurrency.equals(entry.getKey().currency()))
                .map(entry -> toContribution(entry.getKey(), entry.getValue()))
                .sorted(contributionOrder())
                .toList();
        int start = (int) Math.min(pageable.getOffset(), contributions.size());
        int end = (int) Math.min((long) start + pageable.getPageSize(), contributions.size());
        Page<InstrumentRealizedContributionResponse> page = new PageImpl<>(
                contributions.subList(start, end), pageable, contributions.size());
        log.info("Loaded account realized summary: account={}, from={}, to={}, currency={}, page={}, size={}, totalElements={}",
                brokerageAccountId, from, to, selectedCurrency, page.getNumber(), page.getSize(), page.getTotalElements());
        return new AccountRealizedTradingSummaryResponse(brokerageAccountId, from, to, selectedCurrency, summary,
                unidentified, page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private void validateDate(LocalDate date) {
        if (date != null && (date.getYear() < 1 || date.getYear() > 9999)) {
            throw new BadRequestException("Dates must use a year between 0001 and 9999");
        }
    }

    private InstrumentRealizedContributionResponse toContribution(ContributionKey key, List<InstrumentActivity> history) {
        String isin = null;
        String ticker = null;
        String name = null;
        if (key.instrumentKey() != null) {
            // Rows arrive oldest first; retain the latest nonblank sale metadata within the period.
            for (InstrumentActivity activity : history) {
                String nextIsin = TradeActivitySupport.normalizeIdentifier(activity.isin());
                String nextTicker = TradeActivitySupport.normalizeIdentifier(activity.ticker());
                if (nextIsin != null) {
                    isin = nextIsin;
                }
                if (nextTicker != null) {
                    ticker = nextTicker;
                }
                if (activity.instrumentName() != null && !activity.instrumentName().isBlank()) {
                    name = activity.instrumentName().trim();
                }
            }
        }
        return new InstrumentRealizedContributionResponse(key.instrumentKey(), isin, ticker,
                key.instrumentKey() == null ? "Unknown instrument" : name, key.currency(), calculator.calculate(history));
    }

    private Comparator<InstrumentRealizedContributionResponse> contributionOrder() {
        return Comparator.comparing(InstrumentRealizedContributionResponse::currency, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(this::totalResult, Comparator.nullsLast(Comparator.reverseOrder()))
                .thenComparing(InstrumentRealizedContributionResponse::ticker, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(InstrumentRealizedContributionResponse::instrumentKey, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private BigDecimal totalResult(InstrumentRealizedContributionResponse contribution) {
        return contribution.realizedSummary().currencies().stream().findFirst()
                .map(summary -> summary.totalResult()).orElse(null);
    }

    private record ContributionKey(String instrumentKey, String currency) {
    }
}

/*
    example of ContributionKey:
    (NVDA's key, EUR) → [result +20、-5]
    (NVDA's key, USD) → [result +10]
    (AAPL's key, EUR) → [result -3]
 */