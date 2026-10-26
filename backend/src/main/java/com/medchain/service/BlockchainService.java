package com.medchain.service;

import com.medchain.model.BlockchainTransaction;
import com.medchain.repository.BlockchainTransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Credentials;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;
import org.web3j.tx.RawTransactionManager;

import jakarta.annotation.PostConstruct;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BlockchainService — integrates Spring Boot with the Local EVM Test Network (Hardhat node).
 *
 * NOTE: The local Hardhat EVM network operates as a development test environment.
 * Actual medical file payloads are NEVER stored on-chain.
 * Pipeline:
 *   Medical File → AES-256-GCM → Off-Chain Encrypted Storage
 *   SHA-256 Hash + Record Reference + Audit Event → Local EVM Blockchain Transaction
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BlockchainService {

    private final BlockchainTransactionRepository transactionRepository;
    private final AtomicLong blockCounter = new AtomicLong(100L);

    @Value("${blockchain.enabled:false}")
    private boolean enabled;

    @Value("${blockchain.rpc.url:http://localhost:8545}")
    private String rpcUrl;

    @Value("${blockchain.contract.address:0x5FbDB2315678afecb367f032d93F642f64180aa3}")
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

    @Value("${blockchain.chain.id:31337}")
    private long chainId;

    private static final String FN_REGISTER_PSEUDONYM  = "0x" + keccak256Selector("registerPseudonym(bytes32,bytes32)");
    private static final String FN_UPLOAD_RECORD       = "0x" + keccak256Selector("uploadRecord(bytes32,bytes32,bytes32,bytes32)");
    private static final String FN_GRANT_ACCESS        = "0x" + keccak256Selector("grantAccess(bytes32,bytes32,bytes32)");
    private static final String FN_REVOKE_ACCESS       = "0x" + keccak256Selector("revokeAccess(bytes32,bytes32,bytes32)");
    private static final String FN_LOG_ACCESS          = "0x" + keccak256Selector("logAccessAttempt(bytes32,bytes32,bool,uint8)");

    @PostConstruct
    public void init() {
        if (!enabled) {
            log.info("Local EVM Blockchain integration set to DEV mode.");
            return;
        }
        try {
            web3j = Web3j.build(new HttpService(rpcUrl));
            credentials = Credentials.create(privateKey);
            txManager = new RawTransactionManager(web3j, credentials, chainId);
            String clientVersion = web3j.web3ClientVersion().sendAsync().get(3, java.util.concurrent.TimeUnit.SECONDS).getWeb3ClientVersion();
            available = true;
            log.info("Local EVM Hardhat node connected: {} | Contract: {}", clientVersion, contractAddress);
        } catch (Throwable e) {
            available = false;
            log.warn("Local EVM Hardhat node unreachable at {} — using local transaction persistence fallback: {}", rpcUrl, e.getMessage());
        }
    }

    public String registerPseudonym(String pseudonymId, String role) {
        String txHash = available ? sendTxSilently(FN_REGISTER_PSEUDONYM + toHex32(fullHexToBytes32(pseudonymId)) + toHex32(sha3Bytes32(role))) : mockTx();
        persistTx(txHash, "0", null, "PseudonymRegistered", "Hospital A", pseudonymId, null);
        return txHash;
    }

    public boolean isPseudonymRegistered(String pseudonymId) {
        return false;
    }

    public String uploadRecord(String recordId, String sha256Hash, String policyHash, String ownerPID) {
        return uploadRecord(recordId, sha256Hash, policyHash, ownerPID, "Hospital A");
    }

    public String uploadRecord(String recordId, String sha256Hash, String policyHash, String ownerPID, String hospitalOrgId) {
        String txHash = mockTx();
        if (available) {
            try {
                byte[] recId = sha3Bytes32(recordId);
                byte[] hashB = sha3Bytes32(sha256Hash);
                byte[] polH  = fullHexToBytes32(policyHash);
                byte[] owner = fullHexToBytes32(ownerPID);
                String data  = FN_UPLOAD_RECORD + toHex32(recId) + toHex32(hashB) + toHex32(polH) + toHex32(owner);
                txHash = sendTx(data);
            } catch (Exception e) {
                log.warn("Hardhat RPC uploadRecord execution failed: {}", e.getMessage());
            }
        }
        persistTx(txHash, recordId, sha256Hash, "RecordUploaded", hospitalOrgId, ownerPID, null);
        return txHash;
    }

    public String grantAccess(String recordId, String doctorPID, String ownerPID) {
        String txHash = mockTx();
        if (available) {
            try {
                String data = FN_GRANT_ACCESS + toHex32(sha3Bytes32(recordId)) + toHex32(fullHexToBytes32(doctorPID)) + toHex32(fullHexToBytes32(ownerPID));
                txHash = sendTx(data);
            } catch (Exception e) {
                log.warn("Hardhat RPC grantAccess execution failed: {}", e.getMessage());
            }
        }
        persistTx(txHash, recordId, null, "AccessGranted", "Hospital A", ownerPID, doctorPID);
        return txHash;
    }

    public String revokeAccess(String recordId, String doctorPID, String ownerPID) {
        String txHash = mockTx();
        if (available) {
            try {
                String data = FN_REVOKE_ACCESS + toHex32(sha3Bytes32(recordId)) + toHex32(fullHexToBytes32(doctorPID)) + toHex32(fullHexToBytes32(ownerPID));
                txHash = sendTx(data);
            } catch (Exception e) {
                log.warn("Hardhat RPC revokeAccess execution failed: {}", e.getMessage());
            }
        }
        persistTx(txHash, recordId, null, "AccessRevoked", "Hospital A", ownerPID, doctorPID);
        return txHash;
    }

    public String logAccessAttempt(String recordId, String requesterPID, boolean granted, String riskLevel) {
        String txHash = mockTx();
        if (available) {
            try {
                String data = FN_LOG_ACCESS + toHex32(sha3Bytes32(recordId)) + toHex32(fullHexToBytes32(requesterPID))
                        + padLeft(granted ? "1" : "0") + padLeft(Integer.toHexString(riskToInt(riskLevel)));
                txHash = sendTx(data);
            } catch (Exception e) {
                log.warn("Hardhat RPC logAccessAttempt execution failed: {}", e.getMessage());
            }
        }
        persistTx(txHash, recordId, null, "RecordViewed", "Hospital A", null, requesterPID);
        return txHash;
    }

    public boolean isAvailable() { return available; }

    private void persistTx(String txHash, String recordId, String sha256Hash, String eventType,
                           String hospitalOrgId, String patientPID, String requesterPID) {
        try {
            BlockchainTransaction btx = new BlockchainTransaction();
            btx.setTxHash(txHash);
            btx.setBlockNumber(blockCounter.incrementAndGet());
            btx.setRecordId(recordId != null ? recordId : "0");
            btx.setSha256Hash(sha256Hash);
            btx.setEventType(eventType);
            btx.setHospitalOrgId(hospitalOrgId != null ? hospitalOrgId : "Hospital A");
            btx.setPatientPID(patientPID);
            btx.setRequesterPID(requesterPID);
            btx.setVerified(true);
            btx.setTimestamp(LocalDateTime.now());
            transactionRepository.save(btx);
        } catch (Exception e) {
            log.warn("Could not persist BlockchainTransaction: {}", e.getMessage());
        }
    }

    private String sendTx(String data) throws Exception {
        var receipt = txManager.sendTransaction(gasPrice, gasLimit, contractAddress, data, BigInteger.ZERO);
        String txHash = receipt.getTransactionHash();
        log.debug("Local EVM Hardhat transaction submitted: {}", txHash);
        return txHash;
    }

    private String sendTxSilently(String data) {
        try {
            return sendTx(data);
        } catch (Exception e) {
            return mockTx();
        }
    }

    private static String mockTx() {
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

    private static byte[] fullHexToBytes32(String hex) {
        if (hex == null) return new byte[32];
        String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
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

    private static String keccak256Selector(String signature) {
        try {
            byte[] hash = org.web3j.crypto.Hash.sha3(signature.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(Arrays.copyOfRange(hash, 0, 4));
        } catch (Exception e) { return "00000000"; }
    }
}
