package com.medchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${spring.mail.username:${SMTP_USERNAME:${MAIL_USERNAME:}}}")
    private String mailUsername;

    @Value("${spring.mail.password:${SMTP_PASSWORD:${MAIL_PASSWORD:}}}")
    private String mailPassword;

    @Value("${mail.from:${SPRING_MAIL_FROM:${SMTP_FROM:${MAIL_FROM:}}}}")
    private String configuredFrom;

    @Value("${spring.mail.enabled:true}")
    private boolean mailEnabled;

    @Value("${resend.api.key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    @Value("${resend.from:${RESEND_FROM:MedChain <onboarding@resend.dev>}}")
    private String resendFrom;

    public boolean sendOtpEmail(String toEmail, String otp, String type) {
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

        // 1. Try Resend HTTP API (HTTPS port 443 — works seamlessly on Render free tier)
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            try {
                boolean sent = sendViaResend(toEmail, subject, text);
                if (sent) {
                    log.info("Successfully dispatched OTP email to {} via Resend HTTP API", toEmail);
                    return true;
                }
            } catch (Exception e) {
                log.warn("Resend API delivery failed: {}. Continuing with fallback options.", e.getMessage());
            }
        }

        // 2. Try SMTP if explicitly enabled and configured
        if (mailEnabled && mailUsername != null && !mailUsername.isBlank() && mailPassword != null && !mailPassword.isBlank()) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                String fromAddress = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : mailUsername;
                message.setFrom(fromAddress);
                message.setTo(toEmail);
                message.setSubject(subject);
                message.setText(text);

                mailSender.send(message);
                log.info("Successfully dispatched OTP email to {} via SMTP", toEmail);
                return true;
            } catch (Exception e) {
                log.warn("SMTP email dispatch to {} failed: {}. Free-tier cloud providers block outbound SMTP ports (25/465/587). OTP is available via secure in-app fallback.", toEmail, e.getMessage());
            }
        } else {
            log.info("Direct SMTP delivery skipped (credentials missing or mail disabled). Using OTP in-app delivery for {}.", toEmail);
        }

        return false;
    }

    private boolean sendViaResend(String toEmail, String subject, String text) throws Exception {
        Map<String, Object> payload = Map.of(
                "from", resendFrom,
                "to", List.of(toEmail),
                "subject", subject,
                "text", text
        );

        String json = objectMapper.writeValueAsString(payload);
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(10))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return true;
        } else {
            log.warn("Resend API returned status {}: {}", response.statusCode(), response.body());
            return false;
        }
    }
}
