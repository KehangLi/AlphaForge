package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.query.AccountActivityPageResponse;
import com.likehang.alphaforge.model.dto.query.AccountActivityResponse;
import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AccountActivityQueryService {

    private static final Logger log = LoggerFactory.getLogger(AccountActivityQueryService.class);

    private final AccountActivityRepository accountActivityRepository;
    private final BrokerageAccountRepository brokerageAccountRepository;

    public AccountActivityQueryService(
            AccountActivityRepository accountActivityRepository,
            BrokerageAccountRepository brokerageAccountRepository
    ) {
        this.accountActivityRepository = accountActivityRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
    }

    @Transactional(readOnly = true)
    public AccountActivityPageResponse getActivitiesByBrokerageAccount(
            UUID userId,
            UUID brokerageAccountId,
            Pageable pageable
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }

        if (brokerageAccountId == null) {
            throw new BadRequestException("Brokerage account id is required");
        }

        brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brokerage account not found: " + brokerageAccountId
                ));

        Page<AccountActivityResponse> page = accountActivityRepository
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByOccurredAtDesc(
                        userId,
                        brokerageAccountId,
                        pageable
                )
                .map(this::toResponse);

        log.info(
                "Loaded account activities: brokerageAccount={}, page={}, size={}, totalElements={}",
                brokerageAccountId,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return new AccountActivityPageResponse(
                brokerageAccountId,
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast()
        );
    }

    private AccountActivityResponse toResponse(AccountActivity accountActivity) {
        return new AccountActivityResponse(
                accountActivity.getId(),
                accountActivity.getBrokerageAccount().getId(),
                accountActivity.getImportBatch().getId(),
                accountActivity.getRowNumber(),
                accountActivity.getExternalTransactionId(),
                accountActivity.getAction(),
                accountActivity.getOccurredAt(),
                accountActivity.getIsin(),
                accountActivity.getTicker(),
                accountActivity.getInstrumentName(),
                accountActivity.getNotes(),
                accountActivity.getNumberOfShares(),
                accountActivity.getPricePerShare(),
                accountActivity.getPricePerShareCurrency(),
                accountActivity.getExchangeRate(),
                accountActivity.getResultAmount(),
                accountActivity.getResultCurrency(),
                accountActivity.getTotalAmount(),
                accountActivity.getTotalCurrency(),
                accountActivity.getWithholdingTax(),
                accountActivity.getWithholdingTaxCurrency(),
                accountActivity.getCurrencyConversionFee(),
                accountActivity.getCurrencyConversionFeeCurrency(),
                accountActivity.getMerchantName(),
                accountActivity.getMerchantCategory()
        );
    }
}
