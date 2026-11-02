package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.holding.HoldingFilter;
import com.likehang.alphaforge.rest.dto.response.HoldingPageResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.HoldingQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/holdings")
public class BrokerageAccountHoldingController {

    private static final int DEFAULT_PAGE_SIZE = 10;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final HoldingQueryService holdingQueryService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountHoldingController(HoldingQueryService holdingQueryService, CurrentUserService currentUserService) {
        this.holdingQueryService = holdingQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<HoldingPageResponse> listHoldings(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @RequestParam(name = "status", defaultValue = "OPEN") HoldingFilter status,
            @RequestParam(name = "search", defaultValue = "") String search,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        PageRequest pageable = PageRequest.of(Math.clamp(page, 0, MAX_PAGE),
                size < 1 ? DEFAULT_PAGE_SIZE : Math.min(size, MAX_PAGE_SIZE));
        HoldingPageResponse result = holdingQueryService.getHoldingsByBrokerageAccount(
                currentUserService.getCurrentUserId(), brokerageAccountId, status, search, pageable);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(result);
    }
}
