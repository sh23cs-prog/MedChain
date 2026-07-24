package com.medchain.controller;

import com.medchain.dto.UserResponse;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;
import com.medchain.service.BlockchainService;
import com.medchain.service.MLService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final UserRepository userRepository;
    private final AccessLogRepository accessLogRepository;
    private final MedicalRecordRepository recordRepository;
    private final MLService mlService;
    private final BlockchainService blockchainService;

    @GetMapping("/dashboard")
    public ResponseEntity<?> getDashboard() {
        return ResponseEntity.ok(Map.of(
            "totalUsers",       userRepository.count(),
            "totalRecords",     recordRepository.count(),
            "totalLogs",        accessLogRepository.count(),
            "flaggedLogs",      accessLogRepository.findByAnomalyScoreGreaterThan(0.6).size(),
            "mlServiceUp",      mlService.isHealthy(),
            "blockchainUp",     blockchainService.isAvailable()
        ));
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserResponse>> getAllUsers() {
        return ResponseEntity.ok(userRepository.findAll().stream().map(this::toResponse).toList());
    }

    @GetMapping("/logs")
    public ResponseEntity<?> getAllLogs() {
        return ResponseEntity.ok(accessLogRepository.findAll());
    }

    @GetMapping("/logs/flagged")
    public ResponseEntity<?> getFlaggedLogs() {
        return ResponseEntity.ok(accessLogRepository.findByAnomalyScoreGreaterThan(0.6));
    }

    @GetMapping("/doctors")
    public ResponseEntity<List<UserResponse>> getDoctors() {
        return ResponseEntity.ok(userRepository.findByRole("DOCTOR").stream().map(this::toResponse).toList());
    }

    @GetMapping("/patients")
    public ResponseEntity<List<UserResponse>> getPatients() {
        return ResponseEntity.ok(userRepository.findByRole("PATIENT").stream().map(this::toResponse).toList());
    }
    @PostMapping("/users/{userId}/block")
    public ResponseEntity<?> blockUser(@PathVariable String userId) {
        return userRepository.findById(userId).map(u -> {
            u.setBlocked(true);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "User blocked"));
        }).orElse(ResponseEntity.notFound().build());
    }

    @PostMapping("/users/{userId}/unblock")
    public ResponseEntity<?> unblockUser(@PathVariable String userId) {
        return userRepository.findById(userId).map(u -> {
            u.setBlocked(false);
            userRepository.save(u);
            return ResponseEntity.ok(Map.of("message", "User unblocked"));
        }).orElse(ResponseEntity.notFound().build());
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
