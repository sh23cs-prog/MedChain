package com.medchain.repository;

import com.medchain.model.AccessRequest;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends MongoRepository<AccessRequest, String> {
    List<AccessRequest> findByPatientIdAndStatus(String patientId, String status);
    Optional<AccessRequest> findByRecordIdAndDoctorIdAndStatus(String recordId, String doctorId, String status);
    List<AccessRequest> findByRecordIdAndDoctorId(String recordId, String doctorId);
}
