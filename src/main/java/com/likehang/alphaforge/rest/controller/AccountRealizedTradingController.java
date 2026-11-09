package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.AccountRealizedTradingSummaryResponse;
import com.likehang.alphaforge.service.AccountRealizedTradingQueryService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/realized-trading-summary")
public class AccountRealizedTradingController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final AccountRealizedTradingQueryService summaryService;
    private final CurrentUserService currentUserService;

    public AccountRealizedTradingController(AccountRealizedTradingQueryService summaryService, CurrentUserService currentUserService) {
        this.summaryService = summaryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<AccountRealizedTradingSummaryResponse> getSummary(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "currency", required = false) String currency,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        PageRequest pageable = PageRequest.of(Math.clamp(page, 0, MAX_PAGE),
                size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
        AccountRealizedTradingSummaryResponse result = summaryService.getSummary(
                currentUserService.getCurrentUserId(), brokerageAccountId, from, to, currency, pageable);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
