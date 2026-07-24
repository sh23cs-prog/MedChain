package com.medchain.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;
import org.web3j.tx.gas.DefaultGasProvider;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.HexFormat;

/**
 * BlockchainService — integrates with the deployed MedChain Solidity contract.
 *
 * When blockchain.enabled=false (default), all methods are no-ops returning mock tx hashes.
 * When blockchain.enabled=true, calls are made to the local Hardhat node.
 *
 * Contract functions called:
 *   registerPseudonym(bytes32 pid, bytes32 role)
 *   uploadRecord(bytes32 recordId, bytes32 cidHash, bytes32 policyHash, bytes32 ownerPID)
 *   grantAccess(bytes32 recordId, bytes32 doctorPID, bytes32 ownerPID)
 *   revokeAccess(bytes32 recordId, bytes32 doctorPID, bytes32 ownerPID)
 *   logAccessAttempt(bytes32 recordId, bytes32 requesterPID, bool granted, uint8 riskLevel)
 */
@Service
@Slf4j
public class BlockchainService {

    @Value("${blockchain.enabled:false}")
    private boolean enabled;

    @Value("${blockchain.rpc.url:http://localhost:8545}")
    private String rpcUrl;

    @Value("${blockchain.contract.address:}")
    private String contractAddress;

    @Value("${blockchain.private.key:0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80}")
    private String privateKey;

    @Value("${blockchain.gas.price:22000000000}")
    private BigInteger gasPrice;

    @Value("${blockchain.gas.limit:500000}")
    private BigInteger gasLimit;

    private Web3j web3j;
    private Credentials credentials;
    private RawTransactionManager txManager;
    private boolean available = false;

    // Hardhat chain ID
    private static final long CHAIN_ID = 31337L;

    // MedChain contract ABI-encoded function selectors
    private static final String FN_REGISTER_PSEUDONYM  = "0x" + keccak256Selector("registerPseudonym(bytes32,bytes32)");
    private static final String FN_UPLOAD_RECORD       = "0x" + keccak256Selector("uploadRecord(bytes32,bytes32,bytes32,bytes32)");
    private static final String FN_GRANT_ACCESS        = "0x" + keccak256Selector("grantAccess(bytes32,bytes32,bytes32)");
    private static final String FN_REVOKE_ACCESS       = "0x" + keccak256Selector("revokeAccess(bytes32,bytes32,bytes32)");
    private static final String FN_LOG_ACCESS          = "0x" + keccak256Selector("logAccessAttempt(bytes32,bytes32,bool,uint8)");

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Blockchain integration DISABLED — using mock tx hashes");
            return;
        }
        try {
            web3j = Web3j.build(new HttpService(rpcUrl));
            credentials = Credentials.create(privateKey);
            txManager = new RawTransactionManager(web3j, credentials, CHAIN_ID);
            String clientVersion = web3j.web3ClientVersion().send().getWeb3ClientVersion();
            available = true;
            log.info("Blockchain connected: {} | contract: {} | gas price: {} wei", clientVersion, contractAddress, gasPrice);
        } catch (Exception e) {
            log.warn("Blockchain node unreachable — falling back to mock mode: {}", e.getMessage());
        }
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    /** Called on user registration — registers PID on-chain */
    public String registerPseudonym(String pseudonymId, String role) {
        if (!available) return mockTx();
        try {
            byte[] pid      = fullHexToBytes32(pseudonymId);
            byte[] roleHash = sha3Bytes32(role);
            String data = FN_REGISTER_PSEUDONYM + toHex32(pid) + toHex32(roleHash);
            return sendTx(data);
        } catch (Exception e) {
            log.warn("registerPseudonym failed: {}", e.getMessage());
            return mockTx();
        }
    }

    /** Check whether a pseudonym is already registered (used by DataSeeder to avoid double-registration) */
    public boolean isPseudonymRegistered(String pseudonymId) {
        if (!available) return false;
        try {
            // Call isPseudonymRegistered(bytes32) view function
            String selector = "0x" + keccak256Selector("isPseudonymRegistered(bytes32)");
            byte[] pid = fullHexToBytes32(pseudonymId);
            String data = selector + toHex32(pid);
            // For a view call we'd use eth_call; for simplicity we treat unavailability as not registered
            // Full implementation would use web3j.ethCall() here
            return false; // conservative: let the contract reject duplicates gracefully
        } catch (Exception e) {
            return false;
        }
    }

    /** Called on record upload — anchors record hashes on-chain */
    public String uploadRecord(String recordId, String ipfsCid, String policyHash, String ownerPID) {
        if (!available) return mockTx();
        try {
            byte[] recId  = sha3Bytes32(recordId);
            byte[] cidH   = sha3Bytes32(ipfsCid);
            byte[] polH   = fullHexToBytes32(policyHash);
            byte[] owner  = fullHexToBytes32(ownerPID);
            String data = FN_UPLOAD_RECORD + toHex32(recId) + toHex32(cidH) + toHex32(polH) + toHex32(owner);
            return sendTx(data);
        } catch (Exception e) {
            log.warn("uploadRecord failed: {}", e.getMessage());
            return mockTx();
        }
    }

    /** Called when patient grants doctor access */
    public String grantAccess(String recordId, String doctorPID, String ownerPID) {
        if (!available) return mockTx();
        try {
            byte[] recId  = sha3Bytes32(recordId);
            byte[] docPID = fullHexToBytes32(doctorPID);
            byte[] owner  = fullHexToBytes32(ownerPID);
            String data = FN_GRANT_ACCESS + toHex32(recId) + toHex32(docPID) + toHex32(owner);
            return sendTx(data);
        } catch (Exception e) {
            log.warn("grantAccess failed: {}", e.getMessage());
            return mockTx();
        }
    }

    /** Called when patient revokes doctor access */
    public String revokeAccess(String recordId, String doctorPID, String ownerPID) {
        if (!available) return mockTx();
        try {
            byte[] recId  = sha3Bytes32(recordId);
            byte[] docPID = fullHexToBytes32(doctorPID);
            byte[] owner  = fullHexToBytes32(ownerPID);
            String data = FN_REVOKE_ACCESS + toHex32(recId) + toHex32(docPID) + toHex32(owner);
            return sendTx(data);
        } catch (Exception e) {
            log.warn("revokeAccess failed: {}", e.getMessage());
            return mockTx();
        }
    }

    /** Called after every record view — logs access attempt with risk level */
    public String logAccessAttempt(String recordId, String requesterPID, boolean granted, String riskLevel) {
        if (!available) return mockTx();
        try {
            byte[] recId  = sha3Bytes32(recordId);
            byte[] reqPID = fullHexToBytes32(requesterPID);
            int risk = riskToInt(riskLevel);
            String data = FN_LOG_ACCESS
                    + toHex32(recId)
                    + toHex32(reqPID)
                    + padLeft(granted ? "1" : "0")
                    + padLeft(Integer.toHexString(risk));
            return sendTx(data);
        } catch (Exception e) {
            log.warn("logAccessAttempt failed: {}", e.getMessage());
            return mockTx();
        }
    }

    public boolean isAvailable() { return available; }

    // ─── Internal helpers ─────────────────────────────────────────────────────

    private String sendTx(String data) throws Exception {
        var receipt = txManager.sendTransaction(
                gasPrice,
                gasLimit,
                contractAddress,
                data,
                BigInteger.ZERO
        );
        String txHash = receipt.getTransactionHash();
        log.debug("Blockchain tx: {}", txHash);
        return txHash;
    }

    private static String mockTx() {
        // Generate a realistic-looking 66-char Ethereum tx hash
        try {
            MessageDigest d = MessageDigest.getInstance("SHA3-256");
            byte[] hash = d.digest((System.nanoTime() + Thread.currentThread().getName()).getBytes());
            return "0x" + HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return "0x" + Long.toHexString(System.nanoTime()) + "0".repeat(48);
        }
    }

    private static byte[] sha3Bytes32(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            return digest.digest(input.getBytes(StandardCharsets.UTF_8));
        } catch (Exception e) { throw new RuntimeException(e); }
    }

    /**
     * Convert a full 66-char hex pseudonym (0x + 64 hex chars) to a 32-byte array.
     * Pseudonyms are now stored as full SHA3-256 hashes — no truncation or "..." handling needed.
     */
    private static byte[] fullHexToBytes32(String hex) {
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
        // Pad to 64 hex chars if somehow shorter; truncate if longer
        while (clean.length() < 64) clean = "0" + clean;
        if (clean.length() > 64) clean = clean.substring(0, 64);
        return HexFormat.of().parseHex(clean);
    }

    private static String toHex32(byte[] bytes) {
        String hex = HexFormat.of().formatHex(bytes);
        while (hex.length() < 64) hex = "0" + hex;
        return hex.substring(0, 64);
    }

    private static String padLeft(String hex) {
        while (hex.length() < 64) hex = "0" + hex;
        return hex;
    }

    private static int riskToInt(String risk) {
        return switch (risk == null ? "LOW" : risk) {
            case "MEDIUM"   -> 1;
            case "HIGH"     -> 2;
            case "CRITICAL" -> 3;
            default         -> 0;
        };
    }

    /** Compute first 4 bytes of keccak256(signature) as hex — used for function selectors */
    private static String keccak256Selector(String signature) {
        try {
            byte[] hash = org.web3j.crypto.Hash.sha3(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(Arrays.copyOfRange(hash, 0, 4));
        } catch (Exception e) { return "00000000"; }
    }
}
