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

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
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
    @DisplayName("Brevo Selection: Successfully dispatches via Brevo HTTPS API with api-key header")
    void testBrevoSelectedAndSucceeds() throws Exception {
        emailService.setMailProvider("BREVO");
        emailService.setBrevoApiKey("xkeysib-test-dummy-api-key-998877");
        emailService.setBrevoFromEmail("contact@medchain.io");
        emailService.setBrevoFromName("MedChain Security");

        when(httpResponse.statusCode()).thenReturn(201);
        when(httpResponse.body()).thenReturn("{\"messageId\":\"<20241026.test@smtp-relay.brevo.com>\"}");
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        assertDoesNotThrow(() ->
                emailService.sendOtpEmail("patient@example.com", "889900", "REGISTRATION")
        );

        // Verify HTTP request details
        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(httpClient, times(1)).send(requestCaptor.capture(), any());
        HttpRequest sentRequest = requestCaptor.getValue();

        assertEquals(URI.create("https://api.brevo.com/v3/smtp/email"), sentRequest.uri());
        assertEquals("POST", sentRequest.method());
        assertEquals("xkeysib-test-dummy-api-key-998877", sentRequest.headers().firstValue("api-key").orElse(null));
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(null));
        assertEquals("application/json", sentRequest.headers().firstValue("Accept").orElse(null));

        // SMTP must NEVER be called
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Brevo API Failure: Non-2xx response throws clear Brevo error and does NOT fall through to Resend or SMTP")
    void testBrevoApiFailureDoesNotFallThrough() throws Exception {
        emailService.setMailProvider("BREVO");
        emailService.setBrevoApiKey("xkeysib-test-dummy-api-key-998877");
        emailService.setBrevoFromEmail("contact@medchain.io");
        emailService.setResendApiKey("re_existing_resend_key");
        emailService.setMailUsername("user@gmail.com");
        emailService.setMailPassword("password");

        when(httpResponse.statusCode()).thenReturn(400);
        when(httpResponse.body()).thenReturn(
                "{\"code\":\"invalid_parameter\",\"message\":\"sender email is not validated\"}"
        );
        doReturn(httpResponse).when(httpClient).send(any(HttpRequest.class), any());

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("recipient@domain.com", "123456", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("Failed to send verification email via Brevo"),
                "Exception must indicate Brevo failure: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("sender email is not validated"),
                "Exception must convey Brevo message: " + ex.getMessage());

        // JavaMailSender must NEVER be invoked
        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Brevo Sender Validation: Missing BREVO_FROM_EMAIL throws clear configuration error")
    void testBrevoMissingSenderEmailThrowsError() {
        emailService.setMailProvider("BREVO");
        emailService.setBrevoApiKey("xkeysib-test-dummy-api-key-998877");
        emailService.setBrevoFromEmail(null);
        emailService.setConfiguredFrom(null);

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("patient@example.com", "123456", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("Brevo sender email is invalid or missing"),
                "Expected sender email validation error: " + ex.getMessage());
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Brevo Explicit Selection: Missing BREVO_API_KEY throws configuration error")
    void testBrevoExplicitProviderMissingApiKeyThrowsError() {
        emailService.setMailProvider("BREVO");
        emailService.setBrevoApiKey(null);

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("patient@example.com", "123456", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("BREVO_API_KEY is not configured"),
                "Expected missing key error: " + ex.getMessage());
        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Resend Selection: Dispatches via Resend when Resend is configured and Brevo is absent")
    void testResendSelectedAndSucceeds() throws Exception {
        emailService.setMailProvider("AUTO");
        emailService.setBrevoApiKey(null);
        emailService.setResendApiKey("re_test_dummy_key_12345");
        emailService.setConfiguredFrom("onboarding@resend.dev");

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

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Resend API Failure: Non-2xx response throws clear Resend error and does NOT require SMTP credentials")
    void testResendFailureDoesNotRequireSmtp() throws Exception {
        emailService.setMailProvider("RESEND");
        emailService.setResendApiKey("re_test_dummy_key_12345");
        emailService.setConfiguredFrom("onboarding@resend.dev");

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
                "Exception must NOT mention SMTP credentials: " + ex.getMessage());

        verify(mailSender, never()).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("SMTP Fallback: When neither Brevo nor Resend is configured and valid SMTP credentials exist, use SMTP")
    void testFallbackToSmtpWhenNeitherBrevoNorResendConfigured() {
        emailService.setMailProvider("AUTO");
        emailService.setBrevoApiKey(null);
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

        verifyNoInteractions(httpClient);
    }

    @Test
    @DisplayName("Missing Provider Configuration: When no provider is configured, throws clear error")
    void testMissingProviderConfigurationThrowsClearError() {
        emailService.setMailProvider("AUTO");
        emailService.setBrevoApiKey(null);
        emailService.setResendApiKey(null);
        emailService.setMailUsername(null);
        emailService.setMailPassword(null);

        MedChainException ex = assertThrows(MedChainException.class, () ->
                emailService.sendOtpEmail("anyone@medchain.com", "554433", "REGISTRATION")
        );

        assertTrue(ex.getMessage().contains("Email delivery is not configured on the server"),
                "Expected clear missing provider message: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("BREVO_API_KEY"),
                "Expected BREVO_API_KEY mention: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("RESEND_API_KEY"),
                "Expected RESEND_API_KEY mention: " + ex.getMessage());

        verifyNoInteractions(httpClient);
        verifyNoInteractions(mailSender);
    }

    @Test
    @DisplayName("Input Validation: Invalid recipient email address is rejected immediately")
    void testInvalidRecipientEmail() {
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail(null, "123456", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail("   ", "123456", "REGISTRATION"));
        assertThrows(MedChainException.class, () -> emailService.sendOtpEmail("no-at-sign.com", "123456", "REGISTRATION"));
    }
}
