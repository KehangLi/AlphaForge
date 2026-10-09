package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.rest.dto.request.BrokerageAccountCreateRequest;
import com.likehang.alphaforge.rest.dto.response.BrokerageAccountListResponse;
import com.likehang.alphaforge.service.BrokerageAccountService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts")
public class BrokerageAccountController {

    private final BrokerageAccountService brokerageAccountService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountController(
            BrokerageAccountService brokerageAccountService,
            CurrentUserService currentUserService
    ) {
        this.brokerageAccountService = brokerageAccountService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<BrokerageAccountListResponse>> listBrokerageAccounts() {
        UUID userId = currentUserService.getCurrentUserId();
        List<BrokerageAccountListResponse> result = brokerageAccountService.getBrokerageAccountList(userId);

        return ResponseEntity.ok(result);
    }

    @PostMapping
    public ResponseEntity<BrokerageAccountListResponse> createBrokerageAccount(
            @RequestBody BrokerageAccountCreateRequest request
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        BrokerageAccountListResponse result = brokerageAccountService.createBrokerageAccount(userId, request);

        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    @DeleteMapping("/{brokerageAccountId}")
    public ResponseEntity<Void> deleteBrokerageAccount(
            @PathVariable("brokerageAccountId") UUID brokerageAccountId
    ) {
        UUID userId = currentUserService.getCurrentUserId();
        brokerageAccountService.deleteBrokerageAccount(userId, brokerageAccountId);

        return ResponseEntity.noContent().build();
    }
}
