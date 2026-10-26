package com.medchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchain.model.MLAlert;
import com.medchain.model.MLPrediction;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MLPredictionRepository;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import jakarta.annotation.PostConstruct;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * MLService — Spring Boot integration bridge with Sentinel AI FastAPI Microservice.
 *
 * Evaluates real-time behavioral telemetry, calculates anomaly scores, enforces exact
 * risk boundaries (<0.25 LOW, 0.25-0.49 MEDIUM, 0.50-0.74 HIGH, >=0.75 CRITICAL),
 * and automatically persists MLPrediction and MLAlert (OPEN) records for HIGH/CRITICAL events.
 */
import com.medchain.model.AccessLog;
import com.medchain.model.MedicalRecord;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import java.util.Optional;

import com.medchain.repository.MedicalRecordRepository;
import com.medchain.repository.UserRepository;

@Service
@RequiredArgsConstructor
@Slf4j
public class MLService {

    private final MLPredictionRepository predictionRepository;
    private final MLAlertRepository alertRepository;
    private final AccessLogRepository accessLogRepository;
    private final UserRepository userRepository;
    private final MedicalRecordRepository recordRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${ml.service.url:http://localhost:8000}")
    private String mlServiceUrl;

    private RestTemplate restTemplate;

    @PostConstruct
    public void init() {
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .readTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Data
    public static class AnomalyRequest {
        private double hour_of_day;
        private double day_of_week;
        private double records_per_hour;
        private double time_since_last;
        private double specialty_match = 1.0;
        private double hospital_match = 1.0;
        private double access_freq_7d = 10.0;
        private double unique_patients_1h = 1.0;
        private String ipAddress;

        // Backward compatibility setter
        public void setHour(double hour) { this.hour_of_day = hour; }
        public double getHour() { return this.hour_of_day; }
    }

    @Data
    public static class AnomalyResponse {
        private double anomaly_score;
        private double isolation_score;
        private double autoencoder_score;
        private boolean is_anomaly;
        private String risk_level; // LOW, MEDIUM, HIGH, CRITICAL
        private String action;     // GRANT_ACCESS, EXTRA_VERIFICATION, DENY_ACCESS, LOCKDOWN
        private int riskScore;     // 0-100
        private String status;     // SAFE, SUSPICIOUS, BLOCKED
        private Map<String, Object> explainable_report;

        public boolean isAnomaly() { return is_anomaly; }
        public void set_anomaly(boolean is_anomaly) { this.is_anomaly = is_anomaly; }
    }

    public AnomalyRequest buildTelemetry(User doctor, MedicalRecord record) {
        LocalDateTime now = LocalDateTime.now();
        double hourOfDay = now.getHour() + (now.getMinute() / 60.0);
        double dayOfWeek = (double) (now.getDayOfWeek().getValue() - 1);

        double recordsPerHour = 0.0;
        double timeSinceLast = 60.0;
        double accessFreq7d = 0.0;
        double uniquePatients1h = 0.0;

        if (doctor != null && doctor.getId() != null) {
            LocalDateTime oneHourAgo = now.minusHours(1);
            LocalDateTime sevenDaysAgo = now.minusDays(7);

            recordsPerHour = (double) accessLogRepository.countByUserIdAndTimestampAfter(doctor.getId(), oneHourAgo);
            accessFreq7d = (double) accessLogRepository.countByUserIdAndTimestampAfter(doctor.getId(), sevenDaysAgo);
            uniquePatients1h = (double) accessLogRepository.countDistinctPatientPIDByUserIdAndTimestampAfter(doctor.getId(), oneHourAgo);

            Optional<AccessLog> lastLogOpt = accessLogRepository.findTopByUserIdOrderByTimestampDesc(doctor.getId());
            if (lastLogOpt.isPresent() && lastLogOpt.get().getTimestamp() != null) {
                long minutes = java.time.Duration.between(lastLogOpt.get().getTimestamp(), now).toMinutes();
                timeSinceLast = Math.max(0.1, (double) minutes);
            }
        }

        double specialtyMatch = 1.0;
        if (doctor != null && doctor.getAttributes() != null && record != null) {
            String docSpecialty = doctor.getAttributes().get("specialty");
            String policy = record.getAccessPolicy();
            if (docSpecialty != null && policy != null && !policy.equalsIgnoreCase("specialty:ALL")) {
                if (!policy.toUpperCase().contains(docSpecialty.toUpperCase())) {
                    specialtyMatch = 0.0;
                }
            }
        }

        double hospitalMatch = 1.0;
        if (doctor != null && doctor.getAttributes() != null && record != null) {
            String docHospital = doctor.getAttributes().get("hospital");
            String recHospital = record.getHospitalOrgId();
            if (docHospital != null && recHospital != null && !recHospital.isBlank()) {
                if (!recHospital.toUpperCase().contains(docHospital.toUpperCase()) && !docHospital.toUpperCase().contains(recHospital.toUpperCase())) {
                    hospitalMatch = 0.0;
                }
            }
        }

        AnomalyRequest req = new AnomalyRequest();
        req.setHour_of_day(hourOfDay);
        req.setDay_of_week(dayOfWeek);
        req.setRecords_per_hour(recordsPerHour);
        req.setTime_since_last(timeSinceLast);
        req.setSpecialty_match(specialtyMatch);
        req.setHospital_match(hospitalMatch);
        req.setAccess_freq_7d(accessFreq7d);
        req.setUnique_patients_1h(uniquePatients1h);
        return req;
    }

    public AnomalyResponse scoreAccess(AnomalyRequest req) {
        return scoreAccess(req, "system_user", "system_record");
    }

    public AnomalyResponse scoreAccess(AnomalyRequest req, String userId, String recordId) {
        AnomalyResponse res = null;
        try {
            res = restTemplate.postForObject(mlServiceUrl + "/predict", req, AnomalyResponse.class);
        } catch (Exception e) {
            log.debug("Sentinel AI microservice offline at {} — evaluating via internal engine fallback", mlServiceUrl);
            res = internalEvaluation(req);
        }

        if (res == null) {
            res = internalEvaluation(req);
        }

        enrichResponse(res);
        persistPredictionAndAlert(res, userId, recordId, req);
        return res;
    }

    private void persistPredictionAndAlert(AnomalyResponse res, String userId, String recordId, AnomalyRequest req) {
        try {
            double score = res.getAnomaly_score();
            String riskLevel = score >= 0.75 ? "CRITICAL" :
                               score >= 0.50 ? "HIGH" :
                               score >= 0.25 ? "MEDIUM" : "LOW";
            res.setRisk_level(riskLevel);

            // 1. Save MLPrediction
            MLPrediction pred = new MLPrediction();
            pred.setUserId(userId != null ? userId : "system_user");
            pred.setRecordId(recordId != null ? recordId : "system_record");
            pred.setRiskScore(score);
            pred.setRiskLevel(riskLevel);
            pred.setAnomalyStatus(res.getStatus());
            pred.setModelVersion("v1.2.0");
            pred.setAnomaly(res.is_anomaly());
            pred.setExplainableReportJson(objectMapper.writeValueAsString(res.getExplainable_report() != null ? res.getExplainable_report() : Map.of()));
            pred.setTimestamp(LocalDateTime.now());
            predictionRepository.save(pred);

            // 2. Save MLAlert (Status: OPEN) if HIGH or CRITICAL risk (anomalyScore >= 0.50)
            if ("HIGH".equalsIgnoreCase(riskLevel) || "CRITICAL".equalsIgnoreCase(riskLevel) || score >= 0.50) {
                MLAlert alert = new MLAlert();
                alert.setUserId(userId != null ? userId : "system_user");
                alert.setRecordId(recordId != null ? recordId : "system_record");

                if (userId != null && !userId.isBlank() && userRepository != null) {
                    userRepository.findById(userId).ifPresent(u -> alert.setDoctorName(u.getName()));
                }
                if (recordId != null && !recordId.isBlank() && recordRepository != null) {
                    recordRepository.findById(recordId).ifPresent(r -> alert.setRecordTitle(r.getTitle()));
                }

                if (req != null && req.getIpAddress() != null && !req.getIpAddress().isBlank()) {
                    alert.setIpAddress(req.getIpAddress());
                } else {
                    alert.setIpAddress("Not available");
                }

                alert.setRiskScore(score);
                alert.setRiskLevel(riskLevel);
                alert.setStatus("OPEN");

                String rationale = "Sentinel AI Anomaly Alert: " + riskLevel + " risk score " + String.format("%.3f", score) + " detected.";
                if (req != null && req.getSpecialty_match() == 0.0) {
                    rationale = "Anomalous access: Specialty mismatch between requester role and record access policy (Risk Score: " + String.format("%.3f", score) + ")";
                } else if (req != null && req.getHospital_match() == 0.0) {
                    rationale = "Anomalous access: Cross-hospital access attempt from unauthorized organization (Risk Score: " + String.format("%.3f", score) + ")";
                } else if (req != null && req.getRecords_per_hour() > 50) {
                    rationale = "Anomalous velocity burst: High volume access pattern (" + (int)req.getRecords_per_hour() + " req/hr) exceeding baseline threshold (Risk Score: " + String.format("%.3f", score) + ")";
                }
                alert.setRationale(rationale);
                alert.setTimestamp(LocalDateTime.now());
                alertRepository.save(alert);
                log.warn("CRITICAL/HIGH Anomaly Alert created [ID: {} | Status: OPEN | Risk: {} | Score: {}]",
                        alert.getId(), riskLevel, score);
            }
        } catch (Exception e) {
            log.error("Failed to persist ML prediction/alert: {}", e.getMessage());
        }
    }

    public AnomalyResponse internalEvaluation(AnomalyRequest req) {
        double score = 0.05;

        if (req.getSpecialty_match() == 0.0) score += 0.25;
        if (req.getHospital_match() == 0.0)  score += 0.25;
        if (req.getRecords_per_hour() > 100) score += 0.30;
        if (req.getUnique_patients_1h() > 20) score += 0.25;

        score = Math.min(score, 1.0);

        String level;
        String action;
        String statusStr;
        boolean isAnom;

        if (score >= 0.75) {
            level = "CRITICAL"; action = "LOCKDOWN"; statusStr = "BLOCKED"; isAnom = true;
        } else if (score >= 0.50) {
            level = "HIGH"; action = "DENY_ACCESS"; statusStr = "BLOCKED"; isAnom = true;
        } else if (score >= 0.25) {
            level = "MEDIUM"; action = "EXTRA_VERIFICATION"; statusStr = "SUSPICIOUS"; isAnom = false;
        } else {
            level = "LOW"; action = "GRANT_ACCESS"; statusStr = "SAFE"; isAnom = false;
        }

        AnomalyResponse res = new AnomalyResponse();
        res.setAnomaly_score(score);
        res.setIsolation_score(score * 0.9);
        res.setAutoencoder_score(score * 0.95);
        res.setRisk_level(level);
        res.setAction(action);
        res.setStatus(statusStr);
        res.set_anomaly(isAnom);
        res.setRiskScore((int) Math.round(score * 100));

        Map<String, Object> report = new HashMap<>();
        report.put("evalEngine", "Internal Fallback Engine");
        report.put("anomalyScore", score);
        report.put("riskLevel", level);
        res.setExplainable_report(report);

        return res;
    }

    private void enrichResponse(AnomalyResponse res) {
        if (res.getRisk_level() == null) {
            res.setRisk_level(res.getAnomaly_score() >= 0.75 ? "CRITICAL" :
                              res.getAnomaly_score() >= 0.50 ? "HIGH" :
                              res.getAnomaly_score() >= 0.25 ? "MEDIUM" : "LOW");
        }
        res.set_anomaly(res.getAnomaly_score() >= 0.50);
        res.setRiskScore((int) Math.round(res.getAnomaly_score() * 100));
        if (res.getStatus() == null) {
            res.setStatus(switch (res.getRisk_level()) {
                case "CRITICAL", "HIGH" -> "BLOCKED";
                case "MEDIUM"           -> "SUSPICIOUS";
                default                 -> "SAFE";
            });
        }
    }

    public boolean isHealthy() {
        try {
            Map<?, ?> response = restTemplate.getForObject(mlServiceUrl + "/health", Map.class);
            if (response == null) return false;
            String status = String.valueOf(response.get("status"));
            return "UP".equalsIgnoreCase(status) || "healthy".equalsIgnoreCase(status);
        } catch (Exception e) {
            return false;
        }
    }

    public long getResponseTimeMs() {
        try {
            long start = System.currentTimeMillis();
            restTemplate.getForObject(mlServiceUrl + "/health", Map.class);
            return System.currentTimeMillis() - start;
        } catch (Exception e) {
            return -1;
        }
    }
}
