package com.medchain.repository;

import com.medchain.model.BlockchainTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BlockchainTransactionRepository extends JpaRepository<BlockchainTransaction, String> {
    Optional<BlockchainTransaction> findByTxHash(String txHash);
    Optional<BlockchainTransaction> findTopByRecordIdOrderByTimestampDesc(String recordId);
    List<BlockchainTransaction> findByRecordId(String recordId);
    List<BlockchainTransaction> findByHospitalOrgId(String hospitalOrgId);
}
