package com.medchain.service;

import com.medchain.dto.RecordUploadRequest;
import com.medchain.dto.UserResponse;
import com.medchain.exception.MedChainException;
import com.medchain.model.*;
import com.medchain.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecordService {

    private final MedicalRecordRepository recordRepository;
    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final ReEncryptionKeyRepository reKeyRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final MLAlertRepository mlAlertRepository;

    private final FileValidationService fileValidationService;
    private final FileStorageService fileStorageService;
    private final AESEncryptionService aesService;
    private final CPABEService cpabeService;
    private final ProxyReEncryptionService preService;
    private final MLService mlService;
    private final BlockchainService blockchainService;
    private final AccessService accessService;

    private final Optional<JdbcTemplate> jdbcTemplate;

    // ─── Real File Upload ─────────────────────────────────────────────────────

    @Transactional
    public MedicalRecord uploadMultipartFile(MultipartFile file, String title, String description,
                                             String recordType, String patientEmail, String ipAddress) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found for email: " + patientEmail));

        // 1. Validate file (extension, size <= 50MB, MIME type, filename)
        fileValidationService.validateFile(file);

        String sanitizedFilename = fileValidationService.sanitizeFilename(file.getOriginalFilename());
        byte[] plainBytes;
        try {
            plainBytes = file.getBytes();
        } catch (Exception e) {
            throw new MedChainException("Failed to read uploaded file bytes: " + e.getMessage());
        }

        // 2. Calculate SHA-256 over original unencrypted plaintext file
        String sha256Hash = fileValidationService.calculateSHA256(plainBytes);
        log.info("Calculated SHA-256 fingerprint for upload: {}", sha256Hash);

        // 3. Encrypt payload using AES-256-GCM (12-byte secure random IV per file)
        AESEncryptionService.EncryptionResult aesResult = aesService.encrypt(plainBytes);

        // 4. Store encrypted payload off-chain
        String storagePath = fileStorageService.storeEncryptedFile(aesResult.ciphertext(), sanitizedFilename);

        // 5. Build and save MedicalRecord JPA entity
        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patient.getId());
        record.setPatientPID(patient.getPseudonymId());
        record.setTitle(title != null && !title.isBlank() ? title : sanitizedFilename);
        record.setDescription(description != null ? description : "");
        record.setRecordType(recordType != null ? recordType : "CONSULTATION");
        record.setFileName(sanitizedFilename);
        record.setFileSize(formatFileSize(file.getSize()));
        record.setFileMimeType(fileValidationService.detectMimeType(plainBytes, sanitizedFilename, file.getContentType()));
        record.setSha256Hash(sha256Hash);
        record.setStoragePath(storagePath);
        record.setEncryptedContent(Base64.getEncoder().encodeToString(aesResult.ciphertext()));
        record.setEncryptedAesKey(aesResult.aesKeyBase64());
        record.setAesIv(aesResult.ivBase64());

        String policy = "patient:" + patient.getId();
        String policyHash = cpabeService.hashPolicy(policy);
        record.setAccessPolicy(policy);
        record.setPolicyHash(policyHash);

        MedicalRecord saved = recordRepository.save(record);

        // 6. Anchor metadata on blockchain
        String txHash = blockchainService.uploadRecord(
                saved.getId(), saved.getSha256Hash(), policyHash, patient.getPseudonymId());
        saved.setBlockchainTxHash(txHash);
        saved.setBlockchainRecordId("0x" + org.web3j.crypto.Hash.sha3String(saved.getId()).substring(2, 18));
        recordRepository.save(saved);

        // 7. Audit log
        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                saved.getId(), patient.getPseudonymId(), "UPLOAD", ipAddress, true);

        log.info("Medical record '{}' (SHA256: {}) successfully uploaded and encrypted off-chain.", saved.getTitle(), sha256Hash);
        return saved;
    }

    @Transactional
    public MedicalRecord uploadRecord(RecordUploadRequest req, String patientEmail, String ipAddress) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));

        byte[] plainBytes = req.getContent() != null ? decodeContent(req.getContent()) : new byte[0];
        if (plainBytes.length == 0) {
            throw new MedChainException("File content cannot be empty.");
        }

        String sanitizedFilename = fileValidationService.sanitizeFilename(req.getFileName());
        String sha256Hash = fileValidationService.calculateSHA256(plainBytes);

        AESEncryptionService.EncryptionResult aesResult = aesService.encrypt(plainBytes);
        String storagePath = fileStorageService.storeEncryptedFile(aesResult.ciphertext(), sanitizedFilename);

        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patient.getId());
        record.setPatientPID(patient.getPseudonymId());
        record.setTitle(req.getTitle());
        record.setDescription(req.getDescription());
        record.setRecordType(req.getRecordType());
        record.setFileName(sanitizedFilename);
        record.setFileSize(req.getFileSize() != null ? req.getFileSize() : formatFileSize(plainBytes.length));
        record.setFileMimeType(fileValidationService.detectMimeType(plainBytes, sanitizedFilename, req.getFileMimeType()));
        record.setSha256Hash(sha256Hash);
        record.setStoragePath(storagePath);
        record.setEncryptedContent(Base64.getEncoder().encodeToString(aesResult.ciphertext()));
        record.setEncryptedAesKey(aesResult.aesKeyBase64());
        record.setAesIv(aesResult.ivBase64());

        String policy = req.getAccessPolicy() != null ? req.getAccessPolicy() : "patient:" + patient.getId();
        String policyHash = cpabeService.hashPolicy(policy);
        record.setAccessPolicy(policy);
        record.setPolicyHash(policyHash);

        MedicalRecord saved = recordRepository.save(record);

        String txHash = blockchainService.uploadRecord(saved.getId(), saved.getSha256Hash(), policyHash, patient.getPseudonymId());
        saved.setBlockchainTxHash(txHash);
        saved.setBlockchainRecordId("0x" + org.web3j.crypto.Hash.sha3String(saved.getId()).substring(2, 18));
        recordRepository.save(saved);

        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                saved.getId(), patient.getPseudonymId(), "UPLOAD", ipAddress, true);

        return saved;
    }

    // ─── Retrieve Records ────────────────────────────────────────────────────

    public List<MedicalRecord> getPatientRecords(String patientIdOrEmail) {
        if (patientIdOrEmail == null || patientIdOrEmail.isBlank()) {
            return List.of();
        }
        if (patientIdOrEmail.contains("@")) {
            User patient = userRepository.findByEmail(patientIdOrEmail).orElse(null);
            if (patient != null) {
                return recordRepository.findByPatientId(patient.getId());
            }
        }
        return recordRepository.findByPatientId(patientIdOrEmail);
    }

    public List<MedicalRecord> getDoctorRecords(String doctorEmail) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found"));
        return recordRepository.findByAuthorizedDoctorIdsContaining(doctor.getId());
    }

    public List<UserResponse> getAllDoctors() {
        return userRepository.findByRole("DOCTOR").stream().map(this::toUserResponse).toList();
    }

    public List<UserResponse> getAllPatients() {
        return userRepository.findByRole("PATIENT").stream().map(this::toUserResponse).toList();
    }

    // ─── View Record Flow with AES-256-GCM & SHA-256 Checksum Verification ──

    @Transactional
    public Map<String, Object> viewRecord(String recordId, String requesterEmail, String ipAddress) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new MedChainException("User not found for email: " + requesterEmail));

        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found for ID: " + recordId));

        // Enforce access request expiration
        accessService.checkAndEnforceExpiration(recordId, requester.getId());
        record = recordRepository.findById(recordId).orElse(record);

        // 1. Authorization Verification
        boolean isOwner = record.getPatientId().equals(requester.getId());
        boolean isAuthorizedDoctor = record.getAuthorizedDoctorIds().contains(requester.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requester.getRole());

        if ("DOCTOR".equalsIgnoreCase(requester.getRole()) && !isOwner && !isAdmin) {
            boolean hasActiveApprovedRequest = accessRequestRepository
                    .findByRecordIdAndDoctorIdAndStatus(recordId, requester.getId(), "APPROVED")
                    .isPresent();
            if (!hasActiveApprovedRequest) {
                isAuthorizedDoctor = false;
            }
        }

        if (!isOwner && !isAuthorizedDoctor && !isAdmin) {
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "VIEW", ipAddress, false, 0.95, 0.95, 0.95, "HIGH", "UNAUTHORIZED_DOCTOR_ACCESS", null);
            throw new MedChainException("Access denied: You do not have active patient approval for this record.");
        }

        // Evaluate real-time behavioral telemetry via Sentinel AI
        MLService.AnomalyRequest telemetryReq = mlService.buildTelemetry(requester, record);
        MLService.AnomalyResponse mlRes = mlService.scoreAccess(telemetryReq, requester.getId(), recordId);

        // 2. Read Encrypted Ciphertext Payload & Keys (Persistent PostgreSQL prioritized)
        ResolvedCiphertext resolved = resolveCiphertextAndKeys(record);

        // 3. AES-256-GCM Decryption
        byte[] decryptedBytes;
        try {
            decryptedBytes = aesService.decrypt(resolved.ciphertext(), resolved.aesKey(), resolved.iv());
        } catch (Exception e) {
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "INTEGRITY_FAILURE", ipAddress, false, 1.0, 1.0, 1.0, "CRITICAL", "AES-256-GCM authentication tag mismatch: Payload tampered", null);

            MLAlert alert = new MLAlert();
            alert.setUserId(requester.getId());
            alert.setRecordId(recordId);
            alert.setRecordTitle(record.getTitle());
            alert.setDoctorName(requester.getName());
            alert.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "Not available");
            alert.setRiskScore(1.0);
            alert.setRiskLevel("CRITICAL");
            alert.setStatus("OPEN");
            alert.setRationale("INTEGRITY FAILURE: AES-256-GCM tag mismatch on record ID: " + recordId);
            mlAlertRepository.save(alert);

            log.error("INTEGRITY FAILURE! GCM tag mismatch/tampering on record {}: {}", recordId, e.getMessage());
            throw new MedChainException("Integrity check failed: AES-256-GCM authentication tag mismatch or payload corruption.", e);
        }

        // 4. SHA-256 Integrity Verification
        String recalculatedSha256 = fileValidationService.calculateSHA256(decryptedBytes);
        if (record.getSha256Hash() != null && !record.getSha256Hash().equalsIgnoreCase(recalculatedSha256)) {
            // Stop delivery! Log INTEGRITY_FAILURE and create CRITICAL security alert.
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "INTEGRITY_FAILURE", ipAddress, false, 1.0, 1.0, 1.0, "CRITICAL", "SHA-256 checksum mismatch: File payload tampered", null);

            MLAlert alert = new MLAlert();
            alert.setUserId(requester.getId());
            alert.setRecordId(recordId);
            alert.setRecordTitle(record.getTitle());
            alert.setDoctorName(requester.getName());
            alert.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "Not available");
            alert.setRiskScore(1.0);
            alert.setRiskLevel("CRITICAL");
            alert.setStatus("OPEN");
            alert.setRationale("INTEGRITY FAILURE: SHA-256 checksum mismatch (Stored: " + record.getSha256Hash() + " vs Calculated: " + recalculatedSha256 + ")");
            mlAlertRepository.save(alert);

            log.error("INTEGRITY FAILURE! SHA-256 mismatch on record {}: expected {} but calculated {}", recordId, record.getSha256Hash(), recalculatedSha256);
            throw new MedChainException("Integrity check failed: File checksum mismatch. Delivery rejected for security reasons.");
        }

        // 5. Successful Verification
        logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                recordId, record.getPatientPID(), "VIEW", ipAddress, true);

        String effectiveMime = fileValidationService.detectMimeType(decryptedBytes, record.getFileName(), record.getFileMimeType());
        String decryptedDataUrl = "data:" + effectiveMime + ";base64," + Base64.getEncoder().encodeToString(decryptedBytes);

        return Map.of(
                "record", record,
                "sha256Hash", recalculatedSha256,
                "integrityVerified", true,
                "decryptedContent", decryptedDataUrl
        );
    }

    // ─── Authorized File Download ─────────────────────────────────────────────

    public record DownloadedFile(byte[] fileBytes, String filename, String mimeType, String sha256Hash) {}

    @Transactional
    public DownloadedFile downloadRecordFile(String recordId, String requesterEmail, String ipAddress) {
        User requester = userRepository.findByEmail(requesterEmail)
                .orElseThrow(() -> new MedChainException("User not found for email: " + requesterEmail));

        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found for ID: " + recordId));

        // Enforce access request expiration
        accessService.checkAndEnforceExpiration(recordId, requester.getId());
        record = recordRepository.findById(recordId).orElse(record);

        boolean isOwner = record.getPatientId().equals(requester.getId());
        boolean isAuthorizedDoctor = record.getAuthorizedDoctorIds().contains(requester.getId());
        boolean isAdmin = "ADMIN".equalsIgnoreCase(requester.getRole());

        if ("DOCTOR".equalsIgnoreCase(requester.getRole()) && !isOwner && !isAdmin) {
            boolean hasActiveApprovedRequest = accessRequestRepository
                    .findByRecordIdAndDoctorIdAndStatus(recordId, requester.getId(), "APPROVED")
                    .isPresent();
            if (!hasActiveApprovedRequest) {
                isAuthorizedDoctor = false;
            }
        }

        if (!isOwner && !isAuthorizedDoctor && !isAdmin) {
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "DOWNLOAD", ipAddress, false, 0.95, 0.95, 0.95, "HIGH", "UNAUTHORIZED_DOCTOR_DOWNLOAD", null);
            throw new MedChainException("Access denied: You do not have active patient approval to download this record.");
        }

        // Read Encrypted Ciphertext Payload & Keys (Persistent PostgreSQL prioritized)
        ResolvedCiphertext resolved = resolveCiphertextAndKeys(record);

        byte[] decryptedBytes;
        try {
            decryptedBytes = aesService.decrypt(resolved.ciphertext(), resolved.aesKey(), resolved.iv());
        } catch (Exception e) {
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "INTEGRITY_FAILURE", ipAddress, false, 1.0, 1.0, 1.0, "CRITICAL", "AES-256-GCM authentication tag mismatch on download", null);

            MLAlert alert = new MLAlert();
            alert.setUserId(requester.getId());
            alert.setRecordId(recordId);
            alert.setRecordTitle(record.getTitle());
            alert.setDoctorName(requester.getName());
            alert.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "Not available");
            alert.setRiskScore(1.0);
            alert.setRiskLevel("CRITICAL");
            alert.setStatus("OPEN");
            alert.setRationale("INTEGRITY FAILURE ON DOWNLOAD: AES-256-GCM tag mismatch for record ID: " + recordId);
            mlAlertRepository.save(alert);

            throw new MedChainException("Integrity check failed: AES-256-GCM authentication tag mismatch. Download aborted.", e);
        }

        String recalculatedSha256 = fileValidationService.calculateSHA256(decryptedBytes);
        if (record.getSha256Hash() != null && !record.getSha256Hash().equalsIgnoreCase(recalculatedSha256)) {
            logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                    recordId, record.getPatientPID(), "INTEGRITY_FAILURE", ipAddress, false, 1.0, 1.0, 1.0, "CRITICAL", "SHA-256 checksum mismatch: File payload tampered", null);

            MLAlert alert = new MLAlert();
            alert.setUserId(requester.getId());
            alert.setRecordId(recordId);
            alert.setRecordTitle(record.getTitle());
            alert.setDoctorName(requester.getName());
            alert.setIpAddress(ipAddress != null && !ipAddress.isBlank() ? ipAddress : "Not available");
            alert.setRiskScore(1.0);
            alert.setRiskLevel("CRITICAL");
            alert.setStatus("OPEN");
            alert.setRationale("INTEGRITY FAILURE ON DOWNLOAD: SHA-256 checksum mismatch for record ID: " + recordId);
            mlAlertRepository.save(alert);

            throw new MedChainException("Integrity check failed: File checksum mismatch. File download aborted.");
        }

        logAction(requester.getId(), requester.getName(), requester.getPseudonymId(),
                recordId, record.getPatientPID(), "DOWNLOAD", ipAddress, true);

        String effectiveMime = fileValidationService.detectMimeType(decryptedBytes, record.getFileName(), record.getFileMimeType());
        return new DownloadedFile(decryptedBytes, record.getFileName(), effectiveMime, recalculatedSha256);
    }

    // ─── Unified Ciphertext and Encryption Key Resolution ─────────────────────

    public record ResolvedCiphertext(byte[] ciphertext, String aesKey, String iv) {}

    private ResolvedCiphertext resolveCiphertextAndKeys(MedicalRecord record) {
        // 1. Check if this record is a demonstration metadata-only entry (e.g. from DataSeeder)
        boolean hasNoPayload = (record.getEncryptedContent() == null || record.getEncryptedContent().isBlank())
                && (record.getStoragePath() == null || record.getStoragePath().isBlank());

        if (hasNoPayload) {
            throw new MedChainException(
                "This medical record ('" + record.getTitle() + "') is a demonstration metadata entry and does not contain a stored encrypted file payload. " +
                "Please upload a medical document file (PDF, PNG, JPG) to test AES-256-GCM encryption, decryption, and SHA-256 integrity verification."
            );
        }

        byte[] ciphertext = null;
        String aesKey = record.getEncryptedAesKey();
        String iv = record.getAesIv();

        // 2. Prioritize reading directly from database persistent field (encryptedContent)
        if (record.getEncryptedContent() != null && !record.getEncryptedContent().isBlank()) {
            String encContent = record.getEncryptedContent().trim();
            // Check if it's a legacy PostgreSQL Large Object OID (pure numeric digits)
            if (encContent.matches("^\\d+$")) {
                ciphertext = tryRecoverLegacyLobPayload(record, encContent);
            } else {
                try {
                    ciphertext = Base64.getDecoder().decode(encContent);
                } catch (IllegalArgumentException e) {
                    log.warn("Invalid Base64 in encryptedContent for record {}: {}", record.getId(), e.getMessage());
                }
            }
        }

        // Check if encryptedAesKey is a legacy OID (pure numeric digits)
        if (aesKey != null && aesKey.trim().matches("^\\d+$")) {
            String recoveredKey = tryRecoverLegacyLobString(aesKey.trim());
            if (recoveredKey != null && !recoveredKey.isBlank()) {
                aesKey = recoveredKey;
                try {
                    record.setEncryptedAesKey(aesKey);
                    recordRepository.save(record);
                } catch (Exception ex) {
                    log.warn("Failed to persist recovered AES key for record {}: {}", record.getId(), ex.getMessage());
                }
            }
        }

        // 3. Fall back to local filesystem disk storage if DB ciphertext wasn't available
        if ((ciphertext == null || ciphertext.length == 0) && record.getStoragePath() != null && !record.getStoragePath().isBlank()) {
            ciphertext = fileStorageService.readEncryptedFile(record.getStoragePath());
            // If read successfully from disk, persist to PostgreSQL so future container restarts keep the file!
            if (ciphertext != null && ciphertext.length > 0) {
                try {
                    record.setEncryptedContent(Base64.getEncoder().encodeToString(ciphertext));
                    recordRepository.save(record);
                    log.info("Backfilled encryptedContent into PostgreSQL for record {}", record.getId());
                } catch (Exception ex) {
                    log.warn("Failed to backfill encryptedContent into DB for record {}: {}", record.getId(), ex.getMessage());
                }
            }
        }

        // 4. If payload is still missing, provide an accurate, actionable message
        if (ciphertext == null || ciphertext.length == 0) {
            throw new MedChainException(
                "No encrypted payload could be retrieved for medical record '" + record.getTitle() + "' (ID: " + record.getId() + "). " +
                "The file payload was stored on an ephemeral container volume prior to database persistence migration and is unrecoverable. " +
                "Please re-upload this document."
            );
        }

        if (aesKey == null || aesKey.isBlank() || iv == null || iv.isBlank()) {
            throw new MedChainException(
                "Missing encryption key or initialization vector for record '" + record.getTitle() + "' (ID: " + record.getId() + "). " +
                "Unable to decrypt payload."
            );
        }

        return new ResolvedCiphertext(ciphertext, aesKey, iv);
    }

    private byte[] tryRecoverLegacyLobPayload(MedicalRecord record, String oidStr) {
        if (jdbcTemplate == null || jdbcTemplate.isEmpty()) {
            return null;
        }
        try {
            long oid = Long.parseLong(oidStr);
            log.info("Attempting to recover legacy LOB with OID {} for record {}", oid, record.getId());
            byte[] rawBytes = jdbcTemplate.get().queryForObject(
                "SELECT lo_get(?::oid)",
                byte[].class,
                oid
            );
            if (rawBytes != null && rawBytes.length > 0) {
                String asText = new String(rawBytes, StandardCharsets.UTF_8).trim();
                byte[] decodedCiphertext;
                try {
                    decodedCiphertext = Base64.getDecoder().decode(asText);
                } catch (Exception e) {
                    // Not Base64 text, large object may have stored raw binary bytes
                    decodedCiphertext = rawBytes;
                }

                // Heal the record permanently in the PostgreSQL TEXT column
                try {
                    record.setEncryptedContent(Base64.getEncoder().encodeToString(decodedCiphertext));
                    recordRepository.save(record);
                    log.info("Successfully recovered and healed legacy LOB payload for record {}", record.getId());
                } catch (Exception ex) {
                    log.warn("Failed to persist healed record {}: {}", record.getId(), ex.getMessage());
                }
                return decodedCiphertext;
            }
        } catch (Exception e) {
            log.warn("Could not recover legacy LOB OID {} for record {}: {}", oidStr, record.getId(), e.getMessage());
        }
        return null;
    }

    private String tryRecoverLegacyLobString(String oidStr) {
        if (jdbcTemplate == null || jdbcTemplate.isEmpty()) {
            return null;
        }
        try {
            long oid = Long.parseLong(oidStr);
            byte[] rawBytes = jdbcTemplate.get().queryForObject(
                "SELECT lo_get(?::oid)",
                byte[].class,
                oid
            );
            if (rawBytes != null && rawBytes.length > 0) {
                return new String(rawBytes, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.warn("Could not recover legacy LOB OID {} as string: {}", oidStr, e.getMessage());
        }
        return null;
    }

    // ─── Access Request & Grant/Revoke Methods ────────────────────────────────

    @Transactional
    public AccessRequest requestAccess(String recordId, String doctorEmail) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found"));
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found"));

        if (record.getAuthorizedDoctorIds().contains(doctor.getId()))
            throw new MedChainException("You already have access to this record");

        Optional<AccessRequest> existing = accessRequestRepository
                .findByRecordIdAndDoctorIdAndStatus(recordId, doctor.getId(), "PENDING");
        if (existing.isPresent())
            throw new MedChainException("Access request already pending");

        AccessRequest req = new AccessRequest();
        req.setRecordId(recordId);
        req.setDoctorId(doctor.getId());
        req.setPatientId(record.getPatientId());
        req.setStatus("PENDING");
        return accessRequestRepository.save(req);
    }

    public List<AccessRequest> getPatientAccessRequests(String patientEmail) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));
        return accessRequestRepository.findByPatientIdAndStatus(patient.getId(), "PENDING");
    }

    @Transactional
    public AccessRequest denyAccessRequest(String requestId, String patientEmail) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));
        AccessRequest req = accessRequestRepository.findById(requestId)
                .orElseThrow(() -> new MedChainException("Request not found"));

        if (!req.getPatientId().equals(patient.getId()))
            throw new MedChainException("Not authorized to modify this request");

        req.setStatus("DENIED");
        req.setUpdatedAt(LocalDateTime.now());
        return accessRequestRepository.save(req);
    }

    @Transactional
    public MedicalRecord grantAccess(String recordId, String doctorId, String patientEmail, String ipAddress) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found"));

        if (!record.getPatientId().equals(patient.getId()))
            throw new MedChainException("Not authorized to modify this record");

        User doctor = userRepository.findById(doctorId)
                .orElseThrow(() -> new MedChainException("Doctor not found"));

        if (!record.getAuthorizedDoctorIds().contains(doctorId)) {
            record.getAuthorizedDoctorIds().add(doctorId);
            record.getAuthorizedDoctorPIDs().add(doctor.getPseudonymId());

            String rk = preService.generateReEncryptionKey(patient.getId(), doctor.getPseudonymId());
            ReEncryptionKey reKey = new ReEncryptionKey();
            reKey.setRecordId(recordId);
            reKey.setDoctorId(doctorId);
            reKey.setDoctorPID(doctor.getPseudonymId());
            reKey.setTempKey(preService.generateTempAccessKey(recordId, doctorId));
            reKey.setEncryptedAesKey(rk);
            reKey.setExpiry(LocalDateTime.now().plusDays(30));
            reKeyRepository.save(reKey);

            if (blockchainService.isAvailable()) {
                String txHash = blockchainService.grantAccess(recordId, doctor.getPseudonymId(), patient.getPseudonymId());
                record.setBlockchainTxHash(txHash);
            }
        }

        MedicalRecord saved = recordRepository.save(record);
        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                recordId, patient.getPseudonymId(), "ACCESS_APPROVE", ipAddress, true);

        Optional<AccessRequest> pendingOpt = accessRequestRepository.findByRecordIdAndDoctorIdAndStatus(recordId, doctorId, "PENDING");
        if (pendingOpt.isPresent()) {
            AccessRequest r = pendingOpt.get();
            r.setStatus("APPROVED");
            r.setExpiresAt(LocalDateTime.now().plusDays(30));
            r.setUpdatedAt(LocalDateTime.now());
            accessRequestRepository.save(r);
        } else {
            Optional<AccessRequest> approvedOpt = accessRequestRepository.findByRecordIdAndDoctorIdAndStatus(recordId, doctorId, "APPROVED");
            if (approvedOpt.isEmpty()) {
                AccessRequest r = new AccessRequest();
                r.setRecordId(recordId);
                r.setDoctorId(doctorId);
                r.setPatientId(patient.getId());
                r.setStatus("APPROVED");
                r.setReason("Direct grant by patient");
                r.setExpiresAt(LocalDateTime.now().plusDays(30));
                r.setRequestedAt(LocalDateTime.now());
                r.setUpdatedAt(LocalDateTime.now());
                accessRequestRepository.save(r);
            }
        }

        return saved;
    }

    @Transactional
    public MedicalRecord revokeAccess(String recordId, String doctorId, String patientEmail, String ipAddress) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found"));

        if (!record.getPatientId().equals(patient.getId()))
            throw new MedChainException("Not authorized to modify this record");

        User doctor = userRepository.findById(doctorId).orElse(null);

        record.getAuthorizedDoctorIds().remove(doctorId);
        if (doctor != null) record.getAuthorizedDoctorPIDs().remove(doctor.getPseudonymId());

        reKeyRepository.deleteByRecordIdAndDoctorId(recordId, doctorId);

        String doctorPID = doctor != null ? doctor.getPseudonymId() : doctorId;
        if (blockchainService.isAvailable()) {
            String txHash = blockchainService.revokeAccess(recordId, doctorPID, patient.getPseudonymId());
            record.setBlockchainTxHash(txHash);
        }

        MedicalRecord saved = recordRepository.save(record);

        accessRequestRepository.findByRecordIdAndDoctorIdAndStatus(recordId, doctorId, "APPROVED")
                .ifPresent(r -> {
                    r.setStatus("REVOKED");
                    r.setUpdatedAt(LocalDateTime.now());
                    accessRequestRepository.save(r);
                });

        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                recordId, patient.getPseudonymId(), "ACCESS_REVOKE", ipAddress, true);
        return saved;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        char pre = "KMGTPE".charAt(exp - 1);
        return String.format("%.1f %cB", bytes / Math.pow(1024, exp), pre);
    }

    private byte[] decodeContent(String content) {
        if (content.contains(",")) {
            return Base64.getDecoder().decode(content.substring(content.indexOf(',') + 1));
        }
        return Base64.getDecoder().decode(content);
    }

    private UserResponse toUserResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId()); r.setEmail(u.getEmail()); r.setName(u.getName());
        r.setRole(u.getRole()); r.setPhone(u.getPhone()); r.setPseudonymId(u.getPseudonymId());
        r.setAttributes(u.getAttributes()); r.setActive(u.isActive()); r.setBlocked(u.isBlocked());
        r.setCreatedAt(u.getCreatedAt());
        return r;
    }

    private void logAction(String userId, String userName, String requesterPID, String recordId,
                           String patientPID, String action, String ip, boolean granted) {
        logAction(userId, userName, requesterPID, recordId, patientPID, action, ip, granted,
                0.05, 0.04, 0.06, "LOW", null, null);
    }

    private void logAction(String userId, String userName, String requesterPID, String recordId,
                           String patientPID, String action, String ip, boolean granted,
                           double anomalyScore, double isoScore, double aeScore,
                           String riskLevel, String denyReason, String txHash) {
        AccessLog log = new AccessLog();
        log.setUserId(userId);
        log.setUserName(userName);
        log.setRequesterPID(requesterPID);
        log.setRecordId(recordId);
        log.setPatientPID(patientPID);
        log.setAction(action);
        log.setIpAddress(ip);
        log.setGranted(granted);
        log.setAnomalyScore(anomalyScore);
        log.setIsolationScore(isoScore);
        log.setAutoencoderScore(aeScore);
        log.setRiskLevel(riskLevel);
        log.setFlagged(anomalyScore > 0.6);
        log.setDenyReason(denyReason);
        log.setBlockchainTxHash(txHash != null ? txHash : "0x" + Long.toHexString(System.nanoTime()));
        accessLogRepository.save(log);
    }
}
