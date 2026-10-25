package com.medchain;

import com.medchain.exception.MedChainException;
import com.medchain.model.BlockchainTransaction;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.BlockchainTransactionRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.service.BlockchainService;
import com.medchain.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class BlockchainIntegrationTest {

    @Autowired
    private BlockchainService blockchainService;

    @Autowired
    private BlockchainTransactionRepository transactionRepository;

    @Autowired
    private RecordService recordService;

    @Autowired
    private MedicalRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    private String patientEmail = "patient.blockchain.test@medchain.com";
    private String doctorEmail = "doctor.blockchain.test@medchain.com";

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail(patientEmail)) {
            User p = new User();
            p.setName("Blockchain Test Patient");
            p.setEmail(patientEmail);
            p.setPassword("Password123!");
            p.setRole("PATIENT");
            p.setEmailVerified(true);
            p.setPseudonymId("0xpatientbcpid");
            userRepository.save(p);
        }

        if (!userRepository.existsByEmail(doctorEmail)) {
            User d = new User();
            d.setName("Blockchain Test Doctor");
            d.setEmail(doctorEmail);
            d.setPassword("Password123!");
            d.setRole("DOCTOR");
            d.setEmailVerified(true);
            d.setPseudonymId("0xdoctorbcpid");
            userRepository.save(d);
        }
    }

    @Test
    @DisplayName("Phase 5: Blockchain Transaction Submission and JPA Persistence")
    void testBlockchainTransactionSubmissionAndPersistence() {
        String recordId = "rec_bc_test_1001";
        String sha256Hash = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

        // Submit transaction
        String txHash = blockchainService.uploadRecord(recordId, sha256Hash, "0xpolicyhash", "0xpatientbcpid", "Hospital A");
        assertNotNull(txHash, "Transaction hash must be returned");

        // Verify transaction metadata persistence in JPA repository
        List<BlockchainTransaction> txList = transactionRepository.findByRecordId(recordId);
        assertFalse(txList.isEmpty(), "BlockchainTransaction must be persisted in database");

        BlockchainTransaction btx = txList.get(0);
        assertEquals(txHash, btx.getTxHash());
        assertEquals(sha256Hash, btx.getSha256Hash());
        assertEquals("RecordUploaded", btx.getEventType());
        assertEquals("Hospital A", btx.getHospitalOrgId());
        assertTrue(btx.isVerified());
        assertNotNull(btx.getBlockNumber());
    }

    @Test
    @DisplayName("Phase 5: GET /api/blockchain/verify/{recordId} (Verified vs Tampered Hash)")
    void testBlockchainHashVerificationEndpoint() throws Exception {
        byte[] pdfBytes = "%PDF Blockchain Audit Data".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "blockchain_record.pdf", "application/pdf", pdfBytes);

        MedicalRecord record = recordService.uploadMultipartFile(file, "Blockchain Document", "Lab test", "LAB_REPORT", patientEmail, "127.0.0.1");

        String token = jwtUtil.generateToken(patientEmail, "PATIENT");

        // 1. Verification succeeds for matching hash
        mockMvc.perform(get("/api/blockchain/verify/" + record.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(true))
                .andExpect(jsonPath("$.verificationStatus").value("VERIFIED"))
                .andExpect(jsonPath("$.recordId").value(record.getId()))
                .andExpect(jsonPath("$.databaseHash").value(record.getSha256Hash()))
                .andExpect(jsonPath("$.blockchainHash").value(record.getSha256Hash()));

        // 2. Verification fails when database hash is tampered/modified
        MedicalRecord dbRecord = recordRepository.findById(record.getId()).orElseThrow();
        dbRecord.setSha256Hash("0000000000000000000000000000000000000000000000000000000000000000");
        recordRepository.save(dbRecord);

        mockMvc.perform(get("/api/blockchain/verify/" + record.getId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verified").value(false))
                .andExpect(jsonPath("$.verificationStatus").value("INTEGRITY_MISMATCH"))
                .andExpect(jsonPath("$.databaseHash").value("0000000000000000000000000000000000000000000000000000000000000000"))
                .andExpect(jsonPath("$.blockchainHash").value(record.getSha256Hash()));
    }

    @Test
    @DisplayName("Phase 5: Unknown Record Verification Rejection")
    void testUnknownRecordVerificationRejection() throws Exception {
        String token = jwtUtil.generateToken(patientEmail, "PATIENT");

        mockMvc.perform(get("/api/blockchain/verify/non_existent_record_id")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Medical record not found for ID: non_existent_record_id"));
    }
}
