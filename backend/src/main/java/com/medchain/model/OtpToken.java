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
@Table(name = "otp_tokens", indexes = {
        @Index(name = "idx_otp_email", columnList = "email"),
        @Index(name = "idx_otp_expiry", columnList = "expiryTime")
})
public class OtpToken {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String otpHash; // BCrypt-hashed OTP

    @Column(nullable = false)
    private String type; // REGISTRATION, PASSWORD_RESET

    @Column(nullable = false)
    private LocalDateTime expiryTime;

    private int attemptCount = 0;
    private boolean used = false;

    private LocalDateTime lastResentAt = LocalDateTime.now();
    private LocalDateTime createdAt = LocalDateTime.now();
}
