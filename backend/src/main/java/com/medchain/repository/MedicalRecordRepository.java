package com.medchain.repository;

import com.medchain.model.MedicalRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MedicalRecordRepository extends JpaRepository<MedicalRecord, String> {
    List<MedicalRecord> findByPatientId(String patientId);

    @Query("SELECT r FROM MedicalRecord r JOIN r.authorizedDoctorIds d WHERE d = :doctorId")
    List<MedicalRecord> findByAuthorizedDoctorIdsContaining(@Param("doctorId") String doctorId);

    long countByPatientId(String patientId);
    Optional<MedicalRecord> findBySha256Hash(String sha256Hash);
}
