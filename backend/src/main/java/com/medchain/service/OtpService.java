package com.medchain.service;

import com.medchain.exception.MedChainException;
import com.medchain.model.OtpToken;
import com.medchain.repository.OtpTokenRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OtpService {

    private final OtpTokenRepository otpTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final SecureRandom secureRandom = new SecureRandom();

    public void generateAndSendOtp(String email, String type) {
        if (email == null || email.isBlank()) {
            throw new MedChainException("Email address cannot be empty.");
        }

        // Check for resend cooldown (60 seconds)
        Optional<OtpToken> latestTokenOpt = otpTokenRepository
                .findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(email, type);

        if (latestTokenOpt.isPresent()) {
            OtpToken latest = latestTokenOpt.get();
            if (latest.getLastResentAt() != null &&
                latest.getLastResentAt().plusSeconds(60).isAfter(LocalDateTime.now())) {
                throw new MedChainException("Resend cooldown active. Please wait 60 seconds before requesting a new OTP.");
            }
        }

        // Generate cryptographically secure random 6-digit OTP
        int otpInt = 100000 + secureRandom.nextInt(900000);
        String rawOtp = String.valueOf(otpInt);

        // Hash OTP with BCrypt before saving to DB
        String hashedOtp = passwordEncoder.encode(rawOtp);

        OtpToken token = new OtpToken();
        token.setEmail(email);
        token.setOtpHash(hashedOtp);
        token.setType(type);
        token.setExpiryTime(LocalDateTime.now().plusMinutes(5));
        token.setAttemptCount(0);
        token.setUsed(false);
        token.setLastResentAt(LocalDateTime.now());
        token.setCreatedAt(LocalDateTime.now());

        // Dispatch via email service. If email dispatch fails, this throws MedChainException
        // and aborts the flow so the client receives a proper error.
        emailService.sendOtpEmail(email, rawOtp, type);

        // Save token to DB only after successful email dispatch
        otpTokenRepository.save(token);
        log.info("Security OTP token generated and dispatched successfully for recipient [{}] (type={})", maskEmail(email), type);
    }

    public void verifyOtp(String email, String rawOtp, String type) {
        OtpToken token = otpTokenRepository
                .findTopByEmailAndTypeAndUsedFalseOrderByCreatedAtDesc(email, type)
                .orElseThrow(() -> new MedChainException("No active OTP found for this email. Please request a new OTP."));

        if (token.isUsed()) {
            throw new MedChainException("OTP has already been used. Please request a new OTP.");
        }

        if (token.getExpiryTime().isBefore(LocalDateTime.now())) {
            throw new MedChainException("OTP has expired. Please request a new OTP.");
        }

        if (token.getAttemptCount() >= 3) {
            token.setUsed(true);
            otpTokenRepository.save(token);
            throw new MedChainException("Maximum OTP verification attempts exceeded (3). Please request a new OTP.");
        }

        // Increment attempt count
        token.setAttemptCount(token.getAttemptCount() + 1);

        if (!passwordEncoder.matches(rawOtp, token.getOtpHash())) {
            otpTokenRepository.save(token);
            int remaining = 3 - token.getAttemptCount();
            throw new MedChainException("Invalid OTP code. " + (remaining > 0 ? remaining + " attempt(s) remaining." : "Maximum attempts reached."));
        }

        // Verification successful — mark single-use
        token.setUsed(true);
        otpTokenRepository.save(token);
        log.info("OTP verification successful for recipient [{}] (type={})", maskEmail(email), type);
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "unknown";
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return "***" + email.substring(Math.max(0, atIdx));
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }
}
