package com.medchain.controller;

import com.medchain.dto.*;
import com.medchain.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User registration, OTP verification, JWT authentication, and password management")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register user and dispatch OTP via SMTP")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ResponseEntity.ok(authService.register(req));
    }

    @PostMapping("/verify-otp")
    @Operation(summary = "Verify OTP code and retrieve JWT token")
    public ResponseEntity<AuthResponse> verifyOtp(@Valid @RequestBody OtpVerificationRequest req) {
        return ResponseEntity.ok(authService.verifyOtp(req));
    }

    @PostMapping("/resend-otp")
    @Operation(summary = "Resend 6-digit OTP code to user's email")
    public ResponseEntity<Map<String, String>> resendOtp(
            @RequestParam("email") String email,
            @RequestParam(value = "type", defaultValue = "REGISTRATION") String type) {
        String rawOtp = authService.resendOtp(email, type);
        return ResponseEntity.ok(Map.of(
                "message", "A fresh OTP code has been dispatched. (Code: " + rawOtp + ")",
                "otpCode", rawOtp
        ));
    }

    @PostMapping("/login")
    @Operation(summary = "Authenticate user and issue JWT token")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest req) {
        return ResponseEntity.ok(authService.login(req));
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get authenticated user profile details")
    public ResponseEntity<UserResponse> getMe(org.springframework.security.core.Authentication auth) {
        return ResponseEntity.ok(authService.getMe(auth.getName()));
    }

    @PostMapping("/forgot-password")
    @Operation(summary = "Request password reset OTP via email")
    public ResponseEntity<Map<String, String>> forgotPassword(@Valid @RequestBody ForgotPasswordRequest req) {
        String rawOtp = authService.forgotPassword(req);
        return ResponseEntity.ok(Map.of(
                "message", "Password reset OTP sent successfully. (Code: " + rawOtp + ")",
                "otpCode", rawOtp
        ));
    }

    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using OTP code")
    public ResponseEntity<Map<String, String>> resetPassword(@Valid @RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(Map.of("message", "Password reset successfully. You can now login with your new password."));
    }

    @PostMapping("/change-password")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Change password for authenticated user")
    public ResponseEntity<Map<String, String>> changePassword(
            @AuthenticationPrincipal String email,
            @Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(email, req);
        return ResponseEntity.ok(Map.of("message", "Password updated successfully."));
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout user and invalidate JWT token")
    public ResponseEntity<Map<String, String>> logout(@RequestHeader(value = "Authorization", required = false) String authHeader) {
        authService.logout(authHeader);
        return ResponseEntity.ok(Map.of("message", "Logged out successfully. Token invalidated."));
    }
}
