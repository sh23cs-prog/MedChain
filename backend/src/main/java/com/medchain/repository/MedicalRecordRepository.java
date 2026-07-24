package com.medchain.repository;

import com.medchain.model.MedicalRecord;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MedicalRecordRepository extends MongoRepository<MedicalRecord, String> {
    List<MedicalRecord> findByPatientId(String patientId);
    List<MedicalRecord> findByAuthorizedDoctorIdsContaining(String doctorId);
    long countByPatientId(String patientId);
}
