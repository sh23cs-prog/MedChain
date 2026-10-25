package com.medchain.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    public void sendOtpEmail(String toEmail, String otp, String type) {
        String subject = "PASSWORD_RESET".equals(type)
                ? "MedChain Password Reset Verification Code"
                : "MedChain Account Verification Code";

        String text = String.format(
                "Hello,\n\nYour 6-digit MedChain verification code for %s is:\n\n   %s\n\n" +
                "This code is valid for 5 minutes and can only be used once.\n" +
                "Do not share this code with anyone.\n\n" +
                "Regards,\nMedChain Security Team",
                type, otp
        );

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom("noreply@medchain.com");
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Successfully dispatched OTP email to {} via SMTP", toEmail);
        } catch (Exception e) {
            log.warn("SMTP server unreachable at port 1025 ({}). Logged OTP code for local development: [{}]", e.getMessage(), otp);
            // Allow registration to proceed smoothly even if local SMTP daemon is offline
        }
    }
}
