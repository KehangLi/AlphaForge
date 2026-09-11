package com.likehang.alphaforge.repository;

import com.likehang.alphaforge.model.entity.BrokerageAccount;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface BrokerageAccountRepository extends JpaRepository<BrokerageAccount, UUID> {

    List<BrokerageAccount> findByUser_IdOrderByCreatedAtDesc(UUID userId);

    Optional<BrokerageAccount> findByIdAndUser_Id(UUID id, UUID userId);

    Optional<BrokerageAccount> findByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
            UUID userId,
            String brokerName,
            String accountName
    );

    boolean existsByUser_IdAndBrokerNameIgnoreCaseAndAccountNameIgnoreCase(
            UUID userId,
            String brokerName,
            String accountName
    );
}
