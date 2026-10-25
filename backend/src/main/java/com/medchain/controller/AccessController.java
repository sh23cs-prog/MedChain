package com.medchain.controller;

import com.medchain.dto.AccessDecisionRequest;
import com.medchain.dto.AccessRequestDto;
import com.medchain.model.AccessRequest;
import com.medchain.service.AccessService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/access")
@RequiredArgsConstructor
@Tag(name = "Access Control Lifecycle", description = "Endpoints for doctor access requests and patient approval/rejection/revocation decisions")
@SecurityRequirement(name = "Bearer Authentication")
public class AccessController {

    private final AccessService accessService;

    @PostMapping("/request")
    @Operation(summary = "Doctor submits access request for a patient record")
    public ResponseEntity<AccessRequest> requestAccess(@Valid @RequestBody AccessRequestDto dto,
                                                        Authentication auth,
                                                        HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return ResponseEntity.ok(accessService.requestAccess(dto, auth.getName(), request.getRemoteAddr(), userAgent));
    }

    @PostMapping("/approve")
    @Operation(summary = "Patient approves a doctor's pending access request")
    public ResponseEntity<AccessRequest> approveAccess(@Valid @RequestBody AccessDecisionRequest dto,
                                                        Authentication auth,
                                                        HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return ResponseEntity.ok(accessService.approveAccess(dto, auth.getName(), request.getRemoteAddr(), userAgent));
    }

    @PostMapping("/reject")
    @Operation(summary = "Patient rejects a doctor's pending access request")
    public ResponseEntity<AccessRequest> rejectAccess(@Valid @RequestBody AccessDecisionRequest dto,
                                                       Authentication auth,
                                                       HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return ResponseEntity.ok(accessService.rejectAccess(dto, auth.getName(), request.getRemoteAddr(), userAgent));
    }

    @PostMapping("/revoke")
    @Operation(summary = "Patient revokes previously approved doctor access")
    public ResponseEntity<AccessRequest> revokeAccess(@Valid @RequestBody AccessDecisionRequest dto,
                                                       Authentication auth,
                                                       HttpServletRequest request) {
        String userAgent = request.getHeader("User-Agent");
        return ResponseEntity.ok(accessService.revokeAccess(dto, auth.getName(), request.getRemoteAddr(), userAgent));
    }

    @GetMapping("/requests")
    @Operation(summary = "Patient lists incoming access requests")
    public ResponseEntity<List<AccessRequest>> getPatientRequests(Authentication auth) {
        return ResponseEntity.ok(accessService.getPatientRequests(auth.getName()));
    }

    @GetMapping("/doctor/requests")
    @Operation(summary = "Doctor lists submitted access requests")
    public ResponseEntity<List<AccessRequest>> getDoctorRequests(Authentication auth) {
        return ResponseEntity.ok(accessService.getDoctorRequests(auth.getName()));
    }
}
