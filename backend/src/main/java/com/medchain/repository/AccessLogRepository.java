package com.medchain.repository;

import com.medchain.model.AccessLog;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * MongoDB repository for AccessLog entries.
 * Each log entry represents an on-chain-auditable access event.
 */
@Repository
public interface AccessLogRepository extends MongoRepository<AccessLog, String> {

    // ── User/Doctor queries ──────────────────────────────────────────────────
    List<AccessLog> findByUserId(String userId);
    List<AccessLog> findByDoctorId(String doctorId);

    // ── Record queries ────────────────────────────────────────────────────────
    List<AccessLog> findByRecordId(String recordId);

    // ── Anomaly detection queries ─────────────────────────────────────────────
    List<AccessLog> findByAnomalyScoreGreaterThan(double threshold);
    List<AccessLog> findByFlaggedTrue();
    List<AccessLog> findByGrantedFalse();

    // ── Time-based queries (for ML feature computation) ───────────────────────
    List<AccessLog> findByUserIdAndTimestampAfter(String userId, LocalDateTime after);
    long countByUserIdAndTimestampAfter(String userId, LocalDateTime after);
    long countDistinctPatientPIDByUserIdAndTimestampAfter(String userId, LocalDateTime after);

    // ── Count helpers ─────────────────────────────────────────────────────────
    long countByGrantedFalse();
    long countByAction(String action);
}
