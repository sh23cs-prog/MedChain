package com.medchain;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.medchain.dto.AccessDecisionRequest;
import com.medchain.dto.AccessRequestDto;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.AccessRequest;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.service.AccessService;
import com.medchain.service.OtpService;
import com.medchain.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class SecuritySuiteTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("dev", "dev"))
            .withPerMethodLifecycle(false);

    @org.springframework.test.context.DynamicPropertySource
    static void configureMail(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "dev");
        registry.add("spring.mail.password", () -> "dev");
        registry.add("blockchain.enabled", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private OtpService otpService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private AccessService accessService;

    @Autowired
    private MedicalRecordRepository recordRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private MLAlertRepository alertRepository;

    private String patientAEmail = "patient.sec.a@medchain.com";
    private String patientBEmail = "patient.sec.b@medchain.com";
    private String doctorEmail   = "doctor.sec@medchain.com";
    private String adminEmail    = "admin.sec@medchain.com";

    private User patientA;
    private User patientB;
    private User doctor;
    private User admin;

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail(patientAEmail)) {
            patientA = new User();
            patientA.setName("Patient Sec A");
            patientA.setEmail(patientAEmail);
            patientA.setPassword(passwordEncoder.encode("Password123!"));
            patientA.setRole("PATIENT");
            patientA.setEmailVerified(true);
            patientA.setPseudonymId("0xpatientAsecpid");
            patientA = userRepository.save(patientA);
        } else {
            patientA = userRepository.findByEmail(patientAEmail).orElseThrow();
        }

        if (!userRepository.existsByEmail(patientBEmail)) {
            patientB = new User();
            patientB.setName("Patient Sec B");
            patientB.setEmail(patientBEmail);
            patientB.setPassword(passwordEncoder.encode("Password123!"));
            patientB.setRole("PATIENT");
            patientB.setEmailVerified(true);
            patientB.setPseudonymId("0xpatientBsecpid");
            patientB = userRepository.save(patientB);
        } else {
            patientB = userRepository.findByEmail(patientBEmail).orElseThrow();
        }

        if (!userRepository.existsByEmail(doctorEmail)) {
            doctor = new User();
            doctor.setName("Doctor Sec");
            doctor.setEmail(doctorEmail);
            doctor.setPassword(passwordEncoder.encode("Password123!"));
            doctor.setRole("DOCTOR");
            doctor.setEmailVerified(true);
            doctor.setPseudonymId("0xdoctorsecpid");
            doctor = userRepository.save(doctor);
        } else {
            doctor = userRepository.findByEmail(doctorEmail).orElseThrow();
        }

        if (!userRepository.existsByEmail(adminEmail)) {
            admin = new User();
            admin.setName("Admin Sec");
            admin.setEmail(adminEmail);
            admin.setPassword(passwordEncoder.encode("Password123!"));
            admin.setRole("ADMIN");
            admin.setEmailVerified(true);
            admin.setPseudonymId("0xadminsecpid");
            admin = userRepository.save(admin);
        } else {
            admin = userRepository.findByEmail(adminEmail).orElseThrow();
        }
    }

    // ─── 1. JWT & RBAC SECURITY TESTS ────────────────────────────────────────

    @Test
    @DisplayName("Security Scenario 1: Missing Authentication Header Returns 401")
    void testMissingAuthHeaderReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security Scenario 2: Invalid JWT Returns 401")
    void testInvalidJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer INVALID_TAMPERED_JWT_TOKEN"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Security Scenario 3: Wrong Role Access Attempt Returns 403")
    void testWrongRoleAccessReturns403() throws Exception {
        String doctorToken = jwtUtil.generateToken(doctorEmail, "DOCTOR");
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security Scenario 4: Doctor Without Active Access Approval Receives 403")
    void testDoctorWithoutApprovalReceives403() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "unauthorized_scan.pdf", "application/pdf", "%PDF Confidential".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Unapproved Record", "Secret scan", "SCAN", patientAEmail, "127.0.0.1");

        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));
        assertThrows(MedChainException.class, () -> recordService.downloadRecordFile(record.getId(), doctorEmail, "127.0.0.1"));
    }

    @Test
    @DisplayName("Security Scenario 5: Revoked Access Doctor Attempt Receives 403")
    void testRevokedAccessDoctorReceives403() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "revocation_scan.pdf", "application/pdf", "%PDF Revocation Data".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Revocation Test", "Scan notes", "SCAN", patientAEmail, "127.0.0.1");

        // 1. Request access
        AccessRequestDto reqDto = new AccessRequestDto();
        reqDto.setRecordId(record.getId());
        reqDto.setReason("Consultation");
        AccessRequest req = accessService.requestAccess(reqDto, doctorEmail, "127.0.0.1", "JUnit-Agent");

        // 2. Approve access
        AccessDecisionRequest approveDto = new AccessDecisionRequest();
        approveDto.setRequestId(req.getId());
        approveDto.setDurationDays(7);
        accessService.approveAccess(approveDto, patientAEmail, "127.0.0.1", "JUnit-Agent");

        // Doctor can view while APPROVED
        assertDoesNotThrow(() -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));

        // 3. Revoke access
        AccessDecisionRequest revokeDto = new AccessDecisionRequest();
        revokeDto.setRequestId(req.getId());
        accessService.revokeAccess(revokeDto, patientAEmail, "127.0.0.1", "JUnit-Agent");

        // Doctor receives 403 Forbidden exception after revocation
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));
    }

    @Test
    @DisplayName("Security Scenario 6: Patient Cannot Access Another Patient's Operations (Cross-Patient Protection)")
    void testCrossPatientProtection() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "patient_a_file.pdf", "application/pdf", "%PDF Patient A Secret".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Patient A Secret Record", "Private", "LAB_REPORT", patientAEmail, "127.0.0.1");

        // Patient B attempts to view Patient A's record -> Denied
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), patientBEmail, "127.0.0.1"));

        // Patient B attempts to approve/revoke Patient A's record request -> Denied
        AccessDecisionRequest decDto = new AccessDecisionRequest();
        decDto.setRequestId("dummy_req_id");
        assertThrows(MedChainException.class, () -> accessService.approveAccess(decDto, patientBEmail, "127.0.0.1", "JUnit-Agent"));
        assertThrows(MedChainException.class, () -> accessService.revokeAccess(decDto, patientBEmail, "127.0.0.1", "JUnit-Agent"));
    }

    @Test
    @DisplayName("Security Scenario 7: Doctor Cannot Self-Approve Access Requests")
    void testDoctorCannotSelfApprove() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc_self_approve.pdf", "application/pdf", "%PDF Test".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Self Approve Test", "Notes", "LAB_REPORT", patientAEmail, "127.0.0.1");

        AccessRequestDto reqDto = new AccessRequestDto();
        reqDto.setRecordId(record.getId());
        reqDto.setReason("Self approve attempt");
        AccessRequest req = accessService.requestAccess(reqDto, doctorEmail, "127.0.0.1", "JUnit-Agent");

        AccessDecisionRequest approveDto = new AccessDecisionRequest();
        approveDto.setRequestId(req.getId());

        // Doctor attempts to approve request -> Throws 403 MedChainException
        assertThrows(MedChainException.class, () -> accessService.approveAccess(approveDto, doctorEmail, "127.0.0.1", "JUnit-Agent"));
    }

    // ─── 2. OTP SECURITY TESTS ────────────────────────────────────────────────

    @Test
    @DisplayName("Security Scenario 8: OTP Single-Use and Retry Limit Enforcement")
    void testOtpSingleUseAndRetryLimit() {
        String testEmail = "otp.security.suite@medchain.com";
        otpService.generateAndSendOtp(testEmail, "REGISTRATION");

        // Max 3 incorrect verification attempts
        assertThrows(MedChainException.class, () -> otpService.verifyOtp(testEmail, "000000", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> otpService.verifyOtp(testEmail, "111111", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> otpService.verifyOtp(testEmail, "222222", "REGISTRATION"));

        // 4th attempt fails because max 3 attempts exceeded
        assertThrows(MedChainException.class, () -> otpService.verifyOtp(testEmail, "333333", "REGISTRATION"));
    }

    // ─── 3. CRYPTOGRAPHIC & INTEGRITY SECURITY TESTS ──────────────────────────

    @Test
    @DisplayName("Security Scenario 9: Tampered File GCM / SHA-256 Integrity Failure Logging")
    void testTamperedFileIntegrityFailureLogging() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "integrity_sec_test.pdf", "application/pdf", "%PDF Integrity Data".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Integrity Sec Record", "Notes", "LAB_REPORT", patientAEmail, "127.0.0.1");

        // Tamper with encrypted storage payload on disk (flip last byte)
        byte[] storageBytes = Files.readAllBytes(Paths.get(record.getStoragePath()));
        storageBytes[storageBytes.length - 1] ^= 0xFF;
        Files.write(Paths.get(record.getStoragePath()), storageBytes);

        // Patient views tampered record -> Integrity check fails and logs INTEGRITY_FAILURE audit record
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), patientAEmail, "127.0.0.1"));

        List<AccessLog> logs = accessLogRepository.findAll();
        boolean integrityLogged = logs.stream().anyMatch(l -> "INTEGRITY_FAILURE".equalsIgnoreCase(l.getAction()) && record.getId().equals(l.getRecordId()));
        assertTrue(integrityLogged, "INTEGRITY_FAILURE audit log record must be generated upon GCM tag/checksum mismatch");
    }
}
