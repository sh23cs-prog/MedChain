package com.medchain;

import com.medchain.model.*;
import com.medchain.repository.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class DatabaseEntityTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private MedicalRecordRepository medicalRecordRepository;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private ReEncryptionKeyRepository reEncryptionKeyRepository;

    @Autowired
    private BlockchainTransactionRepository blockchainTransactionRepository;

    @Autowired
    private MLPredictionRepository mlPredictionRepository;

    @Autowired
    private MLAlertRepository mlAlertRepository;

    @Test
    @DisplayName("Phase 1: Verify User Entity and UserRepository persistence with H2 JPA")
    void testUserEntityPersistence() {
        User user = new User();
        user.setEmail("phase1.user@medchain.com");
        user.setPassword("secretBCryptHash");
        user.setName("Phase 1 Tester");
        user.setRole(Role.PATIENT.name());
        user.setPhone("+1-555-0199");
        user.setPseudonymId("0xphase1pseudonymhash");
        user.setPseudonymNonce("nonce123");
        user.setAttributes(Map.of("specialty", "Cardiology", "hospital", "CityHospital"));

        User saved = userRepository.save(user);
        assertNotNull(saved.getId());

        Optional<User> found = userRepository.findByEmail("phase1.user@medchain.com");
        assertTrue(found.isPresent());
        assertEquals("Phase 1 Tester", found.get().getName());
        assertEquals("Cardiology", found.get().getAttributes().get("specialty"));
    }

    @Test
    @DisplayName("Phase 1: Verify OtpToken Entity and OtpTokenRepository persistence")
    void testOtpTokenEntityPersistence() {
        OtpToken token = new OtpToken();
        token.setEmail("test.otp@medchain.com");
        token.setOtpHash("$2a$10$HashedOtpValue");
        token.setType("REGISTRATION");
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        token.setAttemptCount(0);
        token.setUsed(false);

        OtpToken saved = otpTokenRepository.save(token);
        assertNotNull(saved.getId());

        Optional<OtpToken> found = otpTokenRepository.findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc("test.otp@medchain.com", "REGISTRATION");
        assertTrue(found.isPresent());
        assertFalse(found.get().isUsed());
    }

    @Test
    @DisplayName("Phase 1: Verify MedicalRecord Entity and MedicalRecordRepository queries")
    void testMedicalRecordEntityPersistence() {
        MedicalRecord record = new MedicalRecord();
        record.setPatientId("patient-123");
        record.setPatientPID("0xpatientpidhash");
        record.setTitle("Blood Panel Report");
        record.setDescription("CBC and Metabolic Panel");
        record.setRecordType("LAB_REPORT");
        record.setFileName("cbc_report.pdf");
        record.setFileSize("150 KB");
        record.setFileMimeType("application/pdf");
        record.setSha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        record.setAuthorizedDoctorIds(List.of("doc-1", "doc-2"));

        MedicalRecord saved = medicalRecordRepository.save(record);
        assertNotNull(saved.getId());

        List<MedicalRecord> patientRecords = medicalRecordRepository.findByPatientId("patient-123");
        assertFalse(patientRecords.isEmpty());

        List<MedicalRecord> doctorRecords = medicalRecordRepository.findByAuthorizedDoctorIdsContaining("doc-1");
        assertFalse(doctorRecords.isEmpty());
    }

    @Test
    @DisplayName("Phase 1: Verify AccessLog Entity and AccessLogRepository queries")
    void testAccessLogEntityPersistence() {
        AccessLog log = new AccessLog();
        log.setUserId("doc-1");
        log.setUserName("Dr. Smith");
        log.setDoctorId("doc-1");
        log.setRecordId("rec-100");
        log.setAction("VIEW");
        log.setAnomalyScore(0.85);
        log.setIsolationScore(0.88);
        log.setAutoencoderScore(0.82);
        log.setRiskLevel("CRITICAL");
        log.setFlagged(true);
        log.setGranted(false);
        log.setDenyReason("ANOMALY_DETECTED");
        log.setIpAddress("192.168.1.50");
        log.setHospitalOrgId("Hospital A");

        AccessLog saved = accessLogRepository.save(log);
        assertNotNull(saved.getId());

        List<AccessLog> flagged = accessLogRepository.findByFlaggedTrue();
        assertFalse(flagged.isEmpty());

        long count = accessLogRepository.countByUserIdAndTimestampAfter("doc-1", LocalDateTime.now().minusHours(1));
        assertTrue(count > 0);
    }

    @Test
    @DisplayName("Phase 1: Verify BlockchainTransaction, MLPrediction, and MLAlert Entities")
    void testAuxiliaryEntities() {
        BlockchainTransaction btx = new BlockchainTransaction();
        btx.setTxHash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        btx.setRecordId("rec-100");
        btx.setSha256Hash("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        btx.setEventType("RecordUploaded");
        btx.setHospitalOrgId("Hospital B");
        BlockchainTransaction savedBtx = blockchainTransactionRepository.save(btx);
        assertNotNull(savedBtx.getId());

        MLPrediction pred = new MLPrediction();
        pred.setUserId("user-1");
        pred.setRecordId("rec-100");
        pred.setRiskScore(0.88);
        pred.setRiskLevel("CRITICAL");
        pred.setAnomalyStatus("LOCKDOWN");
        pred.setAnomaly(true);
        MLPrediction savedPred = mlPredictionRepository.save(pred);
        assertNotNull(savedPred.getId());

        MLAlert alert = new MLAlert();
        alert.setUserId("user-1");
        alert.setRecordId("rec-100");
        alert.setRecordTitle("Blood Panel Report");
        alert.setDoctorName("Dr. Attacker");
        alert.setRiskScore(0.88);
        alert.setRiskLevel("CRITICAL");
        alert.setStatus("OPEN");
        MLAlert savedAlert = mlAlertRepository.save(alert);
        assertNotNull(savedAlert.getId());
    }
}
