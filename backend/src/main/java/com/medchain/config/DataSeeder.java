package com.medchain.config;

import com.medchain.model.AccessLog;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.service.BlockchainService;
import com.medchain.service.PseudonymService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final MedicalRecordRepository recordRepository;
    private final AccessLogRepository accessLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final PseudonymService pseudonymService;
    private final BlockchainService blockchainService;

    @Override
    public void run(String... args) {
        if (userRepository.count() > 0) {
            // On restart, only re-anchor on blockchain if the node is freshly spun up.
            // isPseudonymRegistered() returns false when blockchain is unavailable,
            // so we only call registerPseudonym for PIDs the contract doesn't know yet.
            if (blockchainService.isAvailable()) {
                userRepository.findAll().forEach(u -> {
                    if (!blockchainService.isPseudonymRegistered(u.getPseudonymId())) {
                        blockchainService.registerPseudonym(u.getPseudonymId(), u.getRole());
                    }
                });
                recordRepository.findAll().forEach(r -> {
                    User patient = userRepository.findById(r.getPatientId()).orElse(null);
                    if (patient != null && (r.getBlockchainTxHash() == null || r.getBlockchainTxHash().endsWith("mock"))) {
                        String tx = blockchainService.uploadRecord(
                            r.getId(), r.getSha256Hash() != null ? r.getSha256Hash() : r.getId(), r.getPolicyHash(), patient.getPseudonymId());
                        r.setBlockchainTxHash(tx);
                        r.setBlockchainRecordId("0x" + org.web3j.crypto.Hash.sha3String(r.getId()).substring(2, 18));
                        recordRepository.save(r);
                    }
                });
                log.info("Re-anchored records on blockchain where needed");
            }
            return;
        }
        log.info("Seeding MedChain demo data...");


        // Users
        User alice  = createUser("Alice Johnson",    "patient@medchain.com",  "Password123!", "PATIENT", "+1-555-0101", Map.of());
        User bob    = createUser("Bob Williams",     "bob@medchain.com",      "Password123!", "PATIENT", "+1-555-0105", Map.of());
        User admin  = createUser("Admin User",       "admin@medchain.com",    "Password123!", "ADMIN",   "+1-555-0103", Map.of());

        // Doctors — 8 specializations
        User robert  = createUser("Dr. Robert Smith",   "doctor@medchain.com",    "Password123!", "DOCTOR", "+1-555-0102",
                Map.of("specialty", "Cardiology",    "hospital", "CityHospital",    "license", "Senior",   "department", "Cardiology"));
        User emily   = createUser("Dr. Emily Chen",     "emily@medchain.com",     "Password123!", "DOCTOR", "+1-555-0104",
                Map.of("specialty", "Radiology",     "hospital", "CityHospital",    "license", "Senior",   "department", "Radiology"));
        User james   = createUser("Dr. James Wilson",   "james@medchain.com",     "Password123!", "DOCTOR", "+1-555-0106",
                Map.of("specialty", "Neurology",     "hospital", "CityHospital",    "license", "Senior",   "department", "Neurology"));
        User sarah   = createUser("Dr. Sarah Patel",    "sarah@medchain.com",     "Password123!", "DOCTOR", "+1-555-0107",
                Map.of("specialty", "Orthopedics",   "hospital", "MedicalCenter",   "license", "Junior",   "department", "Orthopedics"));
        User michael = createUser("Dr. Michael Torres", "michael@medchain.com",   "Password123!", "DOCTOR", "+1-555-0108",
                Map.of("specialty", "Oncology",      "hospital", "CancerInstitute", "license", "Senior",   "department", "Oncology"));
        User priya   = createUser("Dr. Priya Sharma",   "priya@medchain.com",     "Password123!", "DOCTOR", "+1-555-0109",
                Map.of("specialty", "Pediatrics",    "hospital", "CityHospital",    "license", "Junior",   "department", "Pediatrics"));
        User david   = createUser("Dr. David Kim",      "david@medchain.com",     "Password123!", "DOCTOR", "+1-555-0110",
                Map.of("specialty", "Dermatology",   "hospital", "SkinClinic",      "license", "Junior",   "department", "Dermatology"));
        User lisa    = createUser("Dr. Lisa Nguyen",    "lisa@medchain.com",      "Password123!", "DOCTOR", "+1-555-0111",
                Map.of("specialty", "Psychiatry",    "hospital", "MindWellness",    "license", "Senior",   "department", "Psychiatry"));

        // Alice's records
        MedicalRecord r1 = createRecord(alice, "Blood Test Results", "Complete blood count and metabolic panel",
                "LAB_REPORT", "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)",
                List.of(robert.getId()), List.of(robert.getPseudonymId()), "2025-01-10");
        MedicalRecord r2 = createRecord(alice, "Chest X-Ray", "Annual chest radiograph — no abnormalities",
                "SCAN", "(specialty=Radiology AND hospital=CityHospital)",
                List.of(emily.getId()), List.of(emily.getPseudonymId()), "2025-01-15");
        MedicalRecord r3 = createRecord(alice, "Cardiology Prescription", "Atorvastatin 20mg daily for 3 months",
                "PRESCRIPTION", "(specialty=Cardiology) OR (license=Senior AND hospital=CityHospital)",
                List.of(robert.getId(), emily.getId()), List.of(robert.getPseudonymId(), emily.getPseudonymId()), "2025-01-20");
        MedicalRecord r4 = createRecord(alice, "Hospital Discharge Summary", "Post-operative care instructions",
                "DISCHARGE", "(license=Senior AND hospital=CityHospital)",
                List.of(robert.getId()), List.of(robert.getPseudonymId()), "2025-01-25");
        MedicalRecord r8 = createRecord(alice, "Neurology Consultation", "Migraine evaluation and treatment plan",
                "LAB_REPORT", "(specialty=Neurology AND hospital=CityHospital)",
                List.of(james.getId()), List.of(james.getPseudonymId()), "2025-03-05");
        MedicalRecord r10 = createRecord(alice, "MRI Brain Scan", "MRI without contrast — no acute intracranial abnormality",
                "SCAN", "(specialty=Neurology OR specialty=Radiology) AND hospital=CityHospital",
                List.of(james.getId(), emily.getId()), List.of(james.getPseudonymId(), emily.getPseudonymId()), "2025-03-10");
        MedicalRecord r11 = createRecord(alice, "Thyroid Function Test", "TSH, T3, T4 levels — subclinical hypothyroidism",
                "LAB_REPORT", "(specialty=Cardiology OR license=Senior)",
                List.of(robert.getId()), List.of(robert.getPseudonymId()), "2025-04-01");
        MedicalRecord r12 = createRecord(alice, "Dermatology Report", "Eczema assessment — mild atopic dermatitis",
                "DISCHARGE", "(specialty=Dermatology)",
                List.of(david.getId()), List.of(david.getPseudonymId()), "2025-04-15");
        MedicalRecord r13 = createRecord(alice, "Psychiatry Evaluation", "Anxiety disorder assessment — GAD-7 score 12",
                "LAB_REPORT", "(specialty=Psychiatry AND license=Senior)",
                List.of(lisa.getId()), List.of(lisa.getPseudonymId()), "2025-05-02");

        // Bob's records
        MedicalRecord r5 = createRecord(bob, "Lipid Panel Results", "Cholesterol and triglyceride levels — borderline high LDL",
                "LAB_REPORT", "(specialty=Cardiology AND hospital=CityHospital)",
                List.of(robert.getId()), List.of(robert.getPseudonymId()), "2025-02-03");
        MedicalRecord r6 = createRecord(bob, "Echocardiogram Report", "Mild mitral valve regurgitation detected",
                "SCAN", "(specialty=Cardiology OR specialty=Radiology) AND hospital=CityHospital",
                List.of(robert.getId(), emily.getId()), List.of(robert.getPseudonymId(), emily.getPseudonymId()), "2025-02-10");
        MedicalRecord r7 = createRecord(bob, "Beta Blocker Prescription", "Metoprolol 50mg twice daily — cardiac management",
                "PRESCRIPTION", "(specialty=Cardiology AND license=Senior)",
                List.of(), List.of(), "2025-02-15");
        MedicalRecord r9 = createRecord(bob, "Orthopedic X-Ray", "Right knee — mild osteoarthritis grade II",
                "SCAN", "(specialty=Orthopedics OR specialty=Radiology)",
                List.of(sarah.getId(), emily.getId()), List.of(sarah.getPseudonymId(), emily.getPseudonymId()), "2025-03-12");
        MedicalRecord r14 = createRecord(bob, "Oncology Screening", "PSA test and colonoscopy — no malignancy detected",
                "LAB_REPORT", "(specialty=Oncology AND license=Senior)",
                List.of(michael.getId()), List.of(michael.getPseudonymId()), "2025-03-20");
        MedicalRecord r15 = createRecord(bob, "Physiotherapy Discharge", "Post-knee surgery rehab — full ROM achieved",
                "DISCHARGE", "(specialty=Orthopedics OR license=Senior)",
                List.of(sarah.getId()), List.of(sarah.getPseudonymId()), "2025-04-10");
        MedicalRecord r16 = createRecord(bob, "Pediatric Vaccination Record", "Annual flu vaccine + Hepatitis B booster",
                "PRESCRIPTION", "(specialty=Pediatrics OR specialty=Cardiology)",
                List.of(priya.getId(), robert.getId()), List.of(priya.getPseudonymId(), robert.getPseudonymId()), "2025-04-22");
        MedicalRecord r17 = createRecord(bob, "Psychiatric Follow-up", "Depression screening — PHQ-9 score 8, mild depression",
                "LAB_REPORT", "(specialty=Psychiatry AND license=Senior)",
                List.of(lisa.getId()), List.of(lisa.getPseudonymId()), "2025-05-10");

        // Access logs
        seedLog(robert,  r1,  alice, "VIEW",   0.12, 0.10, 0.14, "LOW",      true,  null,               "192.168.1.10",   "2025-01-26T08:30:00");
        seedLog(alice,   r2,  alice, "UPLOAD", 0.05, 0.04, 0.06, "LOW",      true,  null,               "192.168.1.5",    "2025-01-26T09:15:00");
        seedLog(emily,   r3,  alice, "VIEW",   0.78, 0.82, 0.74, "HIGH",     false, "ANOMALY_DETECTED", "10.0.0.45",      "2025-01-26T02:45:00");
        seedLog(robert,  r4,  alice, "VIEW",   0.21, 0.18, 0.24, "LOW",      true,  null,               "192.168.1.10",   "2025-01-26T10:00:00");
        seedLog(admin,   r1,  alice, "VIEW",   0.95, 0.97, 0.93, "CRITICAL", false, "ANOMALY_DETECTED", "45.33.32.156",   "2025-01-26T03:12:00");
        seedLog(alice,   r3,  alice, "GRANT",  0.08, 0.07, 0.09, "LOW",      true,  null,               "192.168.1.5",    "2025-01-26T11:30:00");
        seedLog(robert,  r5,  bob,   "VIEW",   0.15, 0.13, 0.17, "LOW",      true,  null,               "192.168.1.10",   "2025-02-04T09:00:00");
        seedLog(emily,   r6,  bob,   "VIEW",   0.11, 0.09, 0.13, "LOW",      true,  null,               "192.168.1.22",   "2025-02-11T10:15:00");
        seedLog(admin,   r7,  bob,   "VIEW",   0.91, 0.94, 0.88, "CRITICAL", false, "ANOMALY_DETECTED", "185.220.101.45", "2025-02-15T01:33:00");
        seedLog(james,   r8,  alice, "VIEW",   0.09, 0.08, 0.10, "LOW",      true,  null,               "192.168.1.30",   "2025-03-06T11:00:00");
        seedLog(sarah,   r9,  bob,   "VIEW",   0.14, 0.12, 0.16, "LOW",      true,  null,               "192.168.1.40",   "2025-03-13T09:30:00");
        seedLog(michael, r5,  bob,   "VIEW",   0.88, 0.91, 0.85, "CRITICAL", false, "ANOMALY_DETECTED", "203.0.113.42",   "2025-02-20T04:22:00");
        seedLog(james,   r10, alice, "VIEW",   0.10, 0.09, 0.11, "LOW",      true,  null,               "192.168.1.30",   "2025-03-11T14:00:00");
        seedLog(emily,   r10, alice, "VIEW",   0.13, 0.11, 0.15, "LOW",      true,  null,               "192.168.1.22",   "2025-03-11T15:30:00");
        seedLog(robert,  r11, alice, "VIEW",   0.17, 0.15, 0.19, "LOW",      true,  null,               "192.168.1.10",   "2025-04-02T10:00:00");
        seedLog(david,   r12, alice, "VIEW",   0.07, 0.06, 0.08, "LOW",      true,  null,               "192.168.1.50",   "2025-04-16T11:00:00");
        seedLog(lisa,    r13, alice, "VIEW",   0.06, 0.05, 0.07, "LOW",      true,  null,               "192.168.1.60",   "2025-05-03T10:00:00");
        seedLog(michael, r14, bob,   "VIEW",   0.08, 0.07, 0.09, "LOW",      true,  null,               "192.168.1.70",   "2025-03-21T09:00:00");
        seedLog(sarah,   r15, bob,   "VIEW",   0.12, 0.10, 0.14, "LOW",      true,  null,               "192.168.1.40",   "2025-04-11T10:00:00");
        seedLog(priya,   r16, bob,   "VIEW",   0.05, 0.04, 0.06, "LOW",      true,  null,               "192.168.1.80",   "2025-04-23T09:00:00");
        seedLog(lisa,    r17, bob,   "VIEW",   0.83, 0.86, 0.80, "CRITICAL", false, "ANOMALY_DETECTED", "198.51.100.23",  "2025-05-11T02:15:00");

        log.info("Seeding complete: 11 users (8 doctors, 2 patients, 1 admin), 17 records, 21 access logs");
    }

    private User createUser(String name, String email, String password, String role, String phone, Map<String, String> attrs) {
        String nonce = pseudonymService.generateNonce();
        User u = new User();
        u.setName(name); u.setEmail(email);
        u.setPassword(passwordEncoder.encode(password));
        u.setRole(role); u.setPhone(phone);
        u.setPseudonymId(pseudonymService.generatePseudonym(email, nonce));
        u.setPseudonymNonce(nonce);
        u.setAttributes(attrs);
        u.setEmailVerified(true);
        User saved = userRepository.save(u);
        // Register pseudonym on blockchain
        blockchainService.registerPseudonym(saved.getPseudonymId(), saved.getRole());
        return saved;
    }

    private MedicalRecord createRecord(User patient, String title, String desc, String type,
                                        String policy, List<String> doctorIds,
                                        List<String> doctorPIDs, String date) {
        MedicalRecord r = new MedicalRecord();
        r.setPatientId(patient.getId());
        r.setPatientPID(patient.getPseudonymId());
        r.setTitle(title); r.setDescription(desc);
        r.setFileName(title.toLowerCase().replaceAll("[^a-z0-9]", "_") + ".pdf");
        r.setFileSize("1.5 MB");
        r.setFileMimeType("application/pdf");
        r.setRecordType(type);

        String sha256Hash = org.web3j.crypto.Hash.sha3String(title + date).substring(2, 66);
        r.setSha256Hash(sha256Hash);
        r.setIpfsCid(null);
        r.setAccessPolicy(policy);
        r.setPolicyHash("0x" + Integer.toHexString(policy.hashCode()));
        r.setBlockchainRecordId("0x" + Long.toHexString(System.nanoTime()));
        r.setBlockchainTxHash("0x" + Long.toHexString(System.nanoTime() + 1));
        r.setAuthorizedDoctorIds(new java.util.ArrayList<>(doctorIds));
        r.setAuthorizedDoctorPIDs(new java.util.ArrayList<>(doctorPIDs));
        r.setUploadedAt(LocalDateTime.parse(date + "T00:00:00"));
        MedicalRecord saved = recordRepository.save(r);
        // Anchor on blockchain
        String tx = blockchainService.uploadRecord(
            saved.getId(), sha256Hash, saved.getPolicyHash(), patient.getPseudonymId());
        saved.setBlockchainTxHash(tx);
        saved.setBlockchainRecordId("0x" + org.web3j.crypto.Hash.sha3String(saved.getId()).substring(2, 18));
        return recordRepository.save(saved);
    }

    private void seedLog(User requester, MedicalRecord record, User patient,
                          String action, double anomaly, double iso, double ae,
                          String risk, boolean granted, String denyReason,
                          String ip, String timestamp) {
        AccessLog l = new AccessLog();
        l.setUserId(requester.getId());
        l.setRequesterPID(requester.getPseudonymId());
        l.setPatientPID(patient.getPseudonymId());
        l.setRecordId(record.getId());
        l.setAction(action);
        l.setAnomalyScore(anomaly); l.setIsolationScore(iso); l.setAutoencoderScore(ae);
        l.setRiskLevel(risk); l.setGranted(granted); l.setDenyReason(denyReason);
        l.setFlagged(anomaly > 0.6); l.setIpAddress(ip);
        l.setBlockchainTxHash("0x" + Long.toHexString(System.nanoTime()));
        l.setTimestamp(LocalDateTime.parse(timestamp));
        accessLogRepository.save(l);
    }
}
