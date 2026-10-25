package com.medchain;

import com.medchain.dto.RegisterRequest;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.MLAlert;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.service.AESEncryptionService;
import com.medchain.service.FileValidationService;
import com.medchain.service.RecordService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev")
public class FileEncryptionIntegrityTest {

    @Autowired
    private FileValidationService fileValidationService;

    @Autowired
    private AESEncryptionService aesService;

    @Autowired
    private RecordService recordService;

    @Autowired
    private MedicalRecordRepository recordRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AccessLogRepository accessLogRepository;

    @Autowired
    private MLAlertRepository mlAlertRepository;

    private String patientEmail = "patient.filetest@medchain.com";
    private String doctorEmail = "doctor.filetest@medchain.com";
    private String unauthorizedDoctorEmail = "unauth.doctor@medchain.com";

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail(patientEmail)) {
            User p = new User();
            p.setName("File Test Patient");
            p.setEmail(patientEmail);
            p.setPassword("Password123!");
            p.setRole("PATIENT");
            p.setEmailVerified(true);
            p.setPseudonymId("0xpatientfiletestpid");
            userRepository.save(p);
        }

        if (!userRepository.existsByEmail(doctorEmail)) {
            User d = new User();
            d.setName("File Test Doctor");
            d.setEmail(doctorEmail);
            d.setPassword("Password123!");
            d.setRole("DOCTOR");
            d.setEmailVerified(true);
            d.setPseudonymId("0xdoctorfiletestpid");
            userRepository.save(d);
        }

        if (!userRepository.existsByEmail(unauthorizedDoctorEmail)) {
            User u = new User();
            u.setName("Unauthorized Doctor");
            u.setEmail(unauthorizedDoctorEmail);
            u.setPassword("Password123!");
            u.setRole("DOCTOR");
            u.setEmailVerified(true);
            u.setPseudonymId("0xunauthdoctorpid");
            userRepository.save(u);
        }
    }

    @Test
    @DisplayName("Phase 3: File Validation (Types, Size Limit, Filename Sanitization, SHA-256)")
    void testFileValidationEngine() {
        // 1. Valid file
        MockMultipartFile validPdf = new MockMultipartFile(
                "file", "blood_report.pdf", "application/pdf", "Sample PDF Medical Content".getBytes(StandardCharsets.UTF_8));
        assertDoesNotThrow(() -> fileValidationService.validateFile(validPdf));

        // 2. Disallowed extension (.exe)
        MockMultipartFile invalidExt = new MockMultipartFile(
                "file", "malware.exe", "application/octet-stream", "Binary".getBytes());
        assertThrows(MedChainException.class, () -> fileValidationService.validateFile(invalidExt));

        // 3. MIME mismatch (.pdf with image/jpeg mime)
        MockMultipartFile mimeMismatch = new MockMultipartFile(
                "file", "fake_doc.pdf", "image/jpeg", "Content".getBytes());
        assertThrows(MedChainException.class, () -> fileValidationService.validateFile(mimeMismatch));

        // 4. Filename sanitization
        String maliciousName = "../../path/traversal_test<script>.pdf";
        String sanitized = fileValidationService.sanitizeFilename(maliciousName);
        assertEquals("traversal_test_script_.pdf", sanitized);

        // 5. SHA-256 calculation
        byte[] data = "Hello MedChain Plaintext".getBytes(StandardCharsets.UTF_8);
        String hash1 = fileValidationService.calculateSHA256(data);
        String hash2 = fileValidationService.calculateSHA256(data);
        assertEquals(hash1, hash2);
        assertEquals(64, hash1.length());
    }

    @Test
    @DisplayName("Phase 3: AES-256-GCM Encryption, Unique IV, and Tag Verification")
    void testAES256GCMEncryption() {
        byte[] plaintext = "Confidential Patient Record Payload".getBytes(StandardCharsets.UTF_8);

        // Encryption 1
        AESEncryptionService.EncryptionResult res1 = aesService.encrypt(plaintext);
        // Encryption 2
        AESEncryptionService.EncryptionResult res2 = aesService.encrypt(plaintext);

        // Unique IV check
        assertNotEquals(res1.ivBase64(), res2.ivBase64(), "Each encryption must generate a unique random 12-byte IV");

        // Decryption check
        byte[] decrypted = aesService.decrypt(res1.ciphertext(), res1.aesKeyBase64(), res1.ivBase64());
        assertArrayEquals(plaintext, decrypted);

        // Authentication tag verification (tamper with ciphertext byte)
        byte[] tamperedCiphertext = res1.ciphertext().clone();
        tamperedCiphertext[0] ^= 0xFF;
        assertThrows(RuntimeException.class, () -> aesService.decrypt(tamperedCiphertext, res1.aesKeyBase64(), res1.ivBase64()),
                "GCM authentication tag verification must fail on tampered ciphertext");
    }

    @Test
    @DisplayName("Phase 3: End-to-End File Upload, Off-Chain Storage & SHA-256 Integrity Verification")
    void testMultipartUploadAndViewFlow() {
        byte[] pdfBytes = "%PDF-1.4 Official Lab Results Confidential Payload".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "lab_report.pdf", "application/pdf", pdfBytes);

        // Upload
        MedicalRecord record = recordService.uploadMultipartFile(file, "Lab Results", "CBC Blood Panel", "LAB_REPORT", patientEmail, "127.0.0.1");
        assertNotNull(record.getId());
        assertNotNull(record.getSha256Hash());
        assertNotNull(record.getStoragePath());

        // Grant Doctor access
        User doctor = userRepository.findByEmail(doctorEmail).orElseThrow();
        recordService.grantAccess(record.getId(), doctor.getId(), patientEmail, "127.0.0.1");

        // View as authorized doctor
        Map<String, Object> viewRes = recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1");
        assertTrue((Boolean) viewRes.get("integrityVerified"));
        assertEquals(record.getSha256Hash(), viewRes.get("sha256Hash"));

        // Download as authorized doctor
        RecordService.DownloadedFile df = recordService.downloadRecordFile(record.getId(), doctorEmail, "127.0.0.1");
        assertArrayEquals(pdfBytes, df.fileBytes());
        assertEquals(record.getSha256Hash(), df.sha256Hash());
    }

    @Test
    @DisplayName("Phase 3: Tampered Encrypted File Detection, INTEGRITY_FAILURE & CRITICAL MLAlert Creation")
    void testTamperedFileIntegrityFailureDetection() throws Exception {
        byte[] originalContent = "Original Untampered Medical Note".getBytes(StandardCharsets.UTF_8);
        MockMultipartFile file = new MockMultipartFile(
                "file", "notes.pdf", "application/pdf", originalContent);

        MedicalRecord record = recordService.uploadMultipartFile(file, "Medical Note", "Clinical notes", "CONSULTATION", patientEmail, "127.0.0.1");
        User doctor = userRepository.findByEmail(doctorEmail).orElseThrow();
        recordService.grantAccess(record.getId(), doctor.getId(), patientEmail, "127.0.0.1");

        // Tamper with encrypted storage payload on disk (flip bytes)
        byte[] storageBytes = Files.readAllBytes(Paths.get(record.getStoragePath()));
        storageBytes[storageBytes.length - 1] ^= 0xFF;
        Files.write(Paths.get(record.getStoragePath()), storageBytes);

        // Viewing tampered file must fail with MedChainException, log INTEGRITY_FAILURE, and create CRITICAL MLAlert
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), doctorEmail, "127.0.0.1"));

        // Check INTEGRITY_FAILURE audit log
        List<AccessLog> logs = accessLogRepository.findByRecordId(record.getId());
        boolean hasIntegrityFailure = logs.stream().anyMatch(l -> "INTEGRITY_FAILURE".equalsIgnoreCase(l.getAction()));
        assertTrue(hasIntegrityFailure, "INTEGRITY_FAILURE audit log must be recorded");

        // Check CRITICAL MLAlert created
        List<MLAlert> alerts = mlAlertRepository.findByUserId(doctor.getId());
        boolean hasCriticalAlert = alerts.stream().anyMatch(a -> "CRITICAL".equalsIgnoreCase(a.getRiskLevel()));
        assertTrue(hasCriticalAlert, "CRITICAL MLAlert must be persisted for integrity failure");
    }

    @Test
    @DisplayName("Phase 3: Unauthorized Access Rejection (403 / Access Denied)")
    void testUnauthorizedFileAccessDenial() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "private_record.pdf", "application/pdf", "Private Payload".getBytes(StandardCharsets.UTF_8));

        MedicalRecord record = recordService.uploadMultipartFile(file, "Private Record", "Patient Only", "LAB_REPORT", patientEmail, "127.0.0.1");

        // Doctor WITHOUT access approval attempts view/download -> Denied
        assertThrows(MedChainException.class, () -> recordService.viewRecord(record.getId(), unauthorizedDoctorEmail, "127.0.0.1"));
        assertThrows(MedChainException.class, () -> recordService.downloadRecordFile(record.getId(), unauthorizedDoctorEmail, "127.0.0.1"));
    }

    @Test
    @DisplayName("Phase 3: Verify MIME Type Detection, AES-256-GCM Decryption, SHA-256 Hash & Content-Type Headers for PDF, JPEG, PNG")
    void testMimeTypeAndPreviewFlow() {
        User doctor = userRepository.findByEmail(doctorEmail).orElseThrow();

        // 1. Real PDF File (%PDF- magic header)
        byte[] pdfBytes = ("%PDF-1.5\n1 0 obj\n<< /Type /Catalog >>\nendobj\n%%EOF").getBytes(StandardCharsets.UTF_8);
        MockMultipartFile pdfFile = new MockMultipartFile("file", "chest_xray_report.pdf", "application/pdf", pdfBytes);

        MedicalRecord pdfRecord = recordService.uploadMultipartFile(pdfFile, "Chest X-Ray Report", "Pulmonology Evaluation", "LAB_REPORT", patientEmail, "127.0.0.1");
        recordService.grantAccess(pdfRecord.getId(), doctor.getId(), patientEmail, "127.0.0.1");

        assertEquals("chest_xray_report.pdf", pdfRecord.getFileName());
        assertEquals("application/pdf", pdfRecord.getFileMimeType());
        assertNotNull(pdfRecord.getStoragePath());

        Map<String, Object> pdfView = recordService.viewRecord(pdfRecord.getId(), doctorEmail, "127.0.0.1");
        String pdfDataUrl = (String) pdfView.get("decryptedContent");
        assertTrue(pdfDataUrl.startsWith("data:application/pdf;base64,"), "PDF preview URL must start with data:application/pdf;base64,");

        RecordService.DownloadedFile pdfDl = recordService.downloadRecordFile(pdfRecord.getId(), doctorEmail, "127.0.0.1");
        assertEquals("application/pdf", pdfDl.mimeType());
        assertEquals(pdfRecord.getSha256Hash(), pdfDl.sha256Hash());


        // 2. Real JPEG File (\xFF\xD8\xFF magic header)
        byte[] jpegBytes = new byte[] {
                (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
                0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x60
        };
        MockMultipartFile jpegFile = new MockMultipartFile("file", "dermatology_scan.jpg", "image/jpeg", jpegBytes);

        MedicalRecord jpegRecord = recordService.uploadMultipartFile(jpegFile, "Dermatology Scan", "Skin lesion photo", "SCAN", patientEmail, "127.0.0.1");
        recordService.grantAccess(jpegRecord.getId(), doctor.getId(), patientEmail, "127.0.0.1");

        assertEquals("dermatology_scan.jpg", jpegRecord.getFileName());
        assertEquals("image/jpeg", jpegRecord.getFileMimeType());

        Map<String, Object> jpegView = recordService.viewRecord(jpegRecord.getId(), doctorEmail, "127.0.0.1");
        String jpegDataUrl = (String) jpegView.get("decryptedContent");
        assertTrue(jpegDataUrl.startsWith("data:image/jpeg;base64,/9j/"), "JPEG preview URL must start with data:image/jpeg;base64,/9j/");

        RecordService.DownloadedFile jpegDl = recordService.downloadRecordFile(jpegRecord.getId(), doctorEmail, "127.0.0.1");
        assertEquals("image/jpeg", jpegDl.mimeType());
        assertEquals(jpegRecord.getSha256Hash(), jpegDl.sha256Hash());


        // 3. Real PNG File (\x89PNG magic header)
        byte[] pngBytes = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                0x00, 0x00, 0x00, 0x0D, 0x49, 0x48, 0x44, 0x52
        };
        MockMultipartFile pngFile = new MockMultipartFile("file", "mri_brain_scan.png", "image/png", pngBytes);

        MedicalRecord pngRecord = recordService.uploadMultipartFile(pngFile, "MRI Brain Scan", "Cerebral coronal view", "SCAN", patientEmail, "127.0.0.1");
        recordService.grantAccess(pngRecord.getId(), doctor.getId(), patientEmail, "127.0.0.1");

        assertEquals("mri_brain_scan.png", pngRecord.getFileName());
        assertEquals("image/png", pngRecord.getFileMimeType());

        Map<String, Object> pngView = recordService.viewRecord(pngRecord.getId(), doctorEmail, "127.0.0.1");
        String pngDataUrl = (String) pngView.get("decryptedContent");
        assertTrue(pngDataUrl.startsWith("data:image/png;base64,iVBORw0KGgo"), "PNG preview URL must start with data:image/png;base64,iVBORw0KGgo");

        RecordService.DownloadedFile pngDl = recordService.downloadRecordFile(pngRecord.getId(), doctorEmail, "127.0.0.1");
        assertEquals("image/png", pngDl.mimeType());
        assertEquals(pngRecord.getSha256Hash(), pngDl.sha256Hash());
    }
}
