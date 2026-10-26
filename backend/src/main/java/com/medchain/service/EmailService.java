package com.medchain.service;

import com.medchain.exception.MedChainException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:${SMTP_USERNAME:${MAIL_USERNAME:}}}")
    private String mailUsername;

    @Value("${spring.mail.password:${SMTP_PASSWORD:${MAIL_PASSWORD:}}}")
    private String mailPassword;

    @Value("${mail.from:${SPRING_MAIL_FROM:${SMTP_FROM:${MAIL_FROM:}}}}")
    private String configuredFrom;

    public void sendOtpEmail(String toEmail, String otp, String type) {
        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            log.error("SMTP delivery failed: Missing SMTP credentials. Ensure SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD are configured in the environment.");
            throw new MedChainException("Email delivery failed: SMTP credentials (SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD) are not configured on the server.");
        }

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
            String fromAddress = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : mailUsername;
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Successfully dispatched OTP email to {} via SMTP", toEmail);
        } catch (Exception e) {
            log.error("Failed to send OTP email to {} via SMTP: {}", toEmail, e.getMessage(), e);
            throw new MedChainException("Failed to send verification email via SMTP: " + e.getMessage() + ". Please verify SMTP server settings.");
        }
    }
}
