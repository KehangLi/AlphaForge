package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
import com.likehang.alphaforge.exception.ConflictException;
import com.likehang.alphaforge.exception.ResourceNotFoundException;
import com.likehang.alphaforge.rest.dto.response.ImportBatchPageResponse;
import com.likehang.alphaforge.rest.dto.response.ImportBatchResponse;
import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.repository.AccountActivityRepository;
import com.likehang.alphaforge.repository.BrokerageAccountRepository;
import com.likehang.alphaforge.repository.ImportBatchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ImportBatchService {

    private static final Logger log = LoggerFactory.getLogger(ImportBatchService.class);

    private final ImportBatchRepository importBatchRepository;
    private final AccountActivityRepository accountActivityRepository;
    private final BrokerageAccountRepository brokerageAccountRepository;
    private final UserWriteLockService userWriteLockService;
    private final AccountActivityHoldingValidationService holdingValidationService;

    public ImportBatchService(
            ImportBatchRepository importBatchRepository,
            AccountActivityRepository accountActivityRepository,
            BrokerageAccountRepository brokerageAccountRepository,
            UserWriteLockService userWriteLockService,
            AccountActivityHoldingValidationService holdingValidationService
    ) {
        this.importBatchRepository = importBatchRepository;
        this.accountActivityRepository = accountActivityRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
        this.userWriteLockService = userWriteLockService;
        this.holdingValidationService = holdingValidationService;
    }

    @Transactional(readOnly = true)
    public ImportBatchPageResponse getImportBatchesByBrokerageAccount(
            UUID userId,
            UUID brokerageAccountId,
            Pageable pageable
    ) {
        validateUserAndBrokerageAccount(userId, brokerageAccountId);
        UUID latestBatchId = importBatchRepository.findFirstByBrokerageAccount_IdOrderByImportSequenceDesc(brokerageAccountId)
                .map(ImportBatch::getId).orElse(null);

        Page<ImportBatchResponse> page = importBatchRepository
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByImportSequenceDesc(
                        userId,
                        brokerageAccountId,
                        pageable
                )
                .map(batch -> toResponse(batch, batch.getId().equals(latestBatchId)));

        log.info(
                "Loaded import batches: brokerageAccount={}, page={}, size={}, totalElements={}",
                brokerageAccountId,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements()
        );

        return new ImportBatchPageResponse(
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

    @Transactional
    public void deleteImportBatch(
            UUID userId,
            UUID brokerageAccountId,
            UUID importBatchId
    ) {
        if (userId == null) {
            throw new BadRequestException("User id is required");
        }
        userWriteLockService.lockUser(userId);
        validateUserAndBrokerageAccount(userId, brokerageAccountId);

        if (importBatchId == null) {
            throw new BadRequestException("Import batch id is required");
        }

        ImportBatch importBatch = importBatchRepository
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndId(
                        userId,
                        brokerageAccountId,
                        importBatchId
                )
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Import batch not found: " + importBatchId
                ));

        ImportBatch latest = importBatchRepository.findFirstByBrokerageAccount_IdOrderByImportSequenceDesc(brokerageAccountId)
                .orElseThrow(() -> new ResourceNotFoundException("Import batch not found: " + importBatchId));
        if (!latest.getId().equals(importBatchId)) {
            throw new ConflictException("Only the latest import batch can be deleted. Delete batch "
                    + latest.getId() + " first, then retry.");
        }
        // Also protect accounts imported before the LIFO rule and deterministic ordering existed.
        try {
            holdingValidationService.validateAfterRemovingBatch(brokerageAccountId, importBatchId);
        } catch (BadRequestException exception) {
            throw new ConflictException("Deleting this batch would leave invalid holdings. " + exception.getMessage());
        }

        long deletedActivities = accountActivityRepository.deleteByImportBatch_Id(importBatchId);
        importBatchRepository.delete(importBatch);

        log.info(
                "Deleted import batch: batch={}, brokerageAccount={}, deletedActivities={}",
                importBatchId,
                brokerageAccountId,
                deletedActivities
        );
    }

    private void validateUserAndBrokerageAccount(UUID userId, UUID brokerageAccountId) {
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
    }

    private ImportBatchResponse toResponse(ImportBatch importBatch, boolean deletable) {
        return new ImportBatchResponse(
                importBatch.getId(),
                importBatch.getBrokerageAccount().getId(),
                importBatch.getOriginalFilename(),
                importBatch.getFileHash(),
                importBatch.getStatus(),
                importBatch.getTotalRows(),
                importBatch.getSuccessRows(),
                importBatch.getFailedRows(),
                importBatch.getStartedAt(),
                importBatch.getCompletedAt(),
                importBatch.getCreatedAt(),
                importBatch.getUpdatedAt(),
                deletable
        );
    }
}
