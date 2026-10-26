package com.medchain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchain.exception.MedChainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class EmailService {

    public enum Provider {
        BREVO,
        RESEND,
        SMTP
    }

    private final JavaMailSender mailSender;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    @Value("${mail.provider:${MAIL_PROVIDER:${EMAIL_PROVIDER:AUTO}}}")
    private String mailProvider;

    @Value("${spring.mail.host:${SMTP_HOST:${MAIL_HOST:smtp.gmail.com}}}")
    private String mailHost;

    @Value("${spring.mail.port:${SMTP_PORT:${MAIL_PORT:587}}}")
    private int mailPort;

    @Value("${spring.mail.username:${SMTP_USERNAME:${MAIL_USERNAME:}}}")
    private String mailUsername;

    @Value("${spring.mail.password:${SMTP_PASSWORD:${MAIL_PASSWORD:}}}")
    private String mailPassword;

    @Value("${spring.mail.from:${mail.from:${SPRING_MAIL_FROM:${SMTP_FROM:}}}}")
    private String configuredFrom;

    @Value("${brevo.api.key:${BREVO_API_KEY:}}")
    private String brevoApiKey;

    @Value("${brevo.from.email:${BREVO_FROM_EMAIL:}}")
    private String brevoFromEmail;

    @Value("${brevo.from.name:${BREVO_FROM_NAME:MedChain Security}}")
    private String brevoFromName;

    @Value("${resend.api.key:${RESEND_API_KEY:}}")
    private String resendApiKey;

    @Value("${resend.from:${RESEND_FROM:}}")
    private String resendFrom;

    @Autowired
    public EmailService(JavaMailSender mailSender, @Autowired(required = false) HttpClient httpClient) {
        this.mailSender = mailSender;
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = new ObjectMapper();
    }

    public EmailService(JavaMailSender mailSender, HttpClient httpClient, ObjectMapper objectMapper) {
        this.mailSender = mailSender;
        this.httpClient = (httpClient != null) ? httpClient : HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.objectMapper = (objectMapper != null) ? objectMapper : new ObjectMapper();
    }

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

        String html = String.format(
                "<div style=\"font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 20px; border: 1px solid #e0e0e0; border-radius: 8px;\">" +
                "<h2 style=\"color: #1976d2;\">MedChain Security Verification</h2>" +
                "<p>Hello,</p>" +
                "<p>Your 6-digit verification code for <strong>%s</strong> is:</p>" +
                "<div style=\"background-color: #f5f5f5; padding: 16px; text-align: center; border-radius: 6px; font-size: 28px; font-weight: bold; letter-spacing: 6px; color: #333; margin: 20px 0;\">%s</div>" +
                "<p>This code is valid for <strong>5 minutes</strong> and can only be used once.</p>" +
                "<p style=\"color: #d32f2f;\">Do not share this code with anyone.</p>" +
                "<hr style=\"border: none; border-top: 1px solid #eee; margin: 20px 0;\" />" +
                "<p style=\"font-size: 12px; color: #777;\">Regards,<br/>MedChain Security Team</p>" +
                "</div>",
                type, otp
        );

        Provider provider = determineProvider();

        if (provider == Provider.BREVO) {
            String activeBrevoKey = getEffectiveBrevoApiKey();
            if (activeBrevoKey == null || activeBrevoKey.isBlank()) {
                log.error("Brevo is selected as email provider, but BREVO_API_KEY is not configured.");
                throw new MedChainException("Brevo is selected as email provider, but BREVO_API_KEY is not configured.");
            }
            sendViaBrevo(toEmail, subject, text, html, activeBrevoKey, getEffectiveBrevoFromEmail(), getEffectiveBrevoFromName());
        } else if (provider == Provider.RESEND) {
            String activeResendKey = getEffectiveResendApiKey();
            if (activeResendKey == null || activeResendKey.isBlank()) {
                log.error("Resend is selected as email provider, but RESEND_API_KEY is not configured.");
                throw new MedChainException("Resend is selected as email provider, but RESEND_API_KEY is not configured.");
            }
            sendViaResend(toEmail, subject, text, html, activeResendKey, getEffectiveSender());
        } else if (provider == Provider.SMTP) {
            if (!isSmtpConfigured()) {
                log.error("SMTP is selected as email provider, but SMTP credentials are not configured.");
                throw new MedChainException("SMTP is selected as email provider, but SMTP credentials (SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD) are not configured.");
            }
            sendViaSmtp(toEmail, subject, text);
        } else {
            log.error("Email delivery failed: No usable email provider configured. Please configure BREVO_API_KEY, RESEND_API_KEY, or SMTP credentials.");
            throw new MedChainException("Email delivery is not configured on the server. Please configure BREVO_API_KEY, RESEND_API_KEY, or SMTP credentials (SPRING_MAIL_USERNAME and SPRING_MAIL_PASSWORD).");
        }
    }

    public Provider determineProvider() {
        String configured = (mailProvider != null && !mailProvider.isBlank()) ? mailProvider.trim().toUpperCase() : "AUTO";
        String envProvider = System.getenv("MAIL_PROVIDER");
        if (envProvider != null && !envProvider.isBlank()) {
            configured = envProvider.trim().toUpperCase();
        }

        if ("BREVO".equals(configured)) {
            return Provider.BREVO;
        }
        if ("RESEND".equals(configured)) {
            return Provider.RESEND;
        }
        if ("SMTP".equals(configured)) {
            return Provider.SMTP;
        }

        // AUTO mode precedence:
        // 1. If Brevo API key is configured, select Brevo
        if (getEffectiveBrevoApiKey() != null) {
            return Provider.BREVO;
        }
        // 2. If Resend API key is configured, select Resend
        if (getEffectiveResendApiKey() != null) {
            return Provider.RESEND;
        }
        // 3. Fallback to SMTP if configured
        if (isSmtpConfigured()) {
            return Provider.SMTP;
        }

        return null;
    }

    private void sendViaBrevo(String toEmail, String subject, String text, String html, String apiKey, String fromEmail, String fromName) {
        String maskedRecipient = maskEmail(toEmail);
        String maskedSender = maskEmail(fromEmail);
        log.info("Attempting email dispatch to [{}] via Brevo HTTPS API (sender=[{}], POST https://api.brevo.com/v3/smtp/email)...",
                maskedRecipient, maskedSender);

        if (apiKey == null || apiKey.isBlank()) {
            log.error("Brevo delivery failed: Missing BREVO_API_KEY");
            throw new MedChainException("Brevo API key is missing. Please configure BREVO_API_KEY.");
        }

        if (fromEmail == null || fromEmail.isBlank() || !fromEmail.contains("@")) {
            log.error("Brevo delivery failed: Invalid or missing BREVO_FROM_EMAIL");
            throw new MedChainException("Brevo sender email is invalid or missing. Please configure BREVO_FROM_EMAIL.");
        }

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("sender", Map.of(
                    "name", (fromName != null && !fromName.isBlank()) ? fromName : "MedChain Security",
                    "email", fromEmail
            ));
            payload.put("to", List.of(Map.of("email", toEmail)));
            payload.put("subject", subject);
            if (html != null && !html.isBlank()) {
                payload.put("htmlContent", html);
            }
            if (text != null && !text.isBlank()) {
                payload.put("textContent", text);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Email successfully accepted by Brevo API for [{}] (HTTP {})", maskedRecipient, statusCode);
                return;
            }

            // Brevo returned non-2xx
            String errorMessage = extractBrevoErrorMessage(responseBody, statusCode);
            log.warn("Brevo API rejected email dispatch to [{}]: HTTP {} - {}", maskedRecipient, statusCode, errorMessage);
            throw new MedChainException("Failed to send verification email via Brevo: " + errorMessage);

        } catch (MedChainException e) {
            throw e;
        } catch (HttpConnectTimeoutException | ConnectException | SocketTimeoutException e) {
            log.error("Network timeout connecting to Brevo API for recipient=[{}]: {}", maskedRecipient, e.getMessage());
            throw new MedChainException("Failed to send verification email: Connection timed out reaching Brevo email service.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email dispatch thread interrupted for [{}]: {}", maskedRecipient, e.getMessage());
            throw new MedChainException("Failed to send verification email: Email dispatch was interrupted.");
        } catch (Exception e) {
            log.error("Unexpected error sending email via Brevo to [{}]: {}", maskedRecipient, e.getMessage());
            throw new MedChainException("Failed to send verification email via Brevo: " + e.getMessage());
        }
    }

    private void sendViaResend(String toEmail, String subject, String text, String html, String apiKey, String fromAddress) {
        String maskedEmail = maskEmail(toEmail);
        log.info("Attempting email dispatch to [{}] via Resend HTTPS API (POST https://api.resend.com/emails)...", maskedEmail);

        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("from", fromAddress);
            payload.put("to", List.of(toEmail));
            payload.put("subject", subject);
            if (html != null && !html.isBlank()) {
                payload.put("html", html);
            }
            if (text != null && !text.isBlank()) {
                payload.put("text", text);
            }

            String jsonPayload = objectMapper.writeValueAsString(payload);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(15))
                    .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int statusCode = response.statusCode();
            String responseBody = response.body();

            if (statusCode >= 200 && statusCode < 300) {
                log.info("Email successfully accepted by Resend API for [{}] (HTTP {})", maskedEmail, statusCode);
                return;
            }

            // Resend returned non-2xx
            String errorMessage = extractResendErrorMessage(responseBody, statusCode);
            log.warn("Resend API rejected email dispatch to [{}]: HTTP {} - {}", maskedEmail, statusCode, errorMessage);
            throw new MedChainException("Failed to send verification email via Resend: " + errorMessage);

        } catch (MedChainException e) {
            throw e;
        } catch (HttpConnectTimeoutException | ConnectException | SocketTimeoutException e) {
            log.error("Network timeout connecting to Resend API for recipient=[{}]: {}", maskedEmail, e.getMessage());
            throw new MedChainException("Failed to send verification email: Connection timed out reaching Resend email service.");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Email dispatch thread interrupted for [{}]: {}", maskedEmail, e.getMessage());
            throw new MedChainException("Failed to send verification email: Email dispatch was interrupted.");
        } catch (Exception e) {
            log.error("Unexpected error sending email via Resend to [{}]: {}", maskedEmail, e.getMessage());
            throw new MedChainException("Failed to send verification email via Resend: " + e.getMessage());
        }
    }

    private void sendViaSmtp(String toEmail, String subject, String text) {
        String maskedEmail = maskEmail(toEmail);
        try {
            log.info("Attempting SMTP connection to [{}:{}] for recipient=[{}]...", mailHost, mailPort, maskedEmail);

            SimpleMailMessage message = new SimpleMailMessage();
            String from = getEffectiveSender();
            message.setFrom(from);
            message.setTo(toEmail);
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email successfully dispatched to [{}] via SMTP host=[{}:{}]", maskedEmail, mailHost, mailPort);
        } catch (Exception e) {
            log.error("SMTP delivery to [{}] failed on host=[{}:{}]: {}", maskedEmail, mailHost, mailPort, e.getMessage());
            String cleanReason = simplifyErrorMessage(e.getMessage());
            throw new MedChainException("Failed to send verification email via SMTP: " + cleanReason + ". Please check server email settings or try again.");
        }
    }

    public String getEffectiveBrevoApiKey() {
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            return brevoApiKey.trim();
        }
        String envKey = System.getenv("BREVO_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return null;
    }

    public String getEffectiveBrevoFromEmail() {
        if (brevoFromEmail != null && !brevoFromEmail.isBlank()) {
            return extractRawEmail(brevoFromEmail.trim());
        }
        String envEmail = System.getenv("BREVO_FROM_EMAIL");
        if (envEmail != null && !envEmail.isBlank()) {
            return extractRawEmail(envEmail.trim());
        }
        String springMailFrom = System.getenv("SPRING_MAIL_FROM");
        if (springMailFrom != null && !springMailFrom.isBlank()) {
            return extractRawEmail(springMailFrom.trim());
        }
        if (configuredFrom != null && !configuredFrom.isBlank()) {
            return extractRawEmail(configuredFrom.trim());
        }
        return null;
    }

    public String getEffectiveBrevoFromName() {
        if (brevoFromName != null && !brevoFromName.isBlank()) {
            return brevoFromName.trim();
        }
        String envName = System.getenv("BREVO_FROM_NAME");
        if (envName != null && !envName.isBlank()) {
            return envName.trim();
        }
        return "MedChain Security";
    }

    public String getEffectiveResendApiKey() {
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            return resendApiKey.trim();
        }
        String envKey = System.getenv("RESEND_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            return envKey.trim();
        }
        return null;
    }

    public String getEffectiveSender() {
        String from = null;
        if (configuredFrom != null && !configuredFrom.isBlank()) {
            from = configuredFrom.trim();
        } else {
            String envFrom = System.getenv("SPRING_MAIL_FROM");
            if (envFrom != null && !envFrom.isBlank()) {
                from = envFrom.trim();
            } else if (resendFrom != null && !resendFrom.isBlank()) {
                from = resendFrom.trim();
            } else {
                String fallbackEnv = System.getenv("RESEND_FROM");
                if (fallbackEnv != null && !fallbackEnv.isBlank()) {
                    from = fallbackEnv.trim();
                }
            }
        }

        if (from == null || from.isBlank()) {
            return "MedChain <onboarding@resend.dev>";
        }

        // If email is plain like "onboarding@resend.dev", wrap with "MedChain <...>"
        if (!from.contains("<") && !from.contains(">") && from.contains("@")) {
            return "MedChain <" + from + ">";
        }
        return from;
    }

    public boolean isSmtpConfigured() {
        String user = (mailUsername != null && !mailUsername.isBlank()) ? mailUsername : System.getenv("SPRING_MAIL_USERNAME");
        String pass = (mailPassword != null && !mailPassword.isBlank()) ? mailPassword : System.getenv("SPRING_MAIL_PASSWORD");
        return user != null && !user.isBlank() && pass != null && !pass.isBlank();
    }

    private String extractRawEmail(String address) {
        if (address == null || address.isBlank()) return null;
        if (address.contains("<") && address.contains(">")) {
            int start = address.indexOf('<') + 1;
            int end = address.indexOf('>');
            if (end > start) {
                return address.substring(start, end).trim();
            }
        }
        return address.trim();
    }

    private String extractBrevoErrorMessage(String body, int statusCode) {
        if (statusCode == 401) {
            return "Unauthorized (invalid or inactive BREVO_API_KEY)";
        }
        if (statusCode == 429) {
            return "Rate limit exceeded on Brevo API. Please try again later.";
        }
        if (body == null || body.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && !node.get("message").asText().isBlank()) {
                return node.get("message").asText();
            }
            if (node.has("code") && !node.get("code").asText().isBlank()) {
                return node.get("code").asText();
            }
        } catch (Exception ignored) {
        }
        String clean = body.replaceAll("[\r\n\t]+", " ").trim();
        if (clean.length() > 200) {
            clean = clean.substring(0, 200) + "...";
        }
        return clean;
    }

    private String extractResendErrorMessage(String body, int statusCode) {
        if (body == null || body.isBlank()) {
            return "HTTP " + statusCode;
        }
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message") && !node.get("message").asText().isBlank()) {
                return node.get("message").asText();
            }
            if (node.has("error") && !node.get("error").asText().isBlank()) {
                return node.get("error").asText();
            }
        } catch (Exception ignored) {
        }
        String clean = body.replaceAll("[\r\n\t]+", " ").trim();
        if (clean.length() > 200) {
            clean = clean.substring(0, 200) + "...";
        }
        return clean;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) return "***" + (atIndex >= 0 ? email.substring(atIndex) : "");
        String prefix = email.substring(0, Math.min(2, atIndex));
        return prefix + "***" + email.substring(atIndex);
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
        return raw.replaceAll("[\r\n]+", " ").trim();
    }

    // Setters for test harness
    void setMailProvider(String mailProvider) {
        this.mailProvider = mailProvider;
    }

    void setBrevoApiKey(String brevoApiKey) {
        this.brevoApiKey = brevoApiKey;
    }

    void setBrevoFromEmail(String brevoFromEmail) {
        this.brevoFromEmail = brevoFromEmail;
    }

    void setBrevoFromName(String brevoFromName) {
        this.brevoFromName = brevoFromName;
    }

    void setResendApiKey(String resendApiKey) {
        this.resendApiKey = resendApiKey;
    }

    void setConfiguredFrom(String configuredFrom) {
        this.configuredFrom = configuredFrom;
    }

    void setMailUsername(String mailUsername) {
        this.mailUsername = mailUsername;
    }

    void setMailPassword(String mailPassword) {
        this.mailPassword = mailPassword;
    }

    void setMailHost(String mailHost) {
        this.mailHost = mailHost;
    }

    void setMailPort(int mailPort) {
        this.mailPort = mailPort;
    }

    void setResendFrom(String resendFrom) {
        this.resendFrom = resendFrom;
    }
}
