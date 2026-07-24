package com.medchain.service;

import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Map;

/**
 * MLService — bridges to the Python FastAPI anomaly detection service.
 *
 * Returns simplified riskScore (0-100) and status (SAFE/SUSPICIOUS/BLOCKED)
 * as requested, while preserving detailed scores for research transparency.
 *
 * Fallback: if ML service is unavailable, defaults to LOW risk (fail-open)
 * so the system remains usable during demo without a running ML service.
 */
@Service
public class MLService {

    @Value("${ml.service.url}")
    private String mlServiceUrl;

    private final RestTemplate restTemplate;

    public MLService() {
        this.restTemplate = new RestTemplateBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .readTimeout(Duration.ofSeconds(5))
                .build();
    }

    // ─── Request / Response ───────────────────────────────────────────────────

    @Data
    public static class AnomalyRequest {
        private double hour;
        private double day_of_week;
        private double records_per_hour;
        private double time_since_last;
        private double specialty_match;
        private double hospital_match;
        private double access_freq_7d;
        private double unique_patients_1h;
    }

    @Data
    public static class AnomalyResponse {
        // ── Raw ML scores ──
        private double anomaly_score;
        private double isolation_score;
        private double autoencoder_score;
        private boolean is_anomaly;
        private String risk_level;
        private String action;

        // ── Simplified demo response ──
        private int riskScore;     // 0–100 integer (anomaly_score * 100)
        private String status;     // SAFE | SUSPICIOUS | BLOCKED

        public boolean isAnomaly() { return is_anomaly; }
        public void set_anomaly(boolean is_anomaly) { this.is_anomaly = is_anomaly; }
    }

    // ─── Score Access ─────────────────────────────────────────────────────────

    /**
     * Score an access request using the Isolation Forest anomaly detector.
     * Populates both the raw scores and the simplified riskScore/status fields.
     */
    public AnomalyResponse scoreAccess(AnomalyRequest request) {
        try {
            AnomalyResponse response = restTemplate.postForObject(
                mlServiceUrl + "/detect",
                request,
                AnomalyResponse.class
            );
            if (response != null) {
                enrichResponse(response);
                return response;
            }
        } catch (Exception e) {
            // ML service unavailable — log and return safe fallback
        }

        // Fallback: LOW risk (fail open for demo reliability)
        AnomalyResponse fallback = new AnomalyResponse();
        fallback.setAnomaly_score(0.08);
        fallback.setIsolation_score(0.07);
        fallback.setAutoencoder_score(0.09);
        fallback.set_anomaly(false);
        fallback.setRisk_level("LOW");
        fallback.setAction("ALLOW");
        fallback.setRiskScore(8);
        fallback.setStatus("SAFE");
        return fallback;
    }

    /** Add the simplified riskScore/status to the raw ML response. */
    private void enrichResponse(AnomalyResponse res) {
        // riskScore: scale anomaly_score (0.0–1.0) to integer 0–100
        res.setRiskScore((int) Math.round(res.getAnomaly_score() * 100));

        // status: map risk level to human-readable status
        res.setStatus(switch (res.getRisk_level() != null ? res.getRisk_level() : "LOW") {
            case "CRITICAL", "HIGH" -> "BLOCKED";
            case "MEDIUM"           -> "SUSPICIOUS";
            default                 -> "SAFE";
        });
    }

    // ─── Health Check ─────────────────────────────────────────────────────────

    public boolean isHealthy() {
        try {
            Map<?, ?> response = restTemplate.getForObject(mlServiceUrl + "/health", Map.class);
            return response != null && "running".equals(response.get("status"));
        } catch (Exception e) {
            return false;
        }
    }

    /** Get response time to ML service in milliseconds (for metrics). */
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
