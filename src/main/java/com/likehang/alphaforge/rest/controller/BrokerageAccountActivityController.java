package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.csv.AccountActivityCsvImportResult;
import com.likehang.alphaforge.model.dto.query.AccountActivityPageResponse;
import com.likehang.alphaforge.service.AccountActivityCsvImportService;
import com.likehang.alphaforge.service.AccountActivityQueryService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/account-activities")
public class BrokerageAccountActivityController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final AccountActivityCsvImportService accountActivityCsvImportService;
    private final AccountActivityQueryService accountActivityQueryService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountActivityController(
            AccountActivityCsvImportService accountActivityCsvImportService,
            AccountActivityQueryService accountActivityQueryService,
            CurrentUserService currentUserService
    ) {
        this.accountActivityCsvImportService = accountActivityCsvImportService;
        this.accountActivityQueryService = accountActivityQueryService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<AccountActivityPageResponse> listActivities(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
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

    @PostMapping(value = "/imports", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<AccountActivityCsvImportResult> uploadCsv(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @RequestParam("file") MultipartFile file
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        AccountActivityCsvImportResult result = accountActivityCsvImportService.importCsv(
                userId,
                brokerageAccountId,
                file
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
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
