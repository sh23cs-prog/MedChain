package com.medchain.service;

import com.medchain.dto.AuthRequest;
import com.medchain.dto.AuthResponse;
import com.medchain.dto.RegisterRequest;
import com.medchain.exception.MedChainException;
import com.medchain.model.User;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;
    private final PasswordEncoder passwordEncoder;
    private final PseudonymService pseudonymService;
    private final BlockchainService blockchainService;

    public AuthResponse register(RegisterRequest req) {
        if (userRepository.existsByEmail(req.getEmail()))
            throw new MedChainException("Email already registered");

        String nonce = pseudonymService.generateNonce();
        String pseudonymId = pseudonymService.generatePseudonym(req.getEmail(), nonce);

        User user = new User();
        user.setEmail(req.getEmail());
        user.setPassword(passwordEncoder.encode(req.getPassword()));
        user.setName(req.getName());
        user.setRole(req.getRole().toUpperCase());
        user.setPhone(req.getPhone());
        user.setPseudonymId(pseudonymId);
        user.setPseudonymNonce(nonce);
        user.setAttributes(req.getAttributes());

        User saved = userRepository.save(user);

        // Register pseudonym on blockchain
        blockchainService.registerPseudonym(pseudonymId, saved.getRole());

        String token = jwtUtil.generateToken(saved.getEmail(), saved.getRole());
        return buildResponse(saved, token);
    }

    public AuthResponse login(AuthRequest req) {
        User user = userRepository.findByEmail(req.getEmail())
                .orElseThrow(() -> new MedChainException("Invalid credentials"));

        if (user.isBlocked())
            throw new MedChainException("Account blocked. Contact admin.");

        if (!passwordEncoder.matches(req.getPassword(), user.getPassword()))
            throw new MedChainException("Invalid credentials");

        String token = jwtUtil.generateToken(user.getEmail(), user.getRole());
        return buildResponse(user, token);
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
