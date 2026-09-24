package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.query.AccountActivityPageResponse;
import com.likehang.alphaforge.service.AccountActivityQueryService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts")
public class BrokerageAccountActivityController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final AccountActivityQueryService accountActivityQueryService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountActivityController(
            AccountActivityQueryService accountActivityQueryService,
            CurrentUserService currentUserService
    ) {
        this.accountActivityQueryService = accountActivityQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping("/{brokerageAccountId}/account-activities")
    public ResponseEntity<AccountActivityPageResponse> listActivities(
            @PathVariable UUID brokerageAccountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        UUID userId = currentUserService.getCurrentUserId();
        AccountActivityPageResponse result = accountActivityQueryService.getActivitiesByBrokerageAccount(
                userId,
                brokerageAccountId,
                pageable
        );

        return ResponseEntity.ok(result);
    }

    private int normalizePage(int page) {
        if (page < 0) {
            return 0;
        }

        return Math.min(page, MAX_PAGE);
    }

    private int normalizeSize(int size) {
        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }
}

// page: which page?   size: each page returns how many records