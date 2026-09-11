package com.likehang.alphaforge.repository;

import com.likehang.alphaforge.model.entity.ImportBatch;
import com.likehang.alphaforge.model.entity.ImportBatchStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ImportBatchRepository extends JpaRepository<ImportBatch, UUID> {

    List<ImportBatch> findByBrokerageAccount_IdOrderByCreatedAtDesc(UUID brokerageAccountId);

    Page<ImportBatch> findByBrokerageAccount_User_IdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    Page<ImportBatch> findByBrokerageAccount_User_IdAndBrokerageAccount_IdOrderByCreatedAtDesc(
            UUID userId,
            UUID brokerageAccountId,
            Pageable pageable
    );

    Page<ImportBatch> findByBrokerageAccount_User_IdAndStatusOrderByCreatedAtDesc(
            UUID userId,
            ImportBatchStatus status,
            Pageable pageable
    );

    Optional<ImportBatch> findByBrokerageAccount_User_IdAndId(UUID userId, UUID id);

    Optional<ImportBatch> findByBrokerageAccount_IdAndFileHash(UUID brokerageAccountId, String fileHash);

    boolean existsByBrokerageAccount_IdAndFileHash(UUID brokerageAccountId, String fileHash);
}
