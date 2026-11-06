package com.likehang.alphaforge.repository;

import com.likehang.alphaforge.model.entity.AccountActivity;
import com.likehang.alphaforge.repository.projection.HoldingActivity;
import com.likehang.alphaforge.repository.projection.InstrumentActivity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AccountActivityRepository extends JpaRepository<AccountActivity, UUID>, JpaSpecificationExecutor<AccountActivity> {

    String INSTRUMENT_SCOPE = """
            where a.brokerageAccount.user.id = :userId and a.brokerageAccount.id = :brokerageAccountId
              and (case when nullif(trim(a.isin), '') is not null
                   then concat('ISIN:', upper(trim(a.isin)))
                   else concat('TICKER:', upper(trim(a.ticker))) end) = :instrumentKey
            """;

    long countByBrokerageAccount_User_Id(UUID userId);

    List<AccountActivity> findByImportBatch_IdOrderByRowNumberAsc(UUID importBatchId);

    List<AccountActivity> findByBrokerageAccount_Id(UUID brokerageAccountId);

    @Query("""
            select new com.likehang.alphaforge.repository.projection.HoldingActivity(
                a.action, a.isin, a.ticker, a.instrumentName, a.numberOfShares, a.occurredAt, a.rowNumber
            )
            from AccountActivity a
            where a.brokerageAccount.user.id = :userId and a.brokerageAccount.id = :brokerageAccountId
            order by a.occurredAt asc, a.importBatch.importSequence asc, a.rowNumber asc, a.id asc
            """)
    List<HoldingActivity> findHoldingActivities(
            @Param("userId") UUID userId,
            @Param("brokerageAccountId") UUID brokerageAccountId
    );

    @Query("""
            select new com.likehang.alphaforge.repository.projection.InstrumentActivity(
                a.action, a.isin, a.ticker, a.instrumentName, a.numberOfShares, a.occurredAt, a.rowNumber,
                a.resultAmount, a.resultCurrency
            )
            from AccountActivity a
            """ + INSTRUMENT_SCOPE + """
            order by a.occurredAt asc, a.importBatch.importSequence asc, a.rowNumber asc, a.id asc
            """)
    List<InstrumentActivity> findInstrumentHistory(
            @Param("userId") UUID userId,
            @Param("brokerageAccountId") UUID brokerageAccountId,
            @Param("instrumentKey") String instrumentKey
    );

    @EntityGraph(attributePaths = {"brokerageAccount", "importBatch"})
    @Query(value = "select a from AccountActivity a " + INSTRUMENT_SCOPE + """
            order by a.occurredAt desc, a.importBatch.importSequence desc, a.rowNumber desc, a.id desc
            """, countQuery = "select count(a) from AccountActivity a " + INSTRUMENT_SCOPE)
    Page<AccountActivity> findInstrumentActivities(
            @Param("userId") UUID userId,
            @Param("brokerageAccountId") UUID brokerageAccountId,
            @Param("instrumentKey") String instrumentKey,
            Pageable pageable
    );

    Page<AccountActivity> findByImportBatch_IdOrderByRowNumberAsc(UUID importBatchId, Pageable pageable);

    Page<AccountActivity> findByImportBatch_BrokerageAccount_User_IdAndImportBatch_IdOrderByRowNumberAsc(
            UUID userId,
            UUID importBatchId,
            Pageable pageable
    );

    @EntityGraph(attributePaths = {"brokerageAccount", "importBatch"})
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

    Optional<AccountActivity> findByIdAndBrokerageAccount_User_IdAndBrokerageAccount_Id(
            UUID id, UUID userId, UUID brokerageAccountId
    );

    Optional<AccountActivity> findByBrokerageAccount_IdAndExternalTransactionId(
            UUID brokerageAccountId,
            String externalTransactionId
    );

    boolean existsByBrokerageAccount_IdAndExternalTransactionId(UUID brokerageAccountId, String externalTransactionId);

    boolean existsByBrokerageAccount_IdAndRawRowHash(UUID brokerageAccountId, String rawRowHash);

    long deleteByImportBatch_Id(UUID importBatchId);

    long deleteByBrokerageAccount_Id(UUID brokerageAccountId);
}
