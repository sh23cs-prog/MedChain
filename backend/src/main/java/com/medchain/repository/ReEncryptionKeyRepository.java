package com.medchain.repository;

import com.medchain.model.ReEncryptionKey;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ReEncryptionKeyRepository extends MongoRepository<ReEncryptionKey, String> {
    Optional<ReEncryptionKey> findByRecordIdAndDoctorIdAndActiveTrue(String recordId, String doctorId);
    List<ReEncryptionKey> findByDoctorId(String doctorId);
    void deleteByRecordIdAndDoctorId(String recordId, String doctorId);
}
