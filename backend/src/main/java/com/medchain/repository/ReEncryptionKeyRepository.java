package com.medchain.repository;

import com.medchain.model.ReEncryptionKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReEncryptionKeyRepository extends JpaRepository<ReEncryptionKey, String> {
    Optional<ReEncryptionKey> findByRecordIdAndDoctorIdAndActiveTrue(String recordId, String doctorId);
    List<ReEncryptionKey> findByDoctorId(String doctorId);
    void deleteByRecordIdAndDoctorId(String recordId, String doctorId);
}
