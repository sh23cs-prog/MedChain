package com.medchain.controller;

import com.medchain.exception.MedChainException;
import com.medchain.model.MLAlert;
import com.medchain.model.MLPrediction;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MLPredictionRepository;
import com.medchain.service.MLService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ml")
@RequiredArgsConstructor
@Tag(name = "Sentinel AI Dashboard", description = "ML Anomaly Detection, Alert Monitoring, and Risk Analysis APIs")
@SecurityRequirement(name = "Bearer Authentication")
public class MLSentinelController {

    private final MLService mlService;
    private final MLPredictionRepository predictionRepository;
    private final MLAlertRepository alertRepository;

    @GetMapping("/stats")
    @Operation(summary = "Get Sentinel AI dashboard statistics",
               description = "Returns genuine predictions, normal vs anomaly counts, risk distribution, open alert count, and model health")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalPredictions = predictionRepository.count();
        long anomalyCount = predictionRepository.countByAnomalyTrue();
        long normalCount = Math.max(0, totalPredictions - anomalyCount);

        long lowCount = predictionRepository.countByRiskLevel("LOW");
        long mediumCount = predictionRepository.countByRiskLevel("MEDIUM");
        long highCount = predictionRepository.countByRiskLevel("HIGH");
        long criticalCount = predictionRepository.countByRiskLevel("CRITICAL");

        long openAlerts = alertRepository.countByStatus("OPEN");
        long ackAlerts = alertRepository.countByStatus("ACKNOWLEDGED");
        long resolvedAlerts = alertRepository.countByStatus("RESOLVED");

        Map<String, Long> riskDistribution = new HashMap<>();
        riskDistribution.put("LOW", lowCount);
        riskDistribution.put("MEDIUM", mediumCount);
        riskDistribution.put("HIGH", highCount);
        riskDistribution.put("CRITICAL", criticalCount);

        Map<String, Object> response = new HashMap<>();
        response.put("totalPredictions", totalPredictions);
        response.put("normalCount", normalCount);
        response.put("anomalyCount", anomalyCount);
        response.put("riskDistribution", riskDistribution);
        response.put("openAlerts", openAlerts);
        response.put("acknowledgedAlerts", ackAlerts);
        response.put("resolvedAlerts", resolvedAlerts);
        response.put("modelHealth", mlService.isHealthy() ? "HEALTHY" : "DEGRADED");
        response.put("modelVersion", "v1.2.0");

        return ResponseEntity.ok(response);
    }

    @GetMapping("/alerts")
    @Operation(summary = "List ML Anomaly Alerts",
               description = "Retrieves ML alerts with optional status filter (OPEN, ACKNOWLEDGED, RESOLVED)")
    public ResponseEntity<List<MLAlert>> getAlerts(@RequestParam(required = false) String status) {
        if (status != null && !status.isBlank()) {
            return ResponseEntity.ok(alertRepository.findByStatus(status.toUpperCase()));
        }
        return ResponseEntity.ok(alertRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp")));
    }

    @GetMapping("/predictions")
    @Operation(summary = "List ML Anomaly Predictions")
    public ResponseEntity<List<MLPrediction>> getPredictions(
            @RequestParam(defaultValue = "50") int limit) {
        List<MLPrediction> predictions = predictionRepository.findAll(
                Sort.by(Sort.Direction.DESC, "timestamp")).stream()
                .limit(limit)
                .toList();
        return ResponseEntity.ok(predictions);
    }

    @PostMapping("/alerts/{id}/status")
    @Operation(summary = "Update ML Alert Status",
               description = "Transition alert status between OPEN, ACKNOWLEDGED, and RESOLVED")
    public ResponseEntity<MLAlert> updateAlertStatus(
            @PathVariable String id,
            @RequestParam String status) {
        MLAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new MedChainException("ML Alert not found for ID: " + id));

        String newStatus = status.toUpperCase();
        if (!List.of("OPEN", "ACKNOWLEDGED", "RESOLVED").contains(newStatus)) {
            throw new MedChainException("Invalid status: " + status + ". Allowed: OPEN, ACKNOWLEDGED, RESOLVED");
        }

        alert.setStatus(newStatus);
        if ("RESOLVED".equals(newStatus)) {
            alert.setResolvedAt(LocalDateTime.now());
        }
        MLAlert saved = alertRepository.save(alert);
        return ResponseEntity.ok(saved);
    }

    private final com.medchain.repository.UserRepository userRepository;

    @PostMapping("/evaluate")
    @Operation(summary = "Evaluate access request using Sentinel AI")
    public ResponseEntity<MLService.AnomalyResponse> evaluateAccess(
            @RequestBody MLService.AnomalyRequest req,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String recordId,
            org.springframework.security.core.Authentication auth,
            jakarta.servlet.http.HttpServletRequest httpRequest) {
        
        String effectiveUserId = userId;
        if ((effectiveUserId == null || effectiveUserId.isBlank()) && auth != null) {
            String email = auth.getName();
            var uOpt = userRepository.findByEmail(email);
            if (uOpt.isPresent()) {
                effectiveUserId = uOpt.get().getId();
            }
        }
        
        if (req != null && (req.getIpAddress() == null || req.getIpAddress().isBlank())) {
            String remoteAddr = httpRequest != null ? httpRequest.getRemoteAddr() : null;
            req.setIpAddress(remoteAddr != null && !remoteAddr.isBlank() ? remoteAddr : "127.0.0.1");
        }

        MLService.AnomalyResponse res = mlService.scoreAccess(req, effectiveUserId, recordId);
        return ResponseEntity.ok(res);
    }
}
