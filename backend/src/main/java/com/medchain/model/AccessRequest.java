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
@Table(name = "access_requests", indexes = {
        @Index(name = "idx_req_patient_id", columnList = "patientId"),
        @Index(name = "idx_req_doctor_id", columnList = "doctorId"),
        @Index(name = "idx_req_record_id", columnList = "recordId"),
        @Index(name = "idx_req_status", columnList = "status")
})
public class AccessRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String recordId;

    @Column(nullable = false)
    private String doctorId;

    @Column(nullable = false)
    private String patientId;

    @Column(nullable = false)
    private String status; // PENDING, APPROVED, REJECTED, REVOKED, EXPIRED

    @Lob
    @Column(columnDefinition = "TEXT")
    private String reason;

    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime expiresAt;
    private LocalDateTime updatedAt = LocalDateTime.now();

    @PreUpdate
    public void preUpdate() {
        this.updatedAt = LocalDateTime.now();
    }
}
