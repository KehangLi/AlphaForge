package com.likehang.alphaforge.service;

import com.likehang.alphaforge.model.dto.command.BrokerageAccountCreateRequest;
import com.likehang.alphaforge.model.dto.query.BrokerageAccountListResponse;
import com.likehang.alphaforge.model.entity.AppUser;
import com.likehang.alphaforge.model.entity.BrokerageAccount;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.AppUserRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
public class BrokerageAccountService {

    private static final Logger log = LoggerFactory.getLogger(BrokerageAccountService.class);

    private final BrokerageAccountRepository brokerageAccountRepository;
    private final AppUserRepository appUserRepository;
    private final AccountActivityRepository accountActivityRepository;
    private final ImportBatchRepository importBatchRepository;

    public BrokerageAccountService(
            BrokerageAccountRepository brokerageAccountRepository,
            AppUserRepository appUserRepository,
            AccountActivityRepository accountActivityRepository,
            ImportBatchRepository importBatchRepository
    ) {
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.appUserRepository = appUserRepository;
        this.accountActivityRepository = accountActivityRepository;
        this.importBatchRepository = importBatchRepository;
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

    @Transactional
    public BrokerageAccountListResponse createBrokerageAccount(
            UUID userId,
            BrokerageAccountCreateRequest request
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }

        if (request == null) {
            throw new BadRequestException("Brokerage account request is required");
        }

        String brokerName = requireText(request.brokerName(), "Broker name is required");
        String accountName = requireText(request.accountName(), "Account name is required");
        String baseCurrency = normalizeBaseCurrency(request.baseCurrency());
        String accountNumberMasked = blankToNull(request.accountNumberMasked());

        validateMaxLength(brokerName, 120, "Broker name");
        validateMaxLength(accountName, 120, "Account name");
        validateMaxLength(accountNumberMasked, 80, "Account number masked");

        AppUser appUser = appUserRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        if (brokerageAccountRepository.existsByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
                userId,
                brokerName,
                accountName
        )) {
            throw new BadRequestException(
                    "Brokerage account already exists: " + brokerName + " / " + accountName
            );
        }

        BrokerageAccount brokerageAccount = new BrokerageAccount(
                appUser,
                brokerName,
                accountName,
                baseCurrency
        );
        brokerageAccount.setAccountNumberMasked(accountNumberMasked);

        BrokerageAccount savedBrokerageAccount = brokerageAccountRepository.save(brokerageAccount);

        log.info(
                "Created brokerage account: user={}, account={}, broker={}, accountName={}",
                userId,
                savedBrokerageAccount.getId(),
                savedBrokerageAccount.getBrokerName(),
                savedBrokerageAccount.getAccountName()
        );

        return toResponse(savedBrokerageAccount);
    }

    @Transactional
    public void deleteBrokerageAccount(UUID userId, UUID brokerageAccountId) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }

        if (brokerageAccountId == null) {
            throw new BadRequestException("Brokerage account id is required");
        }

        BrokerageAccount brokerageAccount = brokerageAccountRepository.findByIdAndUser_Id(brokerageAccountId, userId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Brokerage account not found: " + brokerageAccountId
                ));

        long deletedActivities = accountActivityRepository.deleteByBrokerageAccount_Id(brokerageAccountId);
        long deletedImportBatches = importBatchRepository.deleteByBrokerageAccount_Id(brokerageAccountId);
        brokerageAccountRepository.delete(brokerageAccount);

        log.info(
                "Deleted brokerage account: user={}, account={}, deletedActivities={}, deletedImportBatches={}",
                userId,
                brokerageAccountId,
                deletedActivities,
                deletedImportBatches
        );
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

    private String requireText(String value, String message) {
        String trimmed = blankToNull(value);

        if (trimmed == null) {
            throw new BadRequestException(message);
        }

        return trimmed;
    }

    private String blankToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeBaseCurrency(String value) {
        String baseCurrency = requireText(value, "Base currency is required")
                .toUpperCase(Locale.ROOT);

        if (baseCurrency.length() != 3) {
            throw new BadRequestException("Base currency must be a 3-letter code");
        }

        return baseCurrency;
    }

    private void validateMaxLength(String value, int maxLength, String fieldName) {
        if (value != null && value.length() > maxLength) {
            throw new BadRequestException(fieldName + " must be at most " + maxLength + " characters");
        }
    }
}
