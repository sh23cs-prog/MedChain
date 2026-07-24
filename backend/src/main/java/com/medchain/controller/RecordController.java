package com.medchain.controller;

import com.medchain.dto.RecordUploadRequest;
import com.medchain.dto.UserResponse;
import com.medchain.model.AccessRequest;
import com.medchain.model.MedicalRecord;
import com.medchain.service.RecordService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/records")
@RequiredArgsConstructor
public class RecordController {

    private final RecordService recordService;

    @PostMapping("/upload")
    public ResponseEntity<MedicalRecord> upload(@Valid @RequestBody RecordUploadRequest req,
                                                 Authentication auth,
                                                 HttpServletRequest request) {
        return ResponseEntity.ok(recordService.uploadRecord(req, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/patient/{patientId}")
    public ResponseEntity<List<MedicalRecord>> getPatientRecords(@PathVariable String patientId) {
        return ResponseEntity.ok(recordService.getPatientRecords(patientId));
    }

    @GetMapping("/doctor/authorized")
    public ResponseEntity<List<MedicalRecord>> getDoctorRecords(Authentication auth) {
        return ResponseEntity.ok(recordService.getDoctorRecords(auth.getName()));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<UserResponse>> getDoctors() {
        return ResponseEntity.ok(recordService.getAllDoctors());
    }

    @GetMapping("/patients")
    public ResponseEntity<List<UserResponse>> getPatients() {
        return ResponseEntity.ok(recordService.getAllPatients());
    }

    @PostMapping("/{recordId}/grant/{doctorId}")
    public ResponseEntity<MedicalRecord> grantAccess(@PathVariable String recordId,
                                                      @PathVariable String doctorId,
                                                      Authentication auth,
                                                      HttpServletRequest request) {
        return ResponseEntity.ok(recordService.grantAccess(recordId, doctorId, auth.getName(), request.getRemoteAddr()));
    }

    @PostMapping("/{recordId}/revoke/{doctorId}")
    public ResponseEntity<MedicalRecord> revokeAccess(@PathVariable String recordId,
                                                       @PathVariable String doctorId,
                                                       Authentication auth,
                                                       HttpServletRequest request) {
        return ResponseEntity.ok(recordService.revokeAccess(recordId, doctorId, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/{recordId}/view")
    public ResponseEntity<Map<String, Object>> viewRecord(@PathVariable String recordId,
                                                           Authentication auth,
                                                           HttpServletRequest request) {
        return ResponseEntity.ok(recordService.viewRecord(recordId, auth.getName(), request.getRemoteAddr()));
    }

    @GetMapping("/{recordId}/file")
    public ResponseEntity<byte[]> getRecordFile(@PathVariable String recordId, Authentication auth) {
        String dataUrl = recordService.getRecordFile(recordId, auth.getName());
        // dataUrl format: "data:<mime>;base64,<data>"
        int commaIdx = dataUrl.indexOf(',');
        String meta = dataUrl.substring(5, dataUrl.indexOf(';'));
        byte[] bytes = java.util.Base64.getDecoder().decode(dataUrl.substring(commaIdx + 1));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, meta)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(bytes);
    }

    @PostMapping("/{recordId}/request-access")
    public ResponseEntity<AccessRequest> requestAccess(@PathVariable String recordId,
                                                       Authentication auth) {
        return ResponseEntity.ok(recordService.requestAccess(recordId, auth.getName()));
    }

    @GetMapping("/requests/patient")
    public ResponseEntity<List<AccessRequest>> getPatientRequests(Authentication auth) {
        return ResponseEntity.ok(recordService.getPatientAccessRequests(auth.getName()));
    }

    @PostMapping("/requests/{requestId}/deny")
    public ResponseEntity<AccessRequest> denyRequest(@PathVariable String requestId,
                                                     Authentication auth) {
        return ResponseEntity.ok(recordService.denyAccessRequest(requestId, auth.getName()));
    }
}
