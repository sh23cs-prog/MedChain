package com.medchain.security;

import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TokenBlacklistService {

    private final Set<String> blacklistedTokens = ConcurrentHashMap.newKeySet();
    private final Set<String> revokedUserEmails = ConcurrentHashMap.newKeySet();

    public void blacklistToken(String token) {
        if (token != null && !token.isBlank()) {
            blacklistedTokens.add(token.trim());
        }
    }

    public boolean isBlacklisted(String token) {
        if (token == null || token.isBlank()) {
            return false;
        }
        return blacklistedTokens.contains(token.trim());
    }

    public void revokeUser(String email) {
        if (email != null && !email.isBlank()) {
            revokedUserEmails.add(email.trim().toLowerCase());
        }
    }

    public void unrevokeUser(String email) {
        if (email != null && !email.isBlank()) {
            revokedUserEmails.remove(email.trim().toLowerCase());
        }
    }

    public boolean isUserRevoked(String email) {
        if (email == null || email.isBlank()) {
            return false;
        }
        return revokedUserEmails.contains(email.trim().toLowerCase());
    }
}
