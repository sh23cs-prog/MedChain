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
@Table(name = "ml_alerts", indexes = {
        @Index(name = "idx_mla_status", columnList = "status"),
        @Index(name = "idx_mla_user_id", columnList = "userId")
})
public class MLAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String userId;

    private String recordId;
    private String recordTitle;
    private String doctorName;
    private String ipAddress;

    private double riskScore;
    private String riskLevel; // HIGH, CRITICAL
    private String status = "OPEN"; // OPEN, ACKNOWLEDGED, RESOLVED

    @Lob
    @Column(columnDefinition = "TEXT")
    private String rationale;

    private String blockchainTxHash;
    private LocalDateTime timestamp = LocalDateTime.now();
    private LocalDateTime resolvedAt;
}
