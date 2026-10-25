package com.medchain.controller;

import com.medchain.dto.RecordUploadRequest;
import com.medchain.dto.UserResponse;
import com.medchain.model.AccessRequest;
import com.medchain.model.MedicalRecord;
import com.medchain.service.RecordService;
import com.medchain.service.RecordService.DownloadedFile;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
@Tag(name = "Medical Records", description = "Off-chain AES-256-GCM file storage, SHA-256 integrity verification, and access control")
@SecurityRequirement(name = "Bearer Authentication")
public class RecordController {

    private final RecordService recordService;

    @PostMapping(value = "/upload-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload medical document file (PDF, JPG, PNG, DOC, DOCX up to 50MB)")
    public ResponseEntity<MedicalRecord> uploadMultipartFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "title", required = false) String title,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "recordType", required = false) String recordType,
            Authentication auth,
            HttpServletRequest request) {
        MedicalRecord record = recordService.uploadMultipartFile(file, title, description, recordType, auth.getName(), request.getRemoteAddr());
        return ResponseEntity.ok(record);
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload medical record (JSON payload)")
    public ResponseEntity<MedicalRecord> upload(@Valid @RequestBody RecordUploadRequest req,
                                                 Authentication auth,
                                                 HttpServletRequest request) {
        return ResponseEntity.ok(recordService.uploadRecord(req, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/my")
    @Operation(summary = "Get current patient's medical records")
    public ResponseEntity<List<MedicalRecord>> getMyRecords(Authentication auth) {
        return ResponseEntity.ok(recordService.getPatientRecords(auth.getName()));
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get records for a specific patient ID")
    public ResponseEntity<List<MedicalRecord>> getPatientRecords(@PathVariable String patientId) {
        return ResponseEntity.ok(recordService.getPatientRecords(patientId));
    }

    @GetMapping("/doctor/authorized")
    @Operation(summary = "Get records authorized for current doctor")
    public ResponseEntity<List<MedicalRecord>> getDoctorRecords(Authentication auth) {
        return ResponseEntity.ok(recordService.getDoctorRecords(auth.getName()));
    }

    @GetMapping("/doctors")
    @Operation(summary = "List available doctors")
    public ResponseEntity<List<UserResponse>> getDoctors() {
        return ResponseEntity.ok(recordService.getAllDoctors());
    }

    @GetMapping("/patients")
    @Operation(summary = "List available patients")
    public ResponseEntity<List<UserResponse>> getPatients() {
        return ResponseEntity.ok(recordService.getAllPatients());
    }

    @PostMapping("/{recordId}/grant/{doctorId}")
    @Operation(summary = "Grant doctor access to medical record")
    public ResponseEntity<MedicalRecord> grantAccess(@PathVariable String recordId,
                                                      @PathVariable String doctorId,
                                                      Authentication auth,
                                                      HttpServletRequest request) {
        return ResponseEntity.ok(recordService.grantAccess(recordId, doctorId, auth.getName(), request.getRemoteAddr()));
    }

    @PostMapping("/{recordId}/revoke/{doctorId}")
    @Operation(summary = "Revoke doctor access from medical record")
    public ResponseEntity<MedicalRecord> revokeAccess(@PathVariable String recordId,
                                                       @PathVariable String doctorId,
                                                       Authentication auth,
                                                       HttpServletRequest request) {
        return ResponseEntity.ok(recordService.revokeAccess(recordId, doctorId, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/{recordId}/view")
    @Operation(summary = "View decrypted record content with SHA-256 integrity verification")
    public ResponseEntity<Map<String, Object>> viewRecord(@PathVariable String recordId,
                                                           Authentication auth,
                                                           HttpServletRequest request) {
        return ResponseEntity.ok(recordService.viewRecord(recordId, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/{recordId}/download")
    @Operation(summary = "Download decrypted record file attachment with SHA-256 verification")
    public ResponseEntity<byte[]> downloadRecordFile(@PathVariable String recordId,
                                                      Authentication auth,
                                                      HttpServletRequest request) {
        DownloadedFile df = recordService.downloadRecordFile(recordId, auth.getName(), request.getRemoteAddr());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, df.mimeType())
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + df.filename() + "\"")
                .header("X-SHA256-Checksum", df.sha256Hash())
                .body(df.fileBytes());
    }

    @PostMapping("/{recordId}/request-access")
    @Operation(summary = "Doctor requests access to medical record")
    public ResponseEntity<AccessRequest> requestAccess(@PathVariable String recordId,
                                                       Authentication auth) {
        return ResponseEntity.ok(recordService.requestAccess(recordId, auth.getName()));
    }

    @GetMapping("/requests/patient")
    @Operation(summary = "Patient retrieves pending access requests")
    public ResponseEntity<List<AccessRequest>> getPatientRequests(Authentication auth) {
        return ResponseEntity.ok(recordService.getPatientAccessRequests(auth.getName()));
    }

    @PostMapping("/requests/{requestId}/deny")
    @Operation(summary = "Patient denies doctor access request")
    public ResponseEntity<AccessRequest> denyAccessRequest(@PathVariable String requestId,
                                                            Authentication auth) {
        return ResponseEntity.ok(recordService.denyAccessRequest(requestId, auth.getName()));
    }
}
