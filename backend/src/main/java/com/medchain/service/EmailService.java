package com.medchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchain.exception.MedChainException;
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

    @Value("${spring.mail.host:${SMTP_HOST:${MAIL_HOST:smtp.gmail.com}}}")
    private String mailHost;

    @Value("${spring.mail.port:${SMTP_PORT:${MAIL_PORT:587}}}")
    private int mailPort;

    @Value("${spring.mail.username:${SMTP_USERNAME:${MAIL_USERNAME:}}}")
    private String mailUsername;

    @Value("${spring.mail.password:${SMTP_PASSWORD:${MAIL_PASSWORD:}}}")
    private String mailPassword;

    @Value("${mail.from:${SPRING_MAIL_FROM:${SMTP_FROM:${MAIL_FROM:}}}}")
    private String configuredFrom;

    @Value("${resend.api.key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    @Value("${resend.from:${RESEND_FROM:MedChain <onboarding@resend.dev>}}")
    private String resendFrom;

    public void sendOtpEmail(String toEmail, String otp, String type) {
        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            log.error("Invalid recipient email address for OTP dispatch");
            throw new MedChainException("Invalid email address provided for verification.");
        }

        String maskedEmail = maskEmail(toEmail);
        log.info("Processing OTP email dispatch: type=[{}], recipient=[{}]", type, maskedEmail);

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

        // 1. If Resend HTTP API key is configured, try HTTPS dispatch (works across all cloud providers)
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            try {
                log.info("Attempting email dispatch to [{}] via Resend HTTP API (HTTPS port 443)...", maskedEmail);
                boolean sent = sendViaResend(toEmail, subject, text);
                if (sent) {
                    log.info("Email successfully dispatched to [{}] via Resend HTTP API", maskedEmail);
                    return;
                }
            } catch (Exception e) {
                log.warn("Resend API delivery failed for [{}]: {}. Falling back to direct SMTP...", maskedEmail, e.getMessage());
            }
        }

        // 2. Validate SMTP credentials
        if (mailUsername == null || mailUsername.isBlank() || mailPassword == null || mailPassword.isBlank()) {
            log.error("SMTP delivery failed: Missing SMTP credentials. SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD must be configured.");
            throw new MedChainException("Email delivery failed: SMTP credentials (SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD) are not configured on the server.");
        }

        // 3. Dispatch via SMTP
        try {
            log.info("Attempting SMTP connection to [{}:{}] for recipient=[{}]...", mailHost, mailPort, maskedEmail);

            SimpleMailMessage message = new SimpleMailMessage();
            String fromAddress = (configuredFrom != null && !configuredFrom.isBlank()) ? configuredFrom : mailUsername;
            message.setFrom(fromAddress);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email successfully dispatched to [{}] via SMTP host=[{}:{}]", maskedEmail, mailHost, mailPort);
        } catch (Exception e) {
            log.error("SMTP delivery to [{}] failed on host=[{}:{}]: {}", maskedEmail, mailHost, mailPort, e.getMessage());
            String cleanReason = simplifyErrorMessage(e.getMessage());
            throw new MedChainException("Failed to send verification email: " + cleanReason + ". Please check server email settings or try again.");
        }
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
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey.trim())
                .header("Content-Type", "application/json")
                .timeout(Duration.ofSeconds(15))
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 200 && response.statusCode() < 300) {
            return true;
        } else {
            log.warn("Resend API returned HTTP {}: {}", response.statusCode(), response.body());
            return false;
        }
    }

    private String simplifyErrorMessage(String raw) {
        if (raw == null) return "Connection error";
        if (raw.contains("SocketTimeoutException") || raw.contains("Connect timed out") || raw.contains("timeout")) {
            return "Connection timed out connecting to mail server (network/firewall restriction)";
        }
        if (raw.contains("AuthenticationFailedException") || raw.contains("535-5.7.8") || raw.contains("Username and Password not accepted")) {
            return "SMTP authentication failed (verify email and app password)";
        }
        if (raw.contains("MailConnectException") || raw.contains("Couldn't connect")) {
            return "Unable to connect to mail server (network/firewall restriction)";
        }
        return "Mail server communication error";
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "unknown";
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return "***" + email.substring(Math.max(0, atIdx));
        return email.substring(0, 2) + "***" + email.substring(atIdx);
    }
}
