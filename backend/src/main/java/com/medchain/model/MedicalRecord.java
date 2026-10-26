package com.medchain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "medical_records", indexes = {
        @Index(name = "idx_record_patient_id", columnList = "patientId"),
        @Index(name = "idx_record_patient_pid", columnList = "patientPID"),
        @Index(name = "idx_record_sha256", columnList = "sha256Hash")
})
public class MedicalRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    // ─── Patient ──────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String patientId;

    @Column(nullable = false)
    private String patientPID;

    // ─── Record Metadata ──────────────────────────────────────────────────────
    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private String recordType; // LAB_REPORT, PRESCRIPTION, SCAN, DISCHARGE, CONSULTATION

    // ─── File Info ────────────────────────────────────────────────────────────
    @Column(nullable = false)
    private String fileName;
    private String fileSize;
    private String fileMimeType;
    private String storagePath; // Path on disk / storage

    // ─── Integrity Fingerprint ───────────────────────────────────────────────
    @Column(length = 64)
    private String sha256Hash; // Calculated on original plaintext file

    // ─── IPFS & Off-Chain Storage ─────────────────────────────────────────────
    private String ipfsCid;

    // ─── AES-256-GCM Encryption ───────────────────────────────────────────────
    @Column(columnDefinition = "TEXT")
    private String encryptedContent; // Base64 AES-GCM ciphertext

    @Column(columnDefinition = "TEXT")
    private String encryptedAesKey; // CP-ABE / PRE encrypted key

    private String aesIv; // AES-GCM 12-byte random IV (base64)

    // ─── CP-ABE Policy ────────────────────────────────────────────────────────
    private String accessPolicy;
    private String policyHash;

    // ─── Proxy Re-Encryption ──────────────────────────────────────────────────
    private String reEncryptionKey;

    // ─── Blockchain Anchoring ─────────────────────────────────────────────────
    private String blockchainRecordId;
    private String blockchainTxHash;
    private String hospitalOrgId = "Hospital A"; // Default multi-hospital org ID

    // ─── Access Control Lists ─────────────────────────────────────────────────
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "record_authorized_doctors", joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "doctor_id")
    private List<String> authorizedDoctorIds = new ArrayList<>();

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "record_authorized_doctor_pids", joinColumns = @JoinColumn(name = "record_id"))
    @Column(name = "doctor_pid")
    private List<String> authorizedDoctorPIDs = new ArrayList<>();

    // ─── Status ───────────────────────────────────────────────────────────────
    private boolean active = true;
    private LocalDateTime uploadedAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
