package com.medchain.controller;

import com.medchain.dto.UserResponse;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.MLAlert;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.service.BlockchainService;
import com.medchain.service.MLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin Operations", description = "User management, role assignments, persistent audit logs, and security alert oversight")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final MLAlertRepository mlAlertRepository;
    private final MedicalRecordRepository recordRepository;
    private final MLService mlService;
    private final BlockchainService blockchainService;

    @GetMapping("/dashboard")
    @Operation(summary = "Get administrator dashboard metrics")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalRecords", recordRepository.count(),
                "totalLogs", accessLogRepository.count(),
                "flaggedLogs", accessLogRepository.findByAnomalyScoreGreaterThan(0.6).size(),
                "mlServiceUp", mlService.isHealthy(),
                "blockchainUp", blockchainService.isAvailable()
        ));
    }

    @GetMapping("/users")
    @Operation(summary = "List all registered users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(this::toResponse).toList());
    }

    @PostMapping("/users/{userId}/toggle-active")
    @Operation(summary = "Activate or deactivate a user account")
    public ResponseEntity<UserResponse> toggleUserActive(@PathVariable String userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        u.setActive(!u.isActive());
        User saved = userRepository.save(u);
        return ResponseEntity.ok(toResponse(saved));
    }

    @PostMapping("/users/{userId}/role")
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

    @PostMapping("/users/{userId}/block")
    @Operation(summary = "Block user account")
    public ResponseEntity<Map<String, String>> blockUser(@PathVariable String userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        u.setBlocked(true);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "User blocked successfully."));
    }

    @PostMapping("/users/{userId}/unblock")
    @Operation(summary = "Unblock user account")
    public ResponseEntity<Map<String, String>> unblockUser(@PathVariable String userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new MedChainException("User not found for ID: " + userId));
        u.setBlocked(false);
        userRepository.save(u);
        return ResponseEntity.ok(Map.of("message", "User unblocked successfully."));
    }

    private UserResponse toResponse(User u) {
        UserResponse r = new UserResponse();
        r.setId(u.getId()); r.setEmail(u.getEmail()); r.setName(u.getName());
        r.setRole(u.getRole()); r.setPhone(u.getPhone()); r.setPseudonymId(u.getPseudonymId());
        r.setAttributes(u.getAttributes()); r.setActive(u.isActive()); r.setBlocked(u.isBlocked());
        r.setCreatedAt(u.getCreatedAt());
        return r;
    }
}
