package com.medchain.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medchain.exception.MedChainException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EmailServiceTest {

    @Mock
    private JavaMailSender mailSender;

    @Mock
    private HttpClient httpClient;

    @Mock
    private HttpResponse<String> httpResponse;

    private EmailService emailService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        emailService = new EmailService(mailSender, httpClient, objectMapper);
    }

    @Test
    @DisplayName("Resend Selection: Successfully dispatches via Resend HTTPS API without SMTP credentials")
    void testResendSelectedAndSucceeds() throws Exception {
        emailService.setResendApiKey("re_test_dummy_key_12345");
        emailService.setConfiguredFrom("onboarding@resend.dev");
        emailService.setMailUsername(null);
        emailService.setMailPassword(null);

        when(httpResponse.statusCode()).thenReturn(200);
        when(httpResponse.body()).thenReturn("{\"id\":\"msg_12345abc\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("patient@example.com", "654321", "REGISTRATION")
        );

        // Verify HTTP request details
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).send(requestCaptor.capture(), any());
        HttpRequest sentRequest = requestCaptor.getValue();

        assertEquals(URI.create("https://api.resend.com/emails"), sentRequest.uri());
        assertEquals("POST", sentRequest.method());
        assertEquals("Bearer re_test_dummy_key_12345", sentRequest.headers().firstValue("Authorization").orElse(null));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(null));

        // SMTP must NEVER be called
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Resend API Failure: Non-2xx response throws clear Resend error and does NOT require SMTP credentials")
    void testResendFailureDoesNotRequireSmtp() throws Exception {
        emailService.setResendApiKey("re_test_dummy_key_12345");
        emailService.setConfiguredFrom("onboarding@resend.dev");
        emailService.setMailUsername(null);
        emailService.setMailPassword(null);

        when(httpResponse.statusCode()).thenReturn(403);
        when(httpResponse.body()).thenReturn(
                "{\"statusCode\":403,\"message\":\"You can only send testing emails to your own email address (owner@example.com).\",\"name\":\"validation_error\"}"
        );
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("unauthorized@medchain.com", "112233", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("Failed to send verification email via Resend"),
                "Exception must indicate Resend failure: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("You can only send testing emails to your own email address"),
                "Exception must convey Resend message: " + ex.getMessage());
        assertFalse(ex.getMessage().contains("SMTP credentials"),
                "Exception must NOT mention SMTP credentials when Resend is selected: " + ex.getMessage());

        // JavaMailSender must NEVER be invoked
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP Fallback: When RESEND_API_KEY is not configured and valid SMTP credentials exist, use SMTP")
    void testFallbackToSmtpWhenResendNotConfiguredAndSmtpCredentialsExist() {
        emailService.setResendApiKey(null);
        emailService.setMailUsername("medchain.alerts@gmail.com");
        emailService.setMailPassword("app-password-16-char");
        emailService.setConfiguredFrom("medchain.alerts@gmail.com");
        emailService.setMailHost("smtp.gmail.com");
        emailService.setMailPort(587);

        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("doctor@hospital.org", "998877", "PASSWORD_RESET")
        );

        ArgumentCaptor<SimpleMailMessage> msgCaptor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, times(1)).send(msgCaptor.capture());
        SimpleMailMessage sentMsg = msgCaptor.getValue();

        assertEquals("doctor@hospital.org", sentMsg.getTo()[0]);
        assertTrue(sentMsg.getSubject().contains("Password Reset"));
        assertTrue(sentMsg.getText().contains("998877"));

        // HTTP client must NEVER be called
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Missing Provider Configuration: When neither RESEND_API_KEY nor SMTP credentials exist, throws clear error")
    void testMissingProviderConfigurationThrowsClearError() {
        emailService.setResendApiKey(null);
        emailService.setMailUsername(null);
        emailService.setMailPassword(null);

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("anyone@medchain.com", "554433", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("Email delivery is not configured on the server"),
                "Expected clear missing provider message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("RESEND_API_KEY"),
                "Expected RESEND_API_KEY mention: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("SMTP credentials"),
                "Expected SMTP credentials mention: " + ex.getMessage());

        verifyNoInteractions(httpClient);
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Sender Address: Uses SPRING_MAIL_FROM with formatted display name")
    void testSenderAddressUsesSpringMailFrom() {
        emailService.setConfiguredFrom("onboarding@resend.dev");
        assertEquals("MedChain <onboarding@resend.dev>", emailService.getEffectiveSender());

        emailService.setConfiguredFrom("Custom Security <security@medchain.io>");
        assertEquals("Custom Security <security@medchain.io>", emailService.getEffectiveSender());
    }

    @Test
    @DisplayName("Input Validation: Invalid recipient email address is rejected immediately")
    void testInvalidRecipientEmail() {
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail(null, "123456", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail("   ", "123456", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail("no-at-sign.com", "123456", "REGISTRATION"));
    }
}
