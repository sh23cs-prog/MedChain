package com.medchain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * MedicalRecord — represents a patient's medical document.
 *
 * Security pipeline:
 *   1. File is AES-256-GCM encrypted on upload (aesKey + iv stored here)
 *   2. Encrypted bytes are uploaded to IPFS (ipfsCid stored here)
 *   3. AES key is CP-ABE policy-protected (encryptedAesKey stores CP-ABE ciphertext)
 *   4. Blockchain stores: recordId hash, ipfsCid hash, policyHash, patientPID
 */
@Data
@Document(collection = "medical_records")
public class MedicalRecord {

    @Id
    private String id;

    // ─── Patient ──────────────────────────────────────────────────────────────
    private String patientId;
    private String patientPID;       // SHA3-256 pseudonym (never real identity on-chain)

    // ─── Record Metadata ──────────────────────────────────────────────────────
    private String title;
    private String description;
    private String recordType;       // LAB_REPORT, PRESCRIPTION, SCAN, DISCHARGE, CONSULTATION

    // ─── File Info ────────────────────────────────────────────────────────────
    private String fileName;
    private String fileSize;
    private String fileMimeType;

    // ─── IPFS Storage ─────────────────────────────────────────────────────────
    private String ipfsCid;          // IPFS Content Identifier (encrypted file)

    // ─── AES-256-GCM Encryption ───────────────────────────────────────────────
    private String encryptedContent; // Base64 AES-GCM ciphertext (for text records)
    private String encryptedAesKey;  // CP-ABE encrypted AES key (base64)
    private String aesIv;            // AES-GCM IV (base64, 12 bytes)

    // ─── CP-ABE Access Control ────────────────────────────────────────────────
    private String accessPolicy;     // e.g., "specialty:CARDIOLOGY AND hospital:GENERAL"
    private String policyHash;       // SHA3-256 of accessPolicy

    // ─── Proxy Re-Encryption ──────────────────────────────────────────────────
    private String reEncryptionKey;

    // ─── Blockchain Anchoring ─────────────────────────────────────────────────
    private String blockchainRecordId;   // keccak256 of record ID
    private String blockchainTxHash;     // last TX hash

    // ─── Access Control Lists ─────────────────────────────────────────────────
    private List<String> authorizedDoctorIds  = new ArrayList<>();
    private List<String> authorizedDoctorPIDs = new ArrayList<>();

    // ─── Status ───────────────────────────────────────────────────────────────
    private boolean active = true;
    private LocalDateTime uploadedAt = LocalDateTime.now();
}
