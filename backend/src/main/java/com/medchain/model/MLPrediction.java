package com.medchain.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ml_predictions", indexes = {
        @Index(name = "idx_mlp_user_id", columnList = "userId"),
        @Index(name = "idx_mlp_risk_level", columnList = "riskLevel")
})
public class MLPrediction {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String userId;
    private String recordId;

    private double riskScore; // 0.0 to 1.0 decimal
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private String anomalyStatus; // SAFE, ELEVATED, LOCKDOWN

    private String modelVersion = "v1.1.0";
    private boolean anomaly;

    @Column(columnDefinition = "TEXT")
    private String explainableReportJson;

    private LocalDateTime timestamp = LocalDateTime.now();
}
