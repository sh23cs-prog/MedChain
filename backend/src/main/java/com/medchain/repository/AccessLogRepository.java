package com.medchain.repository;

import com.medchain.model.AccessLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AccessLogRepository extends JpaRepository<AccessLog, String> {

    List<AccessLog> findByUserId(String userId);
    List<AccessLog> findByDoctorId(String doctorId);
    List<AccessLog> findByRecordId(String recordId);

    List<AccessLog> findByAnomalyScoreGreaterThan(double threshold);
    List<AccessLog> findByFlaggedTrue();
    List<AccessLog> findByGrantedFalse();

    List<AccessLog> findByUserIdAndTimestampAfter(String userId, LocalDateTime after);
    long countByUserIdAndTimestampAfter(String userId, LocalDateTime after);
    java.util.Optional<AccessLog> findTopByUserIdOrderByTimestampDesc(String userId);

    @Query("SELECT COUNT(DISTINCT a.patientPID) FROM AccessLog a WHERE a.userId = :userId AND a.timestamp > :after")
    long countDistinctPatientPIDByUserIdAndTimestampAfter(@Param("userId") String userId, @Param("after") LocalDateTime after);

    long countByGrantedFalse();
    long countByAction(String action);
}
