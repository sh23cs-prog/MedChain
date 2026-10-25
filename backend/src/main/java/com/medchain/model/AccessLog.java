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
@Table(name = "access_logs", indexes = {
        @Index(name = "idx_log_user_id", columnList = "userId"),
        @Index(name = "idx_log_doctor_id", columnList = "doctorId"),
        @Index(name = "idx_log_record_id", columnList = "recordId"),
        @Index(name = "idx_log_action", columnList = "action"),
        @Index(name = "idx_log_timestamp", columnList = "timestamp")
})
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    private String requesterPID;
    private String patientPID;

    private String userId;
    private String userName;
    private String role;
    private String doctorId;
    private String recordId;

    @Column(nullable = false)
    private String action; // LOGIN, LOGIN_FAILED, LOGOUT, REGISTER, OTP_REQUEST, OTP_VERIFY, PASSWORD_RESET, UPLOAD, VIEW, DOWNLOAD, DELETE, ACCESS_REQUEST, ACCESS_APPROVE, ACCESS_REJECT, ACCESS_REVOKE, INTEGRITY_FAILURE, SECURITY_ALERT

    private double anomalyScore;
    private double isolationScore;
    private double autoencoderScore;
    private String riskLevel; // LOW, MEDIUM, HIGH, CRITICAL
    private boolean flagged;

    private boolean granted;

    @Lob
    @Column(columnDefinition = "TEXT")
    private String denyReason;

    private String ipAddress;
    private String userAgent;
    private String blockchainTxHash;
    private String hospitalOrgId = "Hospital A";

    private LocalDateTime timestamp = LocalDateTime.now();
}
