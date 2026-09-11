package com.likehang.alphaforge.repository;

import com.likehang.alphaforge.model.entity.AccountActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountActivityRepository extends JpaRepository<AccountActivity, UUID>, JpaSpecificationExecutor<AccountActivity> {

    List<AccountActivity> findByImportBatch_IdOrderByRowNumberAsc(UUID importBatchId);

    Page<AccountActivity> findByImportBatch_IdOrderByRowNumberAsc(UUID importBatchId, Pageable pageable);

    Page<AccountActivity> findByImportBatch_BrokerageAccount_User_IdAndImportBatch_IdOrderByRowNumberAsc(
            UUID userId,
            UUID importBatchId,
            Pageable pageable
    );

    Page<AccountActivity> findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByOccurredAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            Pageable pageable
    );

    Page<AccountActivity> findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndTickerIgnoreCaseOrderByOccurredAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            String ticker,
            Pageable pageable
    );

    Page<AccountActivity> findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndActionIgnoreCaseOrderByOccurredAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            String action,
            Pageable pageable
    );

    Page<AccountActivity> findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    Page<AccountActivity> findByBrokerageAccount_User_IdAndBrokerageAccount_IdAndTickerIgnoreCaseAndOccurredAtBetweenOrderByOccurredAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            String ticker,
            OffsetDateTime from,
            OffsetDateTime to,
            Pageable pageable
    );

    Optional<AccountActivity> findByImportBatch_IdAndRowNumber(UUID importBatchId, int rowNumber);

    Optional<AccountActivity> findByBrokerageAccount_IdAndExternalTransactionId(
            UUID brokerageAccountId,
            String externalTransactionId
    );

    boolean existsByBrokerageAccount_IdAndExternalTransactionId(UUID brokerageAccountId, String externalTransactionId);

    boolean existsByBrokerageAccount_IdAndRawRowHash(UUID brokerageAccountId, String rawRowHash);

    long deleteByImportBatch_Id(UUID importBatchId);
}
