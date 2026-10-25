package com.medchain.repository;

import com.medchain.model.MLAlert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MLAlertRepository extends JpaRepository<MLAlert, String> {
    List<MLAlert> findByUserId(String userId);
    List<MLAlert> findByStatus(String status);
    long countByStatus(String status);
}
