package com.medchain.controller;

import com.medchain.dto.UserResponse;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.MLAlert;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.*;
import com.medchain.security.TokenBlacklistService;
import com.medchain.service.BlockchainService;
import com.medchain.service.MLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "User management, account deactivation/removal, role assignments, persistent audit logs, and security alert oversight")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final MLAlertRepository mlAlertRepository;
    private final MedicalRecordRepository recordRepository;
    private final AccessRequestRepository accessRequestRepository;
    private final ReEncryptionKeyRepository reKeyRepository;
    private final OtpTokenRepository otpTokenRepository;
    private final TokenBlacklistService tokenBlacklistService;
    private final MLService mlService;
    private final BlockchainService blockchainService;
    private final BlockchainTransactionRepository transactionRepository;

    @GetMapping("/dashboard")
    @Operation(summary = "Get administrator dashboard metrics")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalRecords", recordRepository.count(),
                "totalLogs", accessLogRepository.count(),
                "flaggedLogs", accessLogRepository.findByAnomalyScoreGreaterThan(0.6).size(),
                "mlServiceUp", mlService.isHealthy(),
                "blockchainUp", blockchainService.isAvailable() || transactionRepository.count() > 0
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "List all registered users with role and record status")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(this::toResponse).toList());
    }

    @PostMapping("/users/{userId}/deactivate")
    @Transactional
    @Operation(summary = "Deactivate a user account and immediately revoke active sessions")
    public ResponseEntity<UserResponse> deactivateUser(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));

        if (auth != null && auth.getName().equalsIgnoreCase(target.getEmail())) {
            throw new MedChainException("Administrators cannot deactivate their own account.");
        }

        target.setActive(false);
        User saved = userRepository.save(target);

        // Immediately revoke active sessions and token validation
        tokenBlacklistService.revokeUser(target.getEmail());

        logAdminAction(auth != null ? auth.getName() : "ADMIN", target, "USER_DEACTIVATED",
                "User account deactivated by administrator. Active sessions revoked; login blocked.",
                request.getRemoteAddr());

        log.info("User {} ({}) successfully deactivated by administrator {}", target.getName(), target.getEmail(), auth != null ? auth.getName() : "ADMIN");
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/users/{userId}/activate")
    @Transactional
    @Operation(summary = "Reactivate a user account")
    public ResponseEntity<UserResponse> activateUser(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));

        target.setActive(true);
        User saved = userRepository.save(target);

        // Allow subsequent authentications
        tokenBlacklistService.unrevokeUser(target.getEmail());

        logAdminAction(auth != null ? auth.getName() : "ADMIN", target, "USER_ACTIVATED",
                "User account reactivated by administrator.", request.getRemoteAddr());

        log.info("User {} ({}) reactivated by administrator {}", target.getName(), target.getEmail(), auth != null ? auth.getName() : "ADMIN");
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/users/{userId}/toggle-active")
    @Transactional
    @Operation(summary = "Toggle user account active status")
    public ResponseEntity<UserResponse> toggleUserActive(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        if (target.isActive()) {
            return deactivateUser(userId, auth, request);
        } else {
            return activateUser(userId, auth, request);
        }
    }

    @DeleteMapping("/users/{userId}")
    @Transactional
    @Operation(summary = "Permanently remove a user account if no retained medical records exist")
    public ResponseEntity<Map<String, Object>> deleteUser(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User target = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));

        if (auth != null && auth.getName().equalsIgnoreCase(target.getEmail())) {
            throw new MedChainException("Administrators cannot delete their own account.");
        }

        // Requirements 4 & 5: Do not cascade-delete medical records; enforce retention policy
        long patientRecordCount = recordRepository.countByPatientId(userId);
        if (patientRecordCount > 0) {
            throw new MedChainException(
                    "Cannot permanently delete user '" + target.getName() + "' because " + patientRecordCount +
                    " medical record(s) are associated with this patient account. Healthcare compliance and legal retention policies " +
                    "require medical records and audit trails to be preserved. Please deactivate the account instead to immediately revoke access."
            );
        }

        // If user is a Doctor, safely detach them from any authorized medical records
        List<MedicalRecord> doctorRecords = recordRepository.findByAuthorizedDoctorIdsContaining(userId);
        for (MedicalRecord rec : doctorRecords) {
            rec.getAuthorizedDoctorIds().remove(userId);
            if (target.getPseudonymId() != null) {
                rec.getAuthorizedDoctorPIDs().remove(target.getPseudonymId());
            }
            recordRepository.save(rec);
        }

        // Clean up doctor re-encryption keys
        reKeyRepository.deleteByDoctorId(userId);

        // Clean up associated access requests (where user is either patient or doctor)
        accessRequestRepository.deleteByPatientId(userId);
        accessRequestRepository.deleteByDoctorId(userId);

        // Clean up OTP tokens for user's email
        otpTokenRepository.deleteByEmail(target.getEmail());

        // Invalidate any active sessions / tokens immediately
        tokenBlacklistService.revokeUser(target.getEmail());

        // Audit logs (AccessLog) and ML alerts (MLAlert) are PRESERVED for regulatory compliance and blockchain audit trails
        logAdminAction(auth != null ? auth.getName() : "ADMIN", target, "USER_DELETED",
                "User '" + target.getName() + "' (" + target.getEmail() + ", role: " + target.getRole() +
                ") permanently removed by administrator. Ephemeral tokens and access requests cleaned up; audit trail preserved.",
                request.getRemoteAddr());

        // Delete user entity (user_attributes collection table cascades automatically)
        userRepository.delete(target);

        log.info("User {} ({}, ID: {}) permanently removed by administrator {}", target.getName(), target.getEmail(), userId, auth != null ? auth.getName() : "ADMIN");

        return ResponseEntity.ok(Map.of(
                "message", "User '" + target.getName() + "' (" + target.getEmail() + ") was permanently removed.",
                "userId", userId,
                "status", "DELETED"
        ));
    }

    @PostMapping("/users/{userId}/role")
    @Transactional
    @Operation(summary = "Update user role (PATIENT, DOCTOR, ADMIN)")
    public ResponseEntity<UserResponse> updateUserRole(@PathVariable String userId, @RequestParam("role") String role) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        String upperRole = role.toUpperCase();
        if (!List.of("PATIENT", "DOCTOR", "ADMIN").contains(upperRole)) {
            throw new MedChainException("Invalid role specified. Allowed roles: PATIENT, DOCTOR, ADMIN.");
        }
        u.setRole(upperRole);
        User saved = userRepository.save(u);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/users/{userId}/block")
    @Transactional
    @Operation(summary = "Block user account and revoke active sessions")
    public ResponseEntity<Map<String, String>> blockUser(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));

        if (auth != null && auth.getName().equalsIgnoreCase(u.getEmail())) {
            throw new MedChainException("Administrators cannot block their own account.");
        }

        u.setBlocked(true);
        userRepository.save(u);
        tokenBlacklistService.revokeUser(u.getEmail());

        logAdminAction(auth != null ? auth.getName() : "ADMIN", u, "USER_BLOCKED",
                "Account blocked by administrator. Active sessions revoked.", request.getRemoteAddr());

        return ResponseEntity.ok(Map.of("message", "User blocked successfully and sessions revoked."));
    }

    @PostMapping("/users/{userId}/unblock")
    @Transactional
    @Operation(summary = "Unblock user account")
    public ResponseEntity<Map<String, String>> unblockUser(
            @PathVariable String userId,
            Authentication auth,
            HttpServletRequest request) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        u.setBlocked(false);
        userRepository.save(u);
        tokenBlacklistService.unrevokeUser(u.getEmail());

        logAdminAction(auth != null ? auth.getName() : "ADMIN", u, "USER_UNBLOCKED",
                "Account unblocked by administrator.", request.getRemoteAddr());

        return ResponseEntity.ok(Map.of("message", "User unblocked successfully."));
    }

    @GetMapping("/audit-logs")
    @Operation(summary = "View system-wide persistent audit logs")
    public ResponseEntity<List<AccessLog>> getAuditLogs() {
        return ResponseEntity.ok(accessLogRepository.findAll());
    }

    @GetMapping("/security-alerts")
    @Operation(summary = "View system security alerts")
    public ResponseEntity<List<MLAlert>> getSecurityAlerts() {
        return ResponseEntity.ok(mlAlertRepository.findAll());
    }

    @GetMapping("/logs")
    @Operation(summary = "View all access logs")
    public ResponseEntity<List<AccessLog>> getAllLogs() {
        return ResponseEntity.ok(accessLogRepository.findAll());
    }

    @GetMapping("/logs/flagged")
    @Operation(summary = "View flagged access logs")
    public ResponseEntity<List<AccessLog>> getFlaggedLogs() {
        return ResponseEntity.ok(accessLogRepository.findByAnomalyScoreGreaterThan(0.6));
    }

    private void logAdminAction(String adminEmail, User target, String action, String details, String ip) {
        AccessLog l = new AccessLog();
        l.setUserId(target.getId());
        l.setUserName(target.getName());
        l.setRequesterPID(adminEmail);
        l.setPatientPID(target.getPseudonymId());
        l.setRole("ADMIN");
        l.setAction(action);
        l.setGranted(true);
        l.setRiskLevel("LOW");
        l.setAnomalyScore(0.01);
        l.setDenyReason(details);
        l.setIpAddress(ip != null && !ip.isBlank() ? ip : "127.0.0.1");
        l.setTimestamp(LocalDateTime.now());
        accessLogRepository.save(l);
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId());
        r.setEmail(u.getEmail());
        r.setName(u.getName());
        r.setRole(u.getRole());
        r.setPhone(u.getPhone());
        r.setPseudonymId(u.getPseudonymId());
        r.setAttributes(u.getAttributes());
        r.setActive(u.isActive());
        r.setBlocked(u.isBlocked());
        r.setEmailVerified(u.isEmailVerified());
        r.setCreatedAt(u.getCreatedAt());

        if ("PATIENT".equalsIgnoreCase(u.getRole())) {
            r.setRecordCount(recordRepository.countByPatientId(u.getId()));
        } else if ("DOCTOR".equalsIgnoreCase(u.getRole())) {
            r.setRecordCount(recordRepository.findByAuthorizedDoctorIdsContaining(u.getId()).size());
        } else {
            r.setRecordCount(0);
        }
        return r;
    }
}