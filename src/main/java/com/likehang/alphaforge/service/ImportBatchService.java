package com.likehang.alphaforge.service;

import com.likehang.alphaforge.exception.BadRequestException;
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

    public ImportBatchService(
            ImportBatchRepository importBatchRepository,
            AccountActivityRepository accountActivityRepository,
            BrokerageAccountRepository brokerageAccountRepository
    ) {
        this.importBatchRepository = importBatchRepository;
        this.accountActivityRepository = accountActivityRepository;
        this.brokerageAccountRepository = brokerageAccountRepository;
    }

    @Transactional(readOnly = true)
    public ImportBatchPageResponse getImportBatchesByBrokerageAccount(
            UUID userId,
            UUID brokerageAccountId,
            Pageable pageable
    ) {
        validateUserAndBrokerageAccount(userId, brokerageAccountId);

        Page<ImportBatchResponse> page = importBatchRepository
                .findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByCreatedAtDesc(
                        userId,
                        brokerageAccountId,
                        pageable
                )
                .map(this::toResponse);

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

    private ImportBatchResponse toResponse(ImportBatch importBatch) {
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
                importBatch.getUpdatedAt()
        );
    }
}
