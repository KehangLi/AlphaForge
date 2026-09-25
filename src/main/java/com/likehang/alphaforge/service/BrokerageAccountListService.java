package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class BrokerageAccountListService {

    private static final Logger log = LoggerFactory.getLogger(BrokerageAccountListService.class);

    private final BrokerageAccountRepository brokerageAccountRepository;

    public BrokerageAccountListService(BrokerageAccountRepository brokerageAccountRepository) {
        this.brokerageAccountRepository = brokerageAccountRepository;
    }

    @Transactional(readOnly = true)
    public List<BrokerageAccountListResponse> getBrokerageAccountList(
            UUID userId
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }

        List<BrokerageAccount> brokerageAccounts = brokerageAccountRepository.findByUser_IdOrderByCreatedAtDesc(userId);

        log.info("Loaded brokerage accounts: user={}, total={}", userId, brokerageAccounts.size());

        return brokerageAccounts.stream()
                .map(this::toResponse)
                .toList();
    }

    private BrokerageAccountListResponse toResponse(BrokerageAccount brokerageAccount) {
        return new BrokerageAccountListResponse(
                brokerageAccount.getId(),
                brokerageAccount.getBrokerName(),
                brokerageAccount.getAccountName(),
                brokerageAccount.getAccountNumberMasked(),
                brokerageAccount.getBaseCurrency()
        );
    }
}
