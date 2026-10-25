package com.medchain.repository;

import com.medchain.model.MLPrediction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MLPredictionRepository extends JpaRepository<MLPrediction, String> {
    List<MLPrediction> findByUserId(String userId);
    List<MLPrediction> findByRiskLevel(String riskLevel);
    long countByAnomalyTrue();
    long countByRiskLevel(String riskLevel);
}
