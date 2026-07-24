package com.medchain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM Encryption Service
 *
 * Implements the hybrid encryption pipeline described in the paper:
 *   "Blockchain-Aided Privacy-Preserving Medical Data Sharing"
 *
 * Pipeline:
 *   1. Generate random 256-bit AES key + 96-bit IV
 *   2. Encrypt medical file bytes with AES-256-GCM (authenticated encryption)
 *   3. The AES key is then CP-ABE policy-protected (via CPABEService)
 *   4. Encrypted file → IPFS
 *   5. {encryptedAesKey, iv, ipfsCid} stored in MongoDB
 *
 * AES-GCM provides:
 *   - Confidentiality (256-bit key)
 *   - Integrity (128-bit authentication tag)
 *   - No padding needed
 */
@Slf4j
@Service
public class AESEncryptionService {

    private static final String ALGORITHM      = "AES";
    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int    KEY_SIZE_BITS  = 256;
    private static final int    GCM_TAG_BITS   = 128;
    private static final int    IV_BYTES       = 12;  // 96-bit IV recommended for GCM

    private final SecureRandom secureRandom = new SecureRandom();

    // ─── Result record ────────────────────────────────────────────────────────

    /** Holds the result of an AES encryption operation. */
    public record EncryptionResult(
        byte[] ciphertext,      // AES-GCM encrypted bytes (includes auth tag)
        String aesKeyBase64,    // Base64-encoded 256-bit AES key (to be CP-ABE protected)
        String ivBase64         // Base64-encoded 12-byte GCM IV
    ) {}

    // ─── Key Generation ───────────────────────────────────────────────────────

    /**
     * Generate a fresh 256-bit AES key.
     * @return Base64-encoded AES key string
     */
    public String generateAesKey() {
        try {
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, secureRandom);
            SecretKey key = keyGen.generateKey();
            return Base64.getEncoder().encodeToString(key.getEncoded());
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate AES key", e);
        }
    }

    // ─── Encryption ───────────────────────────────────────────────────────────

    /**
     * Encrypt arbitrary bytes using AES-256-GCM.
     *
     * @param plaintext raw bytes to encrypt
     * @return EncryptionResult containing ciphertext, AES key (base64), IV (base64)
     */
    public EncryptionResult encrypt(byte[] plaintext) {
        try {
            // Generate AES key
            KeyGenerator keyGen = KeyGenerator.getInstance(ALGORITHM);
            keyGen.init(KEY_SIZE_BITS, secureRandom);
            SecretKey aesKey = keyGen.generateKey();

            // Generate random IV
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);

            // Encrypt
            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, paramSpec);
            byte[] ciphertext = cipher.doFinal(plaintext);

            String keyB64 = Base64.getEncoder().encodeToString(aesKey.getEncoded());
            String ivB64  = Base64.getEncoder().encodeToString(iv);

            log.debug("AES-256-GCM encryption complete. Plaintext: {} bytes → Ciphertext: {} bytes",
                      plaintext.length, ciphertext.length);

            return new EncryptionResult(ciphertext, keyB64, ivB64);
        } catch (Exception e) {
            throw new RuntimeException("AES encryption failed", e);
        }
    }

    /**
     * Encrypt a UTF-8 string and return Base64-encoded ciphertext.
     */
    public EncryptionResult encryptString(String plaintext) {
        return encrypt(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    // ─── Decryption ───────────────────────────────────────────────────────────

    /**
     * Decrypt AES-256-GCM ciphertext.
     *
     * @param ciphertext   raw ciphertext bytes (includes GCM auth tag)
     * @param aesKeyBase64 Base64-encoded AES key
     * @param ivBase64     Base64-encoded IV
     * @return decrypted plaintext bytes
     */
    public byte[] decrypt(byte[] ciphertext, String aesKeyBase64, String ivBase64) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(aesKeyBase64);
            byte[] iv       = Base64.getDecoder().decode(ivBase64);

            SecretKey aesKey = new SecretKeySpec(keyBytes, ALGORITHM);
            Cipher cipher    = Cipher.getInstance(TRANSFORMATION);
            GCMParameterSpec paramSpec = new GCMParameterSpec(GCM_TAG_BITS, iv);
            cipher.init(Cipher.DECRYPT_MODE, aesKey, paramSpec);

            byte[] plaintext = cipher.doFinal(ciphertext);
            log.debug("AES-256-GCM decryption complete. {} bytes decrypted.", plaintext.length);
            return plaintext;
        } catch (Exception e) {
            throw new RuntimeException("AES decryption failed — key mismatch or data corruption", e);
        }
    }

    /**
     * Decrypt and return result as a UTF-8 string.
     */
    public String decryptToString(byte[] ciphertext, String aesKeyBase64, String ivBase64) {
        byte[] plaintext = decrypt(ciphertext, aesKeyBase64, ivBase64);
        return new String(plaintext, java.nio.charset.StandardCharsets.UTF_8);
    }

    /**
     * Decrypt from Base64-encoded ciphertext string.
     */
    public byte[] decryptFromBase64(String ciphertextBase64, String aesKeyBase64, String ivBase64) {
        byte[] ciphertext = Base64.getDecoder().decode(ciphertextBase64);
        return decrypt(ciphertext, aesKeyBase64, ivBase64);
    }

    // ─── Timing utility (for metrics) ─────────────────────────────────────────

    /**
     * Encrypt and return time taken in milliseconds alongside result.
     */
    public record TimedResult(EncryptionResult result, long encryptionTimeMs) {}

    public TimedResult encryptTimed(byte[] plaintext) {
        long start  = System.currentTimeMillis();
        EncryptionResult result = encrypt(plaintext);
        long elapsed = System.currentTimeMillis() - start;
        return new TimedResult(result, elapsed);
    }
}
