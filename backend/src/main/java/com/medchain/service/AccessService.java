package com.medchain.service;

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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccessService {

    private final AccessRequestRepository accessRequestRepository;
    private final MedicalRecordRepository medicalRecordRepository;
    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final BlockchainService blockchainService;

    public AccessRequest requestAccess(AccessRequestDto dto, String doctorEmail, String ipAddress, String userAgent) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found for email: " + doctorEmail));

        if (!"DOCTOR".equalsIgnoreCase(doctor.getRole())) {
            throw new MedChainException("Only registered doctors are permitted to request record access.");
        }

        MedicalRecord record = medicalRecordRepository.findById(dto.getRecordId())
                .orElseThrow(() -> new MedChainException("Medical record not found for ID: " + dto.getRecordId()));

        if (record.getAuthorizedDoctorIds().contains(doctor.getId())) {
            throw new MedChainException("Doctor already has active APPROVED access to this record.");
        }

        Optional<AccessRequest> pendingOpt = accessRequestRepository
                .findByRecordIdAndDoctorIdAndStatus(record.getId(), doctor.getId(), "PENDING");
        if (pendingOpt.isPresent()) {
            throw new MedChainException("An access request for this record is already PENDING patient review.");
        }

        AccessRequest req = new AccessRequest();
        req.setRecordId(record.getId());
        req.setDoctorId(doctor.getId());
        req.setPatientId(record.getPatientId());
        req.setStatus("PENDING");
        req.setReason(dto.getReason() != null ? dto.getReason() : "Clinical consultation request");
        req.setRequestedAt(LocalDateTime.now());
        req.setUpdatedAt(LocalDateTime.now());

        AccessRequest saved = accessRequestRepository.save(req);

        logAudit(doctor.getId(), doctor.getName(), doctor.getRole(), "ACCESS_REQUEST",
                record.getId(), ipAddress, userAgent, true, null);

        log.info("Access request {} created by Doctor {} for Record {}", saved.getId(), doctor.getEmail(), record.getId());
        return saved;
    }

    public AccessRequest approveAccess(AccessDecisionRequest dto, String patientEmail, String ipAddress, String userAgent) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found for email: " + patientEmail));

        if ("DOCTOR".equalsIgnoreCase(patient.getRole())) {
            throw new MedChainException("Doctors cannot approve access requests.");
        }

        AccessRequest req = accessRequestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new MedChainException("Access request not found for ID: " + dto.getRequestId()));

        MedicalRecord record = medicalRecordRepository.findById(req.getRecordId())
                .orElseThrow(() -> new MedChainException("Medical record not found for ID: " + req.getRecordId()));

        // Security check: Patient can ONLY approve requests for their own records!
        if (!record.getPatientId().equals(patient.getId())) {
            logAudit(patient.getId(), patient.getName(), patient.getRole(), "ACCESS_APPROVE",
                    record.getId(), ipAddress, userAgent, false, "UNAUTHORIZED: Patient attempted to approve another patient's record request");
            throw new MedChainException("Not authorized: You cannot approve access requests for another patient's records.");
        }

        req.setStatus("APPROVED");
        int days = (dto.getDurationDays() != null && dto.getDurationDays() > 0) ? dto.getDurationDays() : 30;
        req.setExpiresAt(LocalDateTime.now().plusDays(days));
        req.setUpdatedAt(LocalDateTime.now());
        AccessRequest saved = accessRequestRepository.save(req);

        User doctor = userRepository.findById(req.getDoctorId()).orElse(null);

        if (!record.getAuthorizedDoctorIds().contains(req.getDoctorId())) {
            record.getAuthorizedDoctorIds().add(req.getDoctorId());
            if (doctor != null) {
                record.getAuthorizedDoctorPIDs().add(doctor.getPseudonymId());
            }
            medicalRecordRepository.save(record);
        }

        if (doctor != null) {
            String txHash = blockchainService.grantAccess(record.getId(), doctor.getPseudonymId(), patient.getPseudonymId());
            record.setBlockchainTxHash(txHash);
            medicalRecordRepository.save(record);
        }

        logAudit(patient.getId(), patient.getName(), patient.getRole(), "ACCESS_APPROVE",
                record.getId(), ipAddress, userAgent, true, null);

        log.info("Access request {} APPROVED by Patient {}. Valid for {} days.", saved.getId(), patient.getEmail(), days);
        return saved;
    }

    public AccessRequest rejectAccess(AccessDecisionRequest dto, String patientEmail, String ipAddress, String userAgent) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found for email: " + patientEmail));

        AccessRequest req = accessRequestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new MedChainException("Access request not found for ID: " + dto.getRequestId()));

        MedicalRecord record = medicalRecordRepository.findById(req.getRecordId())
                .orElseThrow(() -> new MedChainException("Medical record not found for ID: " + req.getRecordId()));

        if (!record.getPatientId().equals(patient.getId())) {
            throw new MedChainException("Not authorized: You cannot reject access requests for another patient's records.");
        }

        req.setStatus("REJECTED");
        req.setUpdatedAt(LocalDateTime.now());
        AccessRequest saved = accessRequestRepository.save(req);

        // Ensure doctor is removed if previously present
        record.getAuthorizedDoctorIds().remove(req.getDoctorId());
        medicalRecordRepository.save(record);

        logAudit(patient.getId(), patient.getName(), patient.getRole(), "ACCESS_REJECT",
                record.getId(), ipAddress, userAgent, true, null);

        log.info("Access request {} REJECTED by Patient {}", saved.getId(), patient.getEmail());
        return saved;
    }

    public AccessRequest revokeAccess(AccessDecisionRequest dto, String patientEmail, String ipAddress, String userAgent) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found for email: " + patientEmail));

        AccessRequest req = accessRequestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new MedChainException("Access request not found for ID: " + dto.getRequestId()));

        MedicalRecord record = medicalRecordRepository.findById(req.getRecordId())
                .orElseThrow(() -> new MedChainException("Medical record not found for ID: " + req.getRecordId()));

        if (!record.getPatientId().equals(patient.getId())) {
            throw new MedChainException("Not authorized: You cannot revoke access for another patient's records.");
        }

        req.setStatus("REVOKED");
        req.setUpdatedAt(LocalDateTime.now());
        AccessRequest saved = accessRequestRepository.save(req);

        record.getAuthorizedDoctorIds().remove(req.getDoctorId());
        User doctor = userRepository.findById(req.getDoctorId()).orElse(null);
        if (doctor != null) {
            record.getAuthorizedDoctorPIDs().remove(doctor.getPseudonymId());
        }
        medicalRecordRepository.save(record);

        if (doctor != null) {
            String txHash = blockchainService.revokeAccess(record.getId(), doctor.getPseudonymId(), patient.getPseudonymId());
            record.setBlockchainTxHash(txHash);
            medicalRecordRepository.save(record);
        }

        logAudit(patient.getId(), patient.getName(), patient.getRole(), "ACCESS_REVOKE",
                record.getId(), ipAddress, userAgent, true, null);

        log.info("Access request {} REVOKED by Patient {}", saved.getId(), patient.getEmail());
        return saved;
    }

    public List<AccessRequest> getPatientRequests(String patientEmail) {
        User patient = userRepository.findByEmail(patientEmail)
                .orElseThrow(() -> new MedChainException("Patient not found for email: " + patientEmail));
        return accessRequestRepository.findByPatientId(patient.getId());
    }

    public List<AccessRequest> getDoctorRequests(String doctorEmail) {
        User doctor = userRepository.findByEmail(doctorEmail)
                .orElseThrow(() -> new MedChainException("Doctor not found for email: " + doctorEmail));
        return accessRequestRepository.findByDoctorId(doctor.getId());
    }

    public void checkAndEnforceExpiration(String recordId, String doctorId) {
        List<AccessRequest> requests = accessRequestRepository.findByDoctorId(doctorId);
        for (AccessRequest req : requests) {
            if (req.getRecordId().equals(recordId) && "APPROVED".equalsIgnoreCase(req.getStatus())) {
                if (req.getExpiresAt() != null && req.getExpiresAt().isBefore(LocalDateTime.now())) {
                    req.setStatus("EXPIRED");
                    req.setUpdatedAt(LocalDateTime.now());
                    accessRequestRepository.save(req);

                    MedicalRecord rec = medicalRecordRepository.findById(recordId).orElse(null);
                    if (rec != null) {
                        rec.getAuthorizedDoctorIds().remove(doctorId);
                        medicalRecordRepository.save(rec);
                    }
                    log.info("Access request {} EXPIRED for Doctor {} on Record {}", req.getId(), doctorId, recordId);
                }
            }
        }
    }

    private void logAudit(String userId, String userName, String role, String action,
                          String recordId, String ipAddress, String userAgent, boolean granted, String denyReason) {
        AccessLog logEntry = new AccessLog();
        logEntry.setUserId(userId);
        logEntry.setUserName(userName);
        logEntry.setRole(role);
        logEntry.setAction(action);
        logEntry.setRecordId(recordId);
        logEntry.setIpAddress(ipAddress);
        logEntry.setUserAgent(userAgent);
        logEntry.setGranted(granted);
        logEntry.setDenyReason(denyReason);
        logEntry.setRiskLevel("LOW");
        logEntry.setTimestamp(LocalDateTime.now());
        accessLogRepository.save(logEntry);
    }
}
