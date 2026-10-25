package com.medchain;

import com.medchain.dto.AccessDecisionRequest;
import com.medchain.dto.AccessRequestDto;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.AccessRequest;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.AccessRequestRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.service.AccessService;
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
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AccessControlLifecycleTest {

    @Autowired
    private AccessService accessService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private AccessRequestRepository accessRequestRepository;

    @Autowired
    private MedicalRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    private String patientAEmail = "patient.a.lifecycle@medchain.com";
    private String patientBEmail = "patient.b.lifecycle@medchain.com";
    private String doctorEmail = "doctor.lifecycle@medchain.com";

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail(patientAEmail)) {
            User pA = new User();
            pA.setName("Patient A");
            pA.setEmail(patientAEmail);
            pA.setPassword("Password123!");
            pA.setRole("PATIENT");
            pA.setEmailVerified(true);
            pA.setPseudonymId("0xpatientApid");
            userRepository.save(pA);
        }

        if (!userRepository.existsByEmail(patientBEmail)) {
            User pB = new User();
            pB.setName("Patient B");
            pB.setEmail(patientBEmail);
            pB.setPassword("Password123!");
            pB.setRole("PATIENT");
            pB.setEmailVerified(true);
            pB.setPseudonymId("0xpatientBpid");
            userRepository.save(pB);
        }

        if (!userRepository.existsByEmail(doctorEmail)) {
            User d = new User();
            d.setName("Doctor Lifecycle");
            d.setEmail(doctorEmail);
            d.setPassword("Password123!");
            d.setRole("DOCTOR");
            d.setEmailVerified(true);
            d.setPseudonymId("0xdoctorlifecyclepid");
            userRepository.save(d);
        }
    }

    @Test
    @DisplayName("Phase 4: Access Lifecycle (PENDING -> APPROVED -> VIEW -> REVOKED -> 403)")
    void testAccessLifecycleApproveAndRevoke() {
        // 1. Patient A uploads a medical record
        MockMultipartFile file = new MockMultipartFile(
                "file", "cardiology.pdf", "application/pdf", "%PDF Cardiology Data".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Cardiology Report", "ECG", "CARDIOLOGY", patientAEmail, "127.0.0.1");

        // 2. Doctor requests access -> PENDING
        AccessRequestDto reqDto = new AccessRequestDto();
        reqDto.setRecordId(record.getId());
        reqDto.setReason("Pre-op cardiology review");

        AccessRequest req = accessService.requestAccess(reqDto, doctorEmail, "127.0.0.1", "JUnit-Agent");
        assertEquals("PENDING", req.getStatus());

        // Unapproved doctor attempts view -> Denied (403 exception)
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));

        // 3. Patient A approves request -> APPROVED
        AccessDecisionRequest approveDto = new AccessDecisionRequest();
        approveDto.setRequestId(req.getId());
        approveDto.setDurationDays(14);

        AccessRequest approvedReq = accessService.approveAccess(approveDto, patientAEmail, "127.0.0.1", "JUnit-Agent");
        assertEquals("APPROVED", approvedReq.getStatus());
        assertNotNull(approvedReq.getExpiresAt());

        // Doctor with APPROVED status views record -> Success
        Map<String, Object> viewRes = recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1");
        assertTrue((Boolean) viewRes.get("integrityVerified"));

        // 4. Patient A revokes access -> REVOKED
        AccessDecisionRequest revokeDto = new AccessDecisionRequest();
        revokeDto.setRequestId(req.getId());

        AccessRequest revokedReq = accessService.revokeAccess(revokeDto, patientAEmail, "127.0.0.1", "JUnit-Agent");
        assertEquals("REVOKED", revokedReq.getStatus());

        // Revoked doctor attempts view -> Denied (403 exception)
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));
        assertThrows(MedChainException.class, () -> recordService.downloadRecordFile(record.getId(), doctorEmail, "127.0.0.1"));
    }

    @Test
    @DisplayName("Phase 4: Access Lifecycle (PENDING -> REJECTED & EXPIRED Enforcement)")
    void testAccessLifecycleRejectAndExpired() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "neurology.pdf", "application/pdf", "%PDF Neurology Data".getBytes(StandardCharsets.UTF_8));
        MedicalRecord record = recordService.uploadMultipartFile(file, "Neurology Scan", "Brain MRI", "NEUROLOGY", patientAEmail, "127.0.0.1");

        // 1. Doctor requests access -> REJECTED
        AccessRequestDto reqDto1 = new AccessRequestDto();
        reqDto1.setRecordId(record.getId());
        reqDto1.setReason("Second opinion");
        AccessRequest req1 = accessService.requestAccess(reqDto1, doctorEmail, "127.0.0.1", "JUnit-Agent");

        AccessDecisionRequest rejectDto = new AccessDecisionRequest();
        rejectDto.setRequestId(req1.getId());
        AccessRequest rejectedReq = accessService.rejectAccess(rejectDto, patientAEmail, "127.0.0.1", "JUnit-Agent");
        assertEquals("REJECTED", rejectedReq.getStatus());

        // Rejected doctor view -> Denied
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));

        // 2. EXPIRED Enforcement Test
        AccessRequest req2 = new AccessRequest();
        req2.setRecordId(record.getId());
        req2.setDoctorId(userRepository.findByEmail(doctorEmail).orElseThrow().getId());
        req2.setPatientId(userRepository.findByEmail(patientAEmail).orElseThrow().getId());
        req2.setStatus("APPROVED");
        req2.setExpiresAt(LocalDateTime.now().minusHours(1)); // Expired 1 hour ago
        accessRequestRepository.save(req2);

        // Add doctor temporarily to record to simulate expired grant
        MedicalRecord rec = recordRepository.findById(record.getId()).orElseThrow();
        rec.getAuthorizedDoctorIds().add(req2.getDoctorId());
        recordRepository.save(rec);

        // Enforce expiration
        accessService.checkAndEnforceExpiration(record.getId(), req2.getDoctorId());

        AccessRequest expiredReq = accessRequestRepository.findById(req2.getId()).orElseThrow();
        assertEquals("EXPIRED", expiredReq.getStatus());

        // Expired doctor view -> Denied
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));
    }

    @Test
    @DisplayName("Phase 4: Authorization Safeguards (Cross-patient approval prevention & Doctor self-approval prevention)")
    void testAuthorizationSafeguards() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "patientA_record.pdf", "application/pdf", "Patient A Data".getBytes(StandardCharsets.UTF_8));
        MedicalRecord recordA = recordService.uploadMultipartFile(file, "Patient A Record", "Private", "LAB_REPORT", patientAEmail, "127.0.0.1");

        AccessRequestDto reqDto = new AccessRequestDto();
        reqDto.setRecordId(recordA.getId());
        AccessRequest req = accessService.requestAccess(reqDto, doctorEmail, "127.0.0.1", "JUnit-Agent");

        // Patient B attempts to approve Patient A's request -> Fails
        AccessDecisionRequest decisionDto = new AccessDecisionRequest();
        decisionDto.setRequestId(req.getId());
        assertThrows(MedChainException.class, () -> accessService.approveAccess(decisionDto, patientBEmail, "127.0.0.1", "JUnit-Agent"));

        // Doctor attempts self-approval -> Fails
        assertThrows(MedChainException.class, () -> accessService.approveAccess(decisionDto, doctorEmail, "127.0.0.1", "JUnit-Agent"));
    }

    @Test
    @DisplayName("Phase 4: Audit Logs & Admin Access Control Enforcement")
    void testAuditLogsAndAdminRBAC() throws Exception {
        String patientToken = jwtUtil.generateToken(patientAEmail, "PATIENT");
        String doctorToken = jwtUtil.generateToken(doctorEmail, "DOCTOR");

        // Non-admin users attempting to access /api/admin/audit-logs receive 403 Forbidden
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + patientToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + doctorToken))
                .andExpect(status().isForbidden());

        // Verify audit logs are persisted in DB
        List<AccessLog> logs = accessLogRepository.findAll();
        assertFalse(logs.isEmpty(), "Audit log database entries must be persisted");
        assertTrue(logs.stream().anyMatch(l -> "ACCESS_REQUEST".equals(l.getAction()) || "UPLOAD".equals(l.getAction())),
                "Audit logs must capture ACCESS_REQUEST and UPLOAD security events");
    }
}
