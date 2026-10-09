package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.response.ImportBatchPageResponse;
import com.likehang.alphaforge.service.CurrentUserService;
import com.likehang.alphaforge.service.ImportBatchService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts/{brokerageAccountId}/import-batches")
public class ImportBatchController {

    private static final int DEFAULT_PAGE_SIZE = 50;
    private static final int MAX_PAGE_SIZE = 200;
    private static final int MAX_PAGE = 1000;

    private final ImportBatchService importBatchService;
    private final CurrentUserService currentUserService;

    public ImportBatchController(
            ImportBatchService importBatchService,
            CurrentUserService currentUserService
    ) {
        this.importBatchService = importBatchService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<ImportBatchPageResponse> listImportBatches(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "" + DEFAULT_PAGE_SIZE) int size
    ) {
        Pageable pageable = PageRequest.of(normalizePage(page), normalizeSize(size));
        UUID userId = currentUserService.getCurrentUserId();
        ImportBatchPageResponse result = importBatchService.getImportBatchesByBrokerageAccount(
                userId,
                brokerageAccountId,
                pageable
        );

        return ResponseEntity.ok(result);
    }

    @DeleteMapping("/{importBatchId}")
    public ResponseEntity<Void> deleteImportBatch(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId,
            @PathVariable("importBatchId") UUID importBatchId
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        importBatchService.deleteImportBatch(userId, brokerageAccountId, importBatchId);

        return ResponseEntity.noContent().build();
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
