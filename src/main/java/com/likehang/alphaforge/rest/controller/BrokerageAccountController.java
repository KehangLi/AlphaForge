package com.likehang.alphaforge.rest.controller;

import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.service.BrokerageAccountListService;
import com.likehang.alphaforge.service.CurrentUserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/brokerage-accounts")
public class BrokerageAccountController {

    private final BrokerageAccountListService brokerageAccountListService;
    private final CurrentUserService currentUserService;

    public BrokerageAccountController(
            BrokerageAccountListService brokerageAccountListService,
            CurrentUserService currentUserService
    ) {
        this.brokerageAccountListService = brokerageAccountListService;
        this.currentUserService = currentUserService;
    }

    @GetMapping
    public ResponseEntity<List<BrokerageAccountListResponse>> listBrokerageAccounts() {
        UUID userId = currentUserService.getCurrentUserId();
        List<BrokerageAccountListResponse> result = brokerageAccountListService.getBrokerageAccountList(userId);

        return ResponseEntity.ok(result);
    }
}
