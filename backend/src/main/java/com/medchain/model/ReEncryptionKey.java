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
@Table(name = "reencryption_keys", indexes = {
        @Index(name = "idx_rekey_record_doctor", columnList = "recordId, doctorId")
})
public class ReEncryptionKey {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String recordId;

    @Column(nullable = false)
    private String doctorId;

    private String doctorPID;

    @Column(columnDefinition = "TEXT")
    private String tempKey;

    @Column(columnDefinition = "TEXT")
    private String encryptedAesKey;

    private LocalDateTime expiry;
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean active = true;
}
