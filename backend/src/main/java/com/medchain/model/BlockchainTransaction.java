package com.medchain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "blockchain_transactions", indexes = {
        @Index(name = "idx_btx_hash", columnList = "txHash", unique = true),
        @Index(name = "idx_btx_record_id", columnList = "recordId")
})
public class BlockchainTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false, unique = true)
    private String txHash;

    private Long blockNumber;

    @Column(nullable = false)
    private String recordId;

    @Column(length = 64)
    private String sha256Hash;

    private String eventType; // RecordUploaded, AccessGranted, AccessRevoked, PseudonymRegistered

    private String requesterPID;
    private String patientPID;
    private String hospitalOrgId = "Hospital A"; // Hospital A / Hospital B / Hospital C

    private boolean verified = true;
    private LocalDateTime timestamp = LocalDateTime.now();
}
