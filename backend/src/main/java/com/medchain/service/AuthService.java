package com.medchain.service;

import com.medchain.dto.*;
import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.Role;
import com.medchain.model.User;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.security.TokenBlacklistService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final PseudonymService pseudonymService;
    private final BlockchainService blockchainService;
    private final OtpService otpService;
    private final TokenBlacklistService tokenBlacklistService;
    private final AccessLogRepository accessLogRepository;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail())) {
            throw new MedChainException("Email already registered: " + req.getEmail());
        }

        String roleStr = req.getRole().toUpperCase();
        try {
            Role.valueOf(roleStr);
        } catch (IllegalArgumentException e) {
            throw new MedChainException("Invalid role specified. Allowed roles: PATIENT, DOCTOR, ADMIN");
        }

        String nonce = pseudonymService.generateNonce();
        String pseudonymId = pseudonymService.generatePseudonym(req.getEmail(), nonce);

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setRole(roleStr);
        user.setPhone(req.getPhone());
        user.setPseudonymId(pseudonymId);
        user.setPseudonymNonce(nonce);
        user.setAttributes(req.getAttributes());
        user.setActive(true);
        user.setBlocked(false);
        user.setEmailVerified(false);

        User saved = userRepository.save(user);

        // Register pseudonym on local EVM node
        if (blockchainService.isAvailable()) {
            blockchainService.registerPseudonym(pseudonymId, saved.getRole());
        }

        // Generate and send 6-digit OTP
        String rawOtp = otpService.generateAndSendOtp(saved.getEmail(), "REGISTRATION");

        logAudit(saved.getId(), saved.getName(), saved.getPseudonymId(), "REGISTER", true, null);
        logAudit(saved.getId(), saved.getName(), saved.getPseudonymId(), "OTP_REQUEST", true, null);

        AuthResponse res = buildResponse(saved, null);
        res.setToken(null); // No token returned until OTP is verified
        res.setOtpPreview(rawOtp);
        res.setMessage("Account created successfully. Your 6-digit verification code is: " + rawOtp);
        return res;
    }

    public AuthResponse verifyOtp(OtpVerificationRequest req) {
        otpService.verifyOtp(req.getEmail(), req.getOtp(), req.getType());

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new MedChainException("User not found for email: " + req.getEmail()));

        if ("REGISTRATION".equalsIgnoreCase(req.getType())) {
            user.setEmailVerified(true);
            userRepository.save(user);
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "OTP_VERIFY", true, null);

        return buildResponse(user, token);
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> {
                    logAudit(null, req.getEmail(), null, "LOGIN_FAILED", false, "User not found");
                    return new MedChainException("Invalid credentials");
                });

        if (!user.isActive()) {
            logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGIN_FAILED", false, "Account deactivated");
            throw new MedChainException("Account is deactivated. Please contact an administrator.");
        }

        if (user.isBlocked()) {
            logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGIN_FAILED", false, "Account blocked");
            throw new MedChainException("Account blocked. Contact administrator.");
        }

        if (!user.isEmailVerified()) {
            // Re-send OTP for verification
            String rawOtp = otpService.generateAndSendOtp(user.getEmail(), "REGISTRATION");
            logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGIN_FAILED", false, "Email unverified — OTP re-sent");
            throw new MedChainException("Email not verified. A fresh OTP code has been generated: " + rawOtp);
        }

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGIN_FAILED", false, "Password mismatch");
            throw new MedChainException("Invalid credentials");
        }

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGIN", true, null);

        return buildResponse(user, token);
    }

    public String resendOtp(String email, String type) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new MedChainException("User not found with email: " + email));
        String rawOtp = otpService.generateAndSendOtp(user.getEmail(), type != null ? type : "REGISTRATION");
        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "OTP_REQUEST", true, "OTP resent via email");
        return rawOtp;
    }

    public String forgotPassword(ForgotPasswordRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new MedChainException("User not found with email: " + req.getEmail()));

        String rawOtp = otpService.generateAndSendOtp(user.getEmail(), "PASSWORD_RESET");
        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "OTP_REQUEST", true, "Password reset OTP requested");
        return rawOtp;
    }

    public void resetPassword(ResetPasswordRequest req) {
        otpService.verifyOtp(req.getEmail(), req.getOtp(), "PASSWORD_RESET");

        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new MedChainException("User not found with email: " + req.getEmail()));

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "PASSWORD_RESET", true, null);
    }

    public void changePassword(String email, ChangePasswordRequest req) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new MedChainException("User not found with email: " + email));

        if (!passwordEncoder.matches(req.getOldPassword(), user.getPassword())) {
            throw new MedChainException("Current password does not match.");
        }

        user.setPassword(passwordEncoder.encode(req.getNewPassword()));
        userRepository.save(user);

        logAudit(user.getId(), user.getName(), user.getPseudonymId(), "PASSWORD_RESET", true, "Password changed via authenticated profile");
    }

    public UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new MedChainException("User not found with email: " + email));
        UserResponse res = new UserResponse();
        res.setId(user.getId());
        res.setEmail(user.getEmail());
        res.setName(user.getName());
        res.setRole(user.getRole());
        res.setPhone(user.getPhone());
        res.setPseudonymId(user.getPseudonymId());
        res.setAttributes(user.getAttributes());
        res.setActive(user.isActive());
        res.setBlocked(user.isBlocked());
        res.setCreatedAt(user.getCreatedAt());
        return res;
    }

    public void logout(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            tokenBlacklistService.blacklistToken(token);
            try {
                String email = jwtUtil.getEmail(token);
                User user = userRepository.findByEmail(email).orElse(null);
                if (user != null) {
                    logAudit(user.getId(), user.getName(), user.getPseudonymId(), "LOGOUT", true, null);
                }
            } catch (Exception ignored) {}
        }
    }

    private void logAudit(String userId, String userName, String pid, String action, boolean granted, String denyReason) {
        AccessLog log = new AccessLog();
        log.setUserId(userId);
        log.setUserName(userName);
        log.setRequesterPID(pid);
        log.setAction(action);
        log.setGranted(granted);
        log.setDenyReason(denyReason);
        log.setRiskLevel("LOW");
        log.setTimestamp(LocalDateTime.now());
        accessLogRepository.save(log);
    }

    private AuthResponse buildResponse(User user, String token) {
        AuthResponse res = new AuthResponse();
        res.setToken(token);
        res.setId(user.getId());
        res.setName(user.getName());
        res.setEmail(user.getEmail());
        res.setRole(user.getRole());
        res.setPseudonymId(user.getPseudonymId());
        res.setAttributes(user.getAttributes());
        res.setActive(user.isActive());
        res.setBlocked(user.isBlocked());
        return res;
    }
}
