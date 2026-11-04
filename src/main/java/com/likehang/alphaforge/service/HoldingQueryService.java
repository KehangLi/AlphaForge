package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.rest.dto.response.HoldingPageResponse;
import com.likehang.alphaforge.rest.dto.response.HoldingResponse;
import com.likehang.alphaforge.service.holding.HoldingCalculator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class HoldingQueryService {

    private static final Logger log = LoggerFactory.getLogger(HoldingQueryService.class);
    private static final int MAX_SEARCH_LENGTH = 120;

    private final BrokerageAccountRepository accounts;
    private final AccountActivityRepository activities;
    private final HoldingCalculator holdingCalculator;

    public HoldingQueryService(BrokerageAccountRepository accounts, AccountActivityRepository activities,
                               HoldingCalculator holdingCalculator) {
        this.accounts = accounts;
        this.activities = activities;
        this.holdingCalculator = holdingCalculator;
    }

    @Transactional(readOnly = true)
    public HoldingPageResponse getHoldingsByBrokerageAccount(
            UUID userId, UUID brokerageAccountId, HoldingFilter status, String search, Pageable pageable
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }
        if (brokerageAccountId == null) {
            throw new BadRequestException("Brokerage account id is required");
        }
        accounts.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Brokerage account not found: " + brokerageAccountId));
        if (status == null || pageable == null || pageable.isUnpaged()) {
            throw new BadRequestException("Holding status and a paged request are required");
        }
        String normalizedSearch = search == null ? "" : search.trim().toUpperCase(Locale.ROOT);
        if (normalizedSearch.length() > MAX_SEARCH_LENGTH) {
            throw new BadRequestException("Holding search must be at most " + MAX_SEARCH_LENGTH + " characters");
        }

        // Aggregate the complete history before applying any search, status filter, or pagination.
        List<HoldingResponse> filtered = holdingCalculator
                .calculate(activities.findHoldingActivities(userId, brokerageAccountId)).stream()
                .filter(holding -> status.includes(holding.status()))
                .filter(holding -> matchesSearch(holding, normalizedSearch))
                .sorted(Comparator.comparing(HoldingQueryService::sortKey).thenComparing(HoldingResponse::instrumentKey))
                .toList();
        int start = (int) Math.min(pageable.getOffset(), filtered.size());
        int end = (int) Math.min((long) start + pageable.getPageSize(), filtered.size());
        Page<HoldingResponse> page = new PageImpl<>(filtered.subList(start, end), pageable, filtered.size());
        log.info("Loaded holdings: brokerageAccount={}, page={}, size={}, totalElements={}",
                brokerageAccountId, page.getNumber(), page.getSize(), page.getTotalElements());
        return new HoldingPageResponse(brokerageAccountId, page.getContent(), page.getNumber(), page.getSize(),
                page.getTotalElements(), page.getTotalPages(), page.isFirst(), page.isLast());
    }

    private boolean matchesSearch(HoldingResponse holding, String search) {
        return search.isEmpty() || contains(holding.ticker(), search) || contains(holding.isin(), search)
                || contains(holding.instrumentName(), search);
    }

    private boolean contains(String value, String search) {
        return value != null && value.toUpperCase(Locale.ROOT).contains(search);
    }

    private static String sortKey(HoldingResponse holding) {
        return holding.ticker() == null ? holding.instrumentKey() : holding.ticker();
    }

}
