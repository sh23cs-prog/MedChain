package com.medchain.service;

import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PseudonymService {

    private final SecureRandom secureRandom = new SecureRandom();

    public String generateNonce() {
        byte[] nonce = new byte[32];
        secureRandom.nextBytes(nonce);
        return Base64.getEncoder().encodeToString(nonce);
    }

    /**
     * PID = SHA3-256(identity || nonce) — returns the full 64-char hex string.
     * Real identity is never stored on-chain — only PID is public.
     *
     * Stored as "0x" + 64 hex chars = a valid Ethereum bytes32 value.
     */
    public String generatePseudonym(String identity, String nonce) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            String input = identity + nonce;
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return "0x" + HexFormat.of().formatHex(hash); // full 64-char hex — valid bytes32
        } catch (Exception e) {
            throw new RuntimeException("Pseudonym generation failed", e);
        }
    }

    /**
     * Returns a display-friendly truncated PID for UI use only.
     * NOT stored in DB or on-chain — use generatePseudonym() for storage.
     */
    public String displayPseudonym(String fullPid) {
        if (fullPid == null || fullPid.length() < 12) return fullPid;
        return fullPid.substring(0, 10) + "..." + fullPid.substring(fullPid.length() - 4);
    }
}
