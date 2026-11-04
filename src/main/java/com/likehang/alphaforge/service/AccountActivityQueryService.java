package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.response.AccountActivityPageResponse;
import com.likehang.alphaforge.rest.dto.response.AccountActivityResponse;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.service.mapper.AccountActivityResponseMapper;
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
    private final AccountActivityResponseMapper responseMapper;

    public AccountActivityQueryService(
            AccountActivityRepository accountActivityRepository,
            BrokerageAccountRepository brokerageAccountRepository,
            AccountActivityResponseMapper responseMapper
    ) {
        this.accountActivityRepository = accountActivityRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.responseMapper = responseMapper;
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
                .map(responseMapper::toResponse); //.map(activity -> responseMapper.toResponse(activity))

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

}
