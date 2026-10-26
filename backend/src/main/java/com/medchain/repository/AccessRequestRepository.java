package com.medchain.repository;

import com.medchain.model.AccessRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccessRequestRepository extends JpaRepository<AccessRequest, String> {
    List<AccessRequest> findByPatientId(String patientId);
    List<AccessRequest> findByPatientIdAndStatus(String patientId, String status);
    Optional<AccessRequest> findByRecordIdAndDoctorIdAndStatus(String recordId, String doctorId, String status);
    List<AccessRequest> findByRecordIdAndDoctorId(String recordId, String doctorId);
    List<AccessRequest> findByDoctorId(String doctorId);
    void deleteByPatientId(String patientId);
    void deleteByDoctorId(String doctorId);
}
