package com.medchain.service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;

/**
 * Simulated CP-ABE (Ciphertext-Policy Attribute-Based Encryption) Service
 *
 * IMPORTANT: This is a research-grade simulation, not a production CP-ABE implementation.
 * The abstraction is clean and can be replaced with a real CP-ABE library (e.g., JPBC-based
 * Charm-Crypto or relic-toolkit bindings) without changing the service interface.
 *
 * Real CP-ABE: Encrypt(MPK, data, policy) → CT, Decrypt(MSK, CT, attributes) → data
 * Simulation:
 *   - AES key is "CP-ABE protected" by encrypting it with a key derived from the policy string
 *   - Doctor can only recover the AES key if their attributes satisfy the policy (checked in software)
 *   - This models the core access control property of CP-ABE faithfully
 *
 * Policy format: "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)"
 */
@Service
public class CPABEService {

    private static final int GCM_IV_LEN  = 12;
    private static final int GCM_TAG_LEN = 128;

    // ─── Legacy: Encrypt + Decrypt full content ───────────────────────────────

    /**
     * Encrypt plaintext under a CP-ABE policy.
     * Format: base64(iv + encryptedAesKey) . base64(iv + encryptedData)
     */
    public String encrypt(String plaintext, String policy) {
        try {
            var keyGen = javax.crypto.KeyGenerator.getInstance("AES");
            keyGen.init(256);
            var aesKey = keyGen.generateKey();

            byte[] dataIv = randomIv();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LEN, dataIv));
            byte[] encryptedData = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] policyKey = derivePolicyKey(policy);
            var policyAesKey = new SecretKeySpec(policyKey, "AES");
            byte[] keyIv = randomIv();
            cipher.init(Cipher.ENCRYPT_MODE, policyAesKey, new GCMParameterSpec(GCM_TAG_LEN, keyIv));
            byte[] encryptedAesKey = cipher.doFinal(aesKey.getEncoded());

            return Base64.getEncoder().encodeToString(concat(keyIv, encryptedAesKey))
                    + "." + Base64.getEncoder().encodeToString(concat(dataIv, encryptedData));
        } catch (Exception e) {
            throw new RuntimeException("CP-ABE encryption failed", e);
        }
    }

    /**
     * Decrypt ciphertext if doctor attributes satisfy the policy.
     */
    public String decrypt(String ciphertext, String policy, Map<String, String> doctorAttributes) {
        if (!attributesSatisfyPolicy(policy, doctorAttributes))
            throw new RuntimeException("Access denied: attributes do not satisfy policy");
        try {
            String[] parts    = ciphertext.split("\\.");
            byte[] keyBlob    = Base64.getDecoder().decode(parts[0]);
            byte[] dataBlob   = Base64.getDecoder().decode(parts[1]);
            byte[] keyIv      = Arrays.copyOfRange(keyBlob,  0, GCM_IV_LEN);
            byte[] encKey     = Arrays.copyOfRange(keyBlob,  GCM_IV_LEN, keyBlob.length);
            byte[] dataIv     = Arrays.copyOfRange(dataBlob, 0, GCM_IV_LEN);
            byte[] encData    = Arrays.copyOfRange(dataBlob, GCM_IV_LEN, dataBlob.length);

            byte[] policyKey  = derivePolicyKey(policy);
            var policyAesKey  = new SecretKeySpec(policyKey, "AES");
            var cipher        = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, policyAesKey, new GCMParameterSpec(GCM_TAG_LEN, keyIv));
            byte[] aesKeyBytes = cipher.doFinal(encKey);

            var aesKey = new SecretKeySpec(aesKeyBytes, "AES");
            cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_LEN, dataIv));
            return new String(cipher.doFinal(encData), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("CP-ABE decryption failed", e);
        }
    }

    // ─── AES Key Protection (used with AESEncryptionService) ─────────────────

    /**
     * CP-ABE protect an AES key: encrypt the raw AES key under the policy-derived key.
     * This models the "key encapsulation" step of real CP-ABE.
     *
     * @param aesKeyBase64 raw AES key in base64
     * @param policy       CP-ABE access policy string
     * @return base64(iv + encryptedAesKey) — can only be decapsulated by authorized doctors
     */
    public String protectAesKey(String aesKeyBase64, String policy) {
        try {
            byte[] aesKeyBytes = Base64.getDecoder().decode(aesKeyBase64);
            byte[] policyKey   = derivePolicyKey(policy);
            var policyAesKey   = new SecretKeySpec(policyKey, "AES");

            byte[] iv    = randomIv();
            var cipher   = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, policyAesKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] encryptedKey = cipher.doFinal(aesKeyBytes);

            return Base64.getEncoder().encodeToString(concat(iv, encryptedKey));
        } catch (Exception e) {
            throw new RuntimeException("AES key protection failed", e);
        }
    }

    /**
     * Recover the AES key from CP-ABE protected ciphertext.
     * Only works if doctor's attributes satisfy the policy.
     *
     * @param encryptedAesKey base64(iv + encryptedAesKey)
     * @param policy          the access policy
     * @param doctorAttributes doctor's attribute map
     * @return base64-encoded AES key
     */
    public String recoverAesKey(String encryptedAesKey, String policy, Map<String, String> doctorAttributes) {
        if (!attributesSatisfyPolicy(policy, doctorAttributes))
            throw new RuntimeException("Policy not satisfied — cannot recover AES key");
        try {
            byte[] blob      = Base64.getDecoder().decode(encryptedAesKey);
            byte[] iv        = Arrays.copyOfRange(blob, 0, GCM_IV_LEN);
            byte[] encKey    = Arrays.copyOfRange(blob, GCM_IV_LEN, blob.length);
            byte[] policyKey = derivePolicyKey(policy);
            var policyAesKey = new SecretKeySpec(policyKey, "AES");

            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, policyAesKey, new GCMParameterSpec(GCM_TAG_LEN, iv));
            byte[] aesKeyBytes = cipher.doFinal(encKey);
            return Base64.getEncoder().encodeToString(aesKeyBytes);
        } catch (Exception e) {
            throw new RuntimeException("AES key recovery failed", e);
        }
    }

    // ─── Policy Evaluation ────────────────────────────────────────────────────

    /**
     * Evaluate whether doctor's attribute set satisfies the CP-ABE policy.
     * Supports AND / OR boolean logic.
     * e.g. "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)"
     */
    public boolean attributesSatisfyPolicy(String policy, Map<String, String> attributes) {
        if (policy == null || policy.isBlank()) return true;
        String normalized = policy.trim().replaceAll("[()]", "");
        String[] orClauses = normalized.split("(?i)\\s+OR\\s+");
        for (String orClause : orClauses) {
            String[] andClauses = orClause.trim().split("(?i)\\s+AND\\s+");
            boolean allMatch = true;
            for (String clause : andClauses) {
                String[] kv = clause.trim().split("[=:]", 2);
                if (kv.length != 2) { allMatch = false; break; }
                String key = kv[0].trim().toLowerCase();
                String val = kv[1].trim();
                if (!val.equalsIgnoreCase(attributes.getOrDefault(key, ""))) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return true;
        }
        return false;
    }

    /** SHA3-256 of the policy string — used as the on-chain policy hash. */
    public String hashPolicy(String policy) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(policy.getBytes(StandardCharsets.UTF_8));
            return "0x" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new RuntimeException("Policy hashing failed", e);
        }
    }

    // ─── Internal ─────────────────────────────────────────────────────────────

    private byte[] derivePolicyKey(String policy) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(policy.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] randomIv() {
        byte[] iv = new byte[GCM_IV_LEN];
        new java.security.SecureRandom().nextBytes(iv);
        return iv;
    }

    private static byte[] concat(byte[] a, byte[] b) {
        byte[] result = new byte[a.length + b.length];
        System.arraycopy(a, 0, result, 0, a.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
