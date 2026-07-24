package com.medchain.service;

import org.springframework.stereotype.Service;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Simulated Proxy Re-Encryption (PRE)
 *
 * Real PRE:
 *   rk = sk_patient / sk_proxy
 *   CT' = PRE.ReEncrypt(rk, CT)  — proxy transforms without seeing plaintext
 *
 * Simulation:
 *   Patient generates re-encryption key rk = H(sk_patient || doctorPID)
 *   Proxy uses rk to re-encrypt AES key for doctor
 *   Doctor decrypts with their own key
 */
@Service
public class ProxyReEncryptionService {

    /**
     * Generate re-encryption key for a specific doctor
     * rk = SHA3-256(patientPrivateKey || doctorPID)
     */
    public String generateReEncryptionKey(String patientId, String doctorPID) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            String input = patientId + doctorPID + UUID.randomUUID();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new RuntimeException("Re-encryption key generation failed", e);
        }
    }

    /**
     * Re-encrypt ciphertext for a specific doctor using re-encryption key
     * Proxy applies transformation: CT' = ReEncrypt(rk, CT)
     */
    public String reEncrypt(String ciphertext, String reEncryptionKey) {
        try {
            byte[] rkBytes = Base64.getDecoder().decode(reEncryptionKey);
            byte[] keyBytes = new byte[32];
            System.arraycopy(rkBytes, 0, keyBytes, 0, Math.min(rkBytes.length, 32));
            SecretKeySpec key = new SecretKeySpec(keyBytes, "AES");

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS5Padding");
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] reEncrypted = cipher.doFinal(ciphertext.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(reEncrypted);
        } catch (Exception e) {
            throw new RuntimeException("Re-encryption failed", e);
        }
    }

    /**
     * Generate a temporary access token for doctor (expires in 1 hour)
     */
    public String generateTempAccessKey(String recordId, String doctorId) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            String input = recordId + doctorId + System.currentTimeMillis();
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return "0x" + HexFormat.of().formatHex(hash).substring(0, 16);
        } catch (Exception e) {
            throw new RuntimeException("Temp key generation failed", e);
        }
    }
}
