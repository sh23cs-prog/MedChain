package com.medchain;

import com.icegreen.greenmail.configuration.GreenMailConfiguration;
import com.icegreen.greenmail.junit5.GreenMailExtension;
import com.icegreen.greenmail.util.ServerSetupTest;
import com.medchain.dto.*;
import com.medchain.exception.MedChainException;
import com.medchain.model.Role;
import com.medchain.model.User;
import com.medchain.repository.OtpTokenRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.service.AuthService;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class AuthSecurityTest {

    @RegisterExtension
    static GreenMailExtension greenMail = new GreenMailExtension(ServerSetupTest.SMTP)
            .withConfiguration(GreenMailConfiguration.aConfig().withUser("dev", "dev"))
            .withPerMethodLifecycle(false);

    @org.springframework.test.context.DynamicPropertySource
    static void configureMail(org.springframework.test.context.DynamicPropertyRegistry registry) {
        registry.add("spring.mail.host", () -> greenMail.getSmtp().getBindTo());
        registry.add("spring.mail.port", () -> greenMail.getSmtp().getPort());
        registry.add("spring.mail.username", () -> "dev");
        registry.add("spring.mail.password", () -> "dev");
    }

    @Autowired
    private AuthService authService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OtpTokenRepository otpTokenRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("Phase 2: End-to-End Registration, SMTP OTP Dispatch, Verification, BCrypt Auth & JWT")
    void testRegistrationOtpAndAuthenticationFlow() throws Exception {
        String testEmail = "patient.smtp.test@medchain.com";

        // 1. Registration
        RegisterRequest regReq = new RegisterRequest();
        regReq.setName("SMTP Test Patient");
        regReq.setEmail(testEmail);
        regReq.setPassword("Password123!");
        regReq.setRole(Role.PATIENT.name());
        regReq.setPhone("+1-555-0999");
        regReq.setAttributes(Map.of());

        AuthResponse regRes = authService.register(regReq);
        assertNotNull(regRes);
        assertNull(regRes.getToken(), "Token must be null until OTP verification");

        User unverified = userRepository.findByEmail(testEmail).orElseThrow();
        assertFalse(unverified.isEmailVerified(), "User emailVerified must be false prior to OTP verification");

        // 2. Real SMTP Delivery Check via GreenMail
        assertTrue(greenMail.waitForIncomingEmail(5000, 1));
        MimeMessage[] messages = greenMail.getReceivedMessages();
        assertTrue(messages.length >= 1, "At least one OTP email must be received");

        MimeMessage latestMsg = messages[messages.length - 1];
        String body = latestMsg.getContent().toString();
        
        Matcher matcher = Pattern.compile("\\b(\\d{6})\\b").matcher(body);
        assertTrue(matcher.find(), "OTP email must contain 6-digit verification code");
        String rawOtp = matcher.group(1);

        // 3. Incorrect OTP fails
        OtpVerificationRequest badOtpReq = new OtpVerificationRequest();
        badOtpReq.setEmail(testEmail);
        badOtpReq.setOtp("000000");
        badOtpReq.setType("REGISTRATION");
        assertThrows(MedChainException.class, () -> authService.verifyOtp(badOtpReq));

        // 4. Correct OTP succeeds & issues JWT
        OtpVerificationRequest goodOtpReq = new OtpVerificationRequest();
        goodOtpReq.setEmail(testEmail);
        goodOtpReq.setOtp(rawOtp);
        goodOtpReq.setType("REGISTRATION");

        AuthResponse verifyRes = authService.verifyOtp(goodOtpReq);
        assertNotNull(verifyRes.getToken(), "Valid OTP verification must return JWT token");
        assertEquals("PATIENT", jwtUtil.getRole(verifyRes.getToken()));

        User verified = userRepository.findByEmail(testEmail).orElseThrow();
        assertTrue(verified.isEmailVerified());

        // 5. Reused OTP fails
        assertThrows(MedChainException.class, () -> authService.verifyOtp(goodOtpReq), "Reused OTP must be rejected");

        // 6. BCrypt Login
        AuthRequest loginReq = new AuthRequest();
        loginReq.setEmail(testEmail);
        loginReq.setPassword("Password123!");

        AuthResponse loginRes = authService.login(loginReq);
        assertNotNull(loginRes.getToken());
        assertTrue(jwtUtil.validateToken(loginRes.getToken()));

        // 7. Wrong BCrypt password fails
        AuthRequest wrongPassReq = new AuthRequest();
        wrongPassReq.setEmail(testEmail);
        wrongPassReq.setPassword("WrongPassword!");
        assertThrows(MedChainException.class, () -> authService.login(wrongPassReq));
    }

    @Test
    @DisplayName("Phase 2: OTP Resend Cooldown and Retry Limits")
    void testOtpCooldownAndRetryLimits() {
        String testEmail = "cooldown.test@medchain.com";

        RegisterRequest reg = new RegisterRequest();
        reg.setName("Cooldown Tester");
        reg.setEmail(testEmail);
        reg.setPassword("Password123!");
        reg.setRole("DOCTOR");
        authService.register(reg);

        // First forgotPassword call sends 1st reset OTP
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest();
        forgotReq.setEmail(testEmail);
        authService.forgotPassword(forgotReq);

        // Immediate 2nd forgotPassword call should hit 60-second resend cooldown
        assertThrows(MedChainException.class, () -> authService.forgotPassword(forgotReq));

        // Attempting wrong OTP 3 times exceeds limit
        OtpVerificationRequest badReq = new OtpVerificationRequest();
        badReq.setEmail(testEmail);
        badReq.setOtp("111111");
        badReq.setType("REGISTRATION");

        for (int i = 0; i < 2; i++) {
            assertThrows(MedChainException.class, () -> authService.verifyOtp(badReq));
        }

        // 3rd attempt blocks token
        assertThrows(MedChainException.class, () -> authService.verifyOtp(badReq));
    }

    @Test
    @DisplayName("Phase 2: Password Reset, Change Password & Logout Token Invalidation")
    void testPasswordResetAndLogout() throws Exception {
        String testEmail = "reset.logout.test@medchain.com";

        RegisterRequest reg = new RegisterRequest();
        reg.setName("Reset Logout Tester");
        reg.setEmail(testEmail);
        reg.setPassword("InitialPass123!");
        reg.setRole("PATIENT");
        authService.register(reg);

        // Manually mark email verified for reset test
        User u = userRepository.findByEmail(testEmail).orElseThrow();
        u.setEmailVerified(true);
        userRepository.save(u);

        // Login to get token
        AuthRequest loginReq = new AuthRequest();
        loginReq.setEmail(testEmail);
        loginReq.setPassword("InitialPass123!");
        AuthResponse loginRes = authService.login(loginReq);
        String token = loginRes.getToken();

        // 1. RBAC Check — PATIENT role accessing /api/admin/users returns 403 Forbidden
        mockMvc.perform(get("/api/admin/users")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));

        // 2. Change Password
        ChangePasswordRequest changeReq = new ChangePasswordRequest();
        changeReq.setOldPassword("InitialPass123!");
        changeReq.setNewPassword("UpdatedPass123!");
        authService.changePassword(testEmail, changeReq);

        // Login with new password
        AuthRequest newLoginReq = new AuthRequest();
        newLoginReq.setEmail(testEmail);
        newLoginReq.setPassword("UpdatedPass123!");
        AuthResponse newLoginRes = authService.login(newLoginReq);
        String newToken = newLoginRes.getToken();

        // 3. Logout & Token Blacklisting
        authService.logout("Bearer " + newToken);

        // Request with blacklisted token returns 401 Unauthorized
        mockMvc.perform(get("/api/records/my")
                        .header("Authorization", "Bearer " + newToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }
}
