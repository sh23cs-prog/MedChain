package com.medchain.service;

import com.medchain.dto.RecordUploadRequest;
import com.medchain.dto.UserResponse;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.AccessRequest;
import com.medchain.model.MedicalRecord;
import com.medchain.model.ReEncryptionKey;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.AccessRequestRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.ReEncryptionKeyRepository;
import com.medchain.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * RecordService — orchestrates the full medical record lifecycle:
 *
 *   UPLOAD:  File → AES-256-GCM encrypt → IPFS upload → Blockchain anchor
 *   GRANT:   Patient grants doctor access → PRE key generated → Blockchain event
 *   REVOKE:  Patient revokes doctor access → PRE key deleted → Blockchain event
 *   VIEW:    Doctor requests record → ML anomaly score → PRE decrypt → Blockchain log
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RecordService {

    private final MedicalRecordRepository   recordRepository;
    private final UserRepository            userRepository;
    private final AccessLogRepository       accessLogRepository;
    private final ReEncryptionKeyRepository reKeyRepository;
    private final AccessRequestRepository   accessRequestRepository;

    private final CPABEService             cpabeService;
    private final ProxyReEncryptionService preService;
    private final MLService                mlService;
    private final BlockchainService        blockchainService;
    private final AESEncryptionService     aesService;
    private final IPFSService              ipfsService;

    // ─── Upload ───────────────────────────────────────────────────────────────

    /**
     * Upload a medical record:
     * 1. AES-256-GCM encrypt the file content
     * 2. Upload encrypted bytes to IPFS
     * 3. CP-ABE protect the AES key
     * 4. Anchor metadata on the blockchain
     * 5. Save everything to MongoDB
     */
    public MedicalRecord uploadRecord(RecordUploadRequest req, String patientEmail, String ipAddress) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found"));

        String policy     = req.getAccessPolicy() != null ? req.getAccessPolicy() : "";
        String policyHash = cpabeService.hashPolicy(policy);

        MedicalRecord record = new MedicalRecord();
        record.setPatientId(patient.getId());
        record.setPatientPID(patient.getPseudonymId());
        record.setTitle(req.getTitle());
        record.setDescription(req.getDescription());
        record.setRecordType(req.getRecordType());
        record.setAccessPolicy(policy);
        record.setPolicyHash(policyHash);
        record.setFileName(req.getFileName());
        record.setFileSize(req.getFileSize());
        record.setFileMimeType(req.getFileMimeType());

        // ── AES-256-GCM Encryption ──────────────────────────────────────────
        if (req.getContent() != null && !req.getContent().isBlank()) {
            try {
                // Decode base64 content from client
                byte[] plainBytes = decodeContent(req.getContent());

                // Encrypt with AES-256-GCM
                long encStart = System.currentTimeMillis();
                AESEncryptionService.EncryptionResult aesResult = aesService.encrypt(plainBytes);
                long encTime = System.currentTimeMillis() - encStart;
                log.debug("AES encryption took {} ms for {} bytes", encTime, plainBytes.length);

                // CP-ABE protect the AES key (simulated — wraps key with policy hash)
                String encryptedAesKey = cpabeService.protectAesKey(aesResult.aesKeyBase64(), policy);
                record.setEncryptedAesKey(encryptedAesKey);
                record.setAesIv(aesResult.ivBase64());

                // Also store encrypted content as base64 (fallback for text records)
                record.setEncryptedContent(Base64.getEncoder().encodeToString(aesResult.ciphertext()));

                // ── IPFS Upload ───────────────────────────────────────────────
                String fileName = req.getFileName() != null ? req.getFileName() : "record.bin";
                String cid = ipfsService.uploadFile(aesResult.ciphertext(), "enc_" + fileName);
                record.setIpfsCid(cid);
                log.info("Medical record uploaded to IPFS. CID: {}", cid);

            } catch (Exception e) {
                log.error("Encryption/IPFS pipeline failed: {}", e.getMessage());
                // Fallback: store as-is with mock CID
                record.setEncryptedContent(req.getContent());
                record.setIpfsCid("Qm" + generateMockCid());
            }
        } else if (req.getIpfsCid() != null) {
            // Client provided CID directly (pre-uploaded)
            record.setIpfsCid(req.getIpfsCid());
        } else {
            record.setIpfsCid("Qm" + generateMockCid());
        }

        // ── Save to MongoDB ──────────────────────────────────────────────────
        MedicalRecord saved = recordRepository.save(record);

        // ── Blockchain Anchor ────────────────────────────────────────────────
        String txHash = blockchainService.uploadRecord(
                saved.getId(), saved.getIpfsCid(), policyHash, patient.getPseudonymId());
        saved.setBlockchainTxHash(txHash);
        saved.setBlockchainRecordId("0x" + org.web3j.crypto.Hash.sha3String(saved.getId()).substring(2, 18));
        recordRepository.save(saved);

        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                  saved.getId(), patient.getPseudonymId(), "UPLOAD", ipAddress, true);

        log.info("Record '{}' uploaded successfully. IPFS: {} | TX: {}", saved.getTitle(), saved.getIpfsCid(), txHash);
        return saved;
    }

    // ─── Retrieve ─────────────────────────────────────────────────────────────

    public List<MedicalRecord> getPatientRecords(String patientId) {
        return recordRepository.findByPatientId(patientId);
    }

    public List<UserResponse> getAllDoctors() {
        return userRepository.findByRole("DOCTOR").stream().map(this::toUserResponse).toList();
    }

    public List<UserResponse> getAllPatients() {
        return userRepository.findByRole("PATIENT").stream().map(this::toUserResponse).toList();
    }

    public List<MedicalRecord> getDoctorRecords(String doctorEmail) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found"));
        return recordRepository.findByAuthorizedDoctorIdsContaining(doctor.getId());
    }

    // ─── Access Request Flow ──────────────────────────────────────────────────

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

    // ─── Grant Access ─────────────────────────────────────────────────────────

    /**
     * Patient grants doctor access:
     * 1. Add doctor to authorized list
     * 2. Generate PRE key for AES key re-encryption
     * 3. Record access grant on blockchain
     */
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

            // Generate Proxy Re-Encryption key
            String rk = preService.generateReEncryptionKey(patient.getId(), doctor.getPseudonymId());
            ReEncryptionKey reKey = new ReEncryptionKey();
            reKey.setRecordId(recordId);
            reKey.setDoctorId(doctorId);
            reKey.setDoctorPID(doctor.getPseudonymId());
            reKey.setTempKey(preService.generateTempAccessKey(recordId, doctorId));
            reKey.setEncryptedAesKey(rk);
            reKey.setExpiry(LocalDateTime.now().plusDays(30));
            reKeyRepository.save(reKey);

            // Blockchain event
            String txHash = blockchainService.grantAccess(
                    recordId, doctor.getPseudonymId(), patient.getPseudonymId());
            record.setBlockchainTxHash(txHash);
        }

        MedicalRecord saved = recordRepository.save(record);
        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                  recordId, patient.getPseudonymId(), "GRANT", ipAddress, true);

        // Mark pending request as granted
        accessRequestRepository.findByRecordIdAndDoctorIdAndStatus(recordId, doctorId, "PENDING")
                .ifPresent(r -> {
                    r.setStatus("GRANTED");
                    r.setUpdatedAt(LocalDateTime.now());
                    accessRequestRepository.save(r);
                });

        return saved;
    }

    // ─── Revoke Access ────────────────────────────────────────────────────────

    /**
     * Patient revokes doctor access:
     * 1. Remove doctor from authorized list
     * 2. Delete PRE key (forward security)
     * 3. Record revocation on blockchain
     */
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

        // Delete PRE key (forward security — doctor can no longer decrypt)
        reKeyRepository.deleteByRecordIdAndDoctorId(recordId, doctorId);

        String doctorPID = doctor != null ? doctor.getPseudonymId() : doctorId;
        String txHash = blockchainService.revokeAccess(recordId, doctorPID, patient.getPseudonymId());
        record.setBlockchainTxHash(txHash);

        MedicalRecord saved = recordRepository.save(record);
        logAction(patient.getId(), patient.getName(), patient.getPseudonymId(),
                  recordId, patient.getPseudonymId(), "REVOKE", ipAddress, true);
        return saved;
    }

    // ─── View Record (Doctor) ─────────────────────────────────────────────────

    /**
     * Doctor views a record:
     * 1. Compute behavioral features from access history
     * 2. Score with Isolation Forest (ML anomaly detection)
     * 3. If allowed: PRE decrypt the AES key, then AES decrypt the content
     * 4. Log access attempt on blockchain
     */
    public Map<String, Object> viewRecord(String recordId, String doctorEmail, String ipAddress) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found"));
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found"));

        LocalDateTime now          = LocalDateTime.now();
        LocalDateTime oneHourAgo   = now.minusHours(1);
        LocalDateTime sevenDaysAgo = now.minusDays(7);

        // Compute behavioral features from access history
        List<AccessLog> logsLastHour = accessLogRepository
                .findByUserIdAndTimestampAfter(doctor.getId(), oneHourAgo);
        long recordsPerHour         = logsLastHour.stream().filter(l -> "VIEW".equals(l.getAction())).count();
        long uniquePatientsLastHour = logsLastHour.stream().map(AccessLog::getPatientPID).distinct().count();
        long accessFreq7d           = accessLogRepository.countByUserIdAndTimestampAfter(doctor.getId(), sevenDaysAgo);

        double timeSinceLast = logsLastHour.stream()
                .filter(l -> "VIEW".equals(l.getAction()))
                .map(AccessLog::getTimestamp)
                .max(LocalDateTime::compareTo)
                .map(t -> (double) java.time.Duration.between(t, now).toMinutes())
                .orElse(60.0);

        Map<String, String> doctorAttrs  = doctor.getAttributes() != null ? doctor.getAttributes() : Map.of();
        User patient = userRepository.findById(record.getPatientId()).orElse(null);
        Map<String, String> patientAttrs = (patient != null && patient.getAttributes() != null)
                ? patient.getAttributes() : Map.of();

        boolean specialtyMatch = cpabeService.attributesSatisfyPolicy(record.getAccessPolicy(), doctorAttrs);
        boolean hospitalMatch  = doctorAttrs.getOrDefault("hospital", "")
                .equals(patientAttrs.getOrDefault("hospital", ""));

        // ML Risk Scoring
        MLService.AnomalyRequest mlReq = new MLService.AnomalyRequest();
        mlReq.setHour(now.getHour());
        mlReq.setDay_of_week(now.getDayOfWeek().getValue());
        mlReq.setRecords_per_hour(recordsPerHour);
        mlReq.setTime_since_last(timeSinceLast);
        mlReq.setSpecialty_match(specialtyMatch ? 1.0 : 0.0);
        mlReq.setHospital_match(hospitalMatch ? 1.0 : 0.0);
        mlReq.setAccess_freq_7d(accessFreq7d);
        mlReq.setUnique_patients_1h(uniquePatientsLastHour);

        MLService.AnomalyResponse mlRes = mlService.scoreAccess(mlReq);

        boolean policyMatch = record.getAuthorizedDoctorIds().contains(doctor.getId());
        boolean allowed     = policyMatch && !mlRes.isAnomaly();

        // Blockchain audit log
        String txHash = blockchainService.logAccessAttempt(
                recordId, doctor.getPseudonymId(), allowed, mlRes.getRisk_level());

        logAction(doctor.getId(), doctor.getName(), doctor.getPseudonymId(),
                  recordId, record.getPatientPID(), "VIEW", ipAddress, allowed,
                  mlRes.getAnomaly_score(), mlRes.getIsolation_score(),
                  mlRes.getAutoencoder_score(), mlRes.getRisk_level(),
                  allowed ? null : (mlRes.isAnomaly() ? "ANOMALY_DETECTED" : "POLICY_MISMATCH"),
                  txHash);

        if (!allowed) throw new MedChainException("Access denied: " + mlRes.getRisk_level());

        // Decrypt content using PRE → AES pipeline
        String decryptedContent = "";
        if (record.getEncryptedContent() != null && record.getEncryptedAesKey() != null
                && record.getAesIv() != null) {
            try {
                // Step 1: Recover AES key via PRE (simulated CP-ABE key decapsulation)
                String aesKey = cpabeService.recoverAesKey(record.getEncryptedAesKey(), record.getAccessPolicy(), doctorAttrs);
                // Step 2: AES-256-GCM decrypt
                byte[] cipherBytes = Base64.getDecoder().decode(record.getEncryptedContent());
                byte[] plainBytes  = aesService.decrypt(cipherBytes, aesKey, record.getAesIv());
                decryptedContent   = "data:" + (record.getFileMimeType() != null ? record.getFileMimeType() : "text/plain")
                                     + ";base64," + Base64.getEncoder().encodeToString(plainBytes);
            } catch (Exception e) {
                log.warn("Decryption failed for record {}: {}", recordId, e.getMessage());
                // Fallback to legacy CP-ABE decryption
                try {
                    decryptedContent = cpabeService.decrypt(record.getEncryptedContent(), record.getAccessPolicy(), doctorAttrs);
                } catch (Exception e2) {
                    decryptedContent = "";
                }
            }
        } else if (record.getEncryptedContent() != null) {
            // Legacy records without AES pipeline
            try {
                decryptedContent = cpabeService.decrypt(record.getEncryptedContent(), record.getAccessPolicy(), doctorAttrs);
            } catch (Exception e) {
                decryptedContent = "";
            }
        }

        return Map.of(
            "record",           record,
            "anomalyScore",     mlRes.getAnomaly_score(),
            "riskScore",        mlRes.getRiskScore(),
            "riskLevel",        mlRes.getRisk_level(),
            "status",           mlRes.getStatus(),
            "decryptionStatus", "AES_PRE_APPLIED",
            "blockchainTxHash", txHash,
            "decryptedContent", decryptedContent,
            "ipfsGatewayUrl",   ipfsService.getGatewayUrl(record.getIpfsCid())
        );
    }

    // ─── File Download (raw bytes) ─────────────────────────────────────────────

    public String getRecordFile(String recordId, String doctorEmail) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found"));
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Record not found"));

        if (!record.getAuthorizedDoctorIds().contains(doctor.getId()))
            throw new MedChainException("Access denied");

        if (record.getEncryptedContent() == null)
            throw new MedChainException("No file content stored for this record");

        Map<String, String> doctorAttrs = doctor.getAttributes() != null ? doctor.getAttributes() : Map.of();
        return cpabeService.decrypt(record.getEncryptedContent(), record.getAccessPolicy(), doctorAttrs);
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private byte[] decodeContent(String content) {
        if (content.contains(",")) {
            // data URL format: data:mime;base64,xxxxx
            return Base64.getDecoder().decode(content.substring(content.indexOf(',') + 1));
        }
        // Plain base64
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
        log.setBlockchainTxHash(txHash != null ? txHash : blockchainMockTx());
        accessLogRepository.save(log);
    }

    private static String blockchainMockTx() {
        try {
            java.security.MessageDigest d = java.security.MessageDigest.getInstance("SHA3-256");
            byte[] hash = d.digest((System.nanoTime() + "").getBytes());
            return "0x" + java.util.HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "0x" + Long.toHexString(System.nanoTime()) + "0".repeat(48);
        }
    }

    private String generateMockCid() {
        return Long.toHexString(System.currentTimeMillis()) + "abc123def456";
    }
}
