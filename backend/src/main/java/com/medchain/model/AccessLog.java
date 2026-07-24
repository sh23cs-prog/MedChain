package com.medchain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "access_logs")
public class AccessLog {

    @Id
    private String id;

    private String requesterPID;
    private String patientPID;

    private String userId;
    private String userName;       // human-readable name — for admin audit readability
    private String doctorId;
    private String recordId;
    private String action;          // VIEW, UPLOAD, GRANT, REVOKE

    private double anomalyScore;
    private double isolationScore;
    private double autoencoderScore;
    private String riskLevel;       // LOW, MEDIUM, HIGH, CRITICAL
    private boolean flagged;

    private boolean granted;
    private String denyReason;

    private String ipAddress;
    private String blockchainTxHash;
    private LocalDateTime timestamp = LocalDateTime.now();
}
