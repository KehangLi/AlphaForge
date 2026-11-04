package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.projection.HoldingActivity;
import com.likehang.alphaforge.rest.dto.response.AccountActivityResponse;
import com.likehang.alphaforge.rest.dto.response.HoldingResponse;
import com.likehang.alphaforge.rest.dto.response.InstrumentDetailResponse;
import com.likehang.alphaforge.service.holding.HoldingCalculator;
import com.likehang.alphaforge.service.holding.TradeActivitySupport;
import com.likehang.alphaforge.service.mapper.AccountActivityResponseMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class InstrumentQueryService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentQueryService.class);

    private final BrokerageAccountRepository accounts;
    private final AccountActivityRepository activities;
    private final HoldingCalculator holdingCalculator;
    private final AccountActivityResponseMapper responseMapper;

    public InstrumentQueryService(BrokerageAccountRepository accounts, AccountActivityRepository activities,
                                  HoldingCalculator holdingCalculator, AccountActivityResponseMapper responseMapper) {
        this.accounts = accounts;
        this.activities = activities;
        this.holdingCalculator = holdingCalculator;
        this.responseMapper = responseMapper;
    }

    // Summary and page must use the same snapshot if an import/deletion commits between queries.
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public InstrumentDetailResponse getInstrumentByBrokerageAccount(
            UUID userId, UUID brokerageAccountId, String instrumentKey, Pageable pageable
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }
        if (brokerageAccountId == null) {
            throw new BadRequestException("Brokerage account id is required");
        }
        accounts.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Brokerage account not found: " + brokerageAccountId));
        String key = TradeActivitySupport.normalizeInstrumentKey(instrumentKey);
        if (pageable == null || pageable.isUnpaged()) {
            throw new BadRequestException("A paged request is required");
        }

        List<HoldingActivity> history = activities.findInstrumentHistory(userId, brokerageAccountId, key);
        if (history.isEmpty()) {
            throw new ResourceNotFoundException("Instrument not found in brokerage account: " + key);
        }
        HoldingResponse holding = holdingCalculator.calculate(history).stream().findFirst().orElse(null);

        String isin = null;
        String ticker = null;
        String name = null;
        for (HoldingActivity activity : history) {
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

        Page<AccountActivityResponse> page = activities.findInstrumentActivities(userId, brokerageAccountId, key, pageable)
                .map(responseMapper::toResponse);
        log.info("Loaded instrument detail: brokerageAccount={}, instrument={}, page={}, size={}, totalElements={}",
                brokerageAccountId, key, page.getNumber(), page.getSize(), page.getTotalElements());
        return new InstrumentDetailResponse(brokerageAccountId, key, isin, ticker, name, holding,
                page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.isFirst(), page.isLast());
    }
}

/*
  will return the summary of current ticker, and current page of activities
*/