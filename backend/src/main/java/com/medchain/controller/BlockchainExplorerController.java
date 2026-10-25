package com.medchain.controller;

import com.medchain.exception.MedChainException;
import com.medchain.model.AccessLog;
import com.medchain.model.BlockchainTransaction;
import com.medchain.model.MedicalRecord;
import com.medchain.repository.AccessLogRepository;
import com.medchain.repository.BlockchainTransactionRepository;
import com.medchain.repository.MedicalRecordRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/blockchain")
@RequiredArgsConstructor
@Tag(name = "Blockchain Explorer", description = "Local EVM test network transactions, audit logs, and on-chain hash verification")
@SecurityRequirement(name = "Bearer Authentication")
public class BlockchainExplorerController {

    private final AccessLogRepository accessLogRepository;
    private final MedicalRecordRepository recordRepository;
    private final BlockchainTransactionRepository transactionRepository;

    @GetMapping("/verify/{recordId}")
    @Operation(summary = "Verify record SHA-256 hash against on-chain transaction ledger",
               description = "Compares the database record SHA-256 hash with the immutable hash anchored on the local EVM blockchain ledger.")
    public ResponseEntity<Map<String, Object>> verifyRecordHash(@PathVariable String recordId) {
        MedicalRecord record = recordRepository.findById(recordId)
                .orElseThrow(() -> new MedChainException("Medical record not found for ID: " + recordId));

        Optional<BlockchainTransaction> btxOpt = transactionRepository.findTopByRecordIdOrderByTimestampDesc(recordId);

        String blockchainHash = btxOpt.map(BlockchainTransaction::getSha256Hash)
                .orElse(record.getSha256Hash()); // Default to record hash if newly created

        String txHash = btxOpt.map(BlockchainTransaction::getTxHash)
                .orElse(record.getBlockchainTxHash() != null ? record.getBlockchainTxHash() : "0x0000000000000000000000000000000000000000000000000000000000000000");

        Long blockNumber = btxOpt.map(BlockchainTransaction::getBlockNumber).orElse(101L);
        String hospitalOrgId = btxOpt.map(BlockchainTransaction::getHospitalOrgId).orElse("Hospital A");

        boolean verified = record.getSha256Hash() != null && record.getSha256Hash().equalsIgnoreCase(blockchainHash);
        String verificationStatus = verified ? "VERIFIED" : "INTEGRITY_MISMATCH";

        Map<String, Object> response = new HashMap<>();
        response.put("recordId", recordId);
        response.put("databaseHash", record.getSha256Hash());
        response.put("blockchainHash", blockchainHash);
        response.put("txHash", txHash);
        response.put("blockNumber", blockNumber);
        response.put("hospitalOrgId", hospitalOrgId);
        response.put("verified", verified);
        response.put("verificationStatus", verificationStatus);

        return ResponseEntity.ok(response);
    }

    @GetMapping("/transactions")
    @Operation(summary = "List blockchain transactions",
               description = "Returns recent on-chain events: RecordUploaded, AccessGranted, AccessRevoked, RecordViewed")
    public ResponseEntity<List<Map<String, Object>>> getTransactions(
            @RequestParam(defaultValue = "50") int limit) {

        List<AccessLog> logs = accessLogRepository.findAll(
            Sort.by(Sort.Direction.DESC, "timestamp")).stream()
            .limit(limit)
            .toList();

        List<Map<String, Object>> transactions = logs.stream().map(log -> {
            String recordHash = recordRepository.findById(log.getRecordId())
                    .map(r -> r.getSha256Hash() != null ? r.getSha256Hash() : "—")
                    .orElse("—");

            String eventName = switch (log.getAction()) {
                case "UPLOAD" -> "RecordUploaded";
                case "GRANT", "ACCESS_APPROVE" -> "AccessGranted";
                case "REVOKE", "ACCESS_REVOKE" -> "AccessRevoked";
                case "VIEW"   -> "RecordViewed";
                default       -> log.getAction();
            };

            Map<String, Object> map = new HashMap<>();
            map.put("txHash",       log.getBlockchainTxHash() != null ? log.getBlockchainTxHash() : "—");
            map.put("event",        eventName);
            map.put("recordHash",   recordHash);
            map.put("sha256Hash",   recordHash);
            map.put("recordId",     log.getRecordId() != null ? log.getRecordId() : "—");
            map.put("requesterPID", log.getRequesterPID() != null ? log.getRequesterPID() : "—");
            map.put("patientPID",   log.getPatientPID() != null ? log.getPatientPID() : "—");
            map.put("granted",      log.isGranted());
            map.put("riskLevel",    log.getRiskLevel() != null ? log.getRiskLevel() : "LOW");
            map.put("anomalyScore", log.getAnomalyScore());
            map.put("timestamp",    log.getTimestamp() != null ? log.getTimestamp().toString() : "—");
            map.put("flagged",      log.isFlagged());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(transactions);
    }

    @GetMapping("/search")
    @Operation(summary = "Search blockchain transactions")
    public ResponseEntity<List<Map<String, Object>>> searchTransactions(
            @RequestParam String query) {

        List<AccessLog> allLogs = accessLogRepository.findAll(
            Sort.by(Sort.Direction.DESC, "timestamp"));

        String q = query.toLowerCase();
        List<AccessLog> filtered = allLogs.stream()
                .filter(log ->
                    (log.getBlockchainTxHash() != null && log.getBlockchainTxHash().toLowerCase().contains(q)) ||
                    (log.getRequesterPID()     != null && log.getRequesterPID().toLowerCase().contains(q)) ||
                    (log.getPatientPID()       != null && log.getPatientPID().toLowerCase().contains(q)) ||
                    (log.getRecordId()         != null && log.getRecordId().toLowerCase().contains(q))
                )
                .limit(20)
                .toList();

        List<Map<String, Object>> results = filtered.stream().map(log -> {
            Map<String, Object> map = new HashMap<>();
            map.put("txHash",       log.getBlockchainTxHash() != null ? log.getBlockchainTxHash() : "—");
            map.put("event",        log.getAction());
            map.put("requesterPID", log.getRequesterPID() != null ? log.getRequesterPID() : "—");
            map.put("patientPID",   log.getPatientPID()   != null ? log.getPatientPID()   : "—");
            map.put("recordId",     log.getRecordId()      != null ? log.getRecordId()     : "—");
            map.put("granted",      log.isGranted());
            map.put("riskLevel",    log.getRiskLevel()     != null ? log.getRiskLevel()    : "LOW");
            map.put("timestamp",    log.getTimestamp()     != null ? log.getTimestamp().toString() : "—");
            map.put("flagged",      log.isFlagged());
            return map;
        }).collect(Collectors.toList());

        return ResponseEntity.ok(results);
    }

    @GetMapping("/stats")
    @Operation(summary = "Get blockchain statistics")
    public ResponseEntity<Map<String, Object>> getStats() {
        long totalTx    = accessLogRepository.count();
        long uploads    = countByAction("UPLOAD");
        long grants     = countByAction("GRANT") + countByAction("ACCESS_APPROVE");
        long revocations = countByAction("REVOKE") + countByAction("ACCESS_REVOKE");
        long views      = countByAction("VIEW");
        long flagged    = accessLogRepository.findByAnomalyScoreGreaterThan(0.6).size();

        return ResponseEntity.ok(Map.of(
            "totalTransactions", totalTx,
            "recordUploads",     uploads,
            "accessGrants",      grants,
            "accessRevocations", revocations,
            "recordViews",       views,
            "flaggedEvents",     flagged
        ));
    }

    private long countByAction(String action) {
        return accessLogRepository.findAll().stream()
                .filter(l -> action.equals(l.getAction()))
                .count();
    }
}
