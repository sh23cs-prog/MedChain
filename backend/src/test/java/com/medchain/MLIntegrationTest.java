package com.medchain;

import com.medchain.model.MLAlert;
import com.medchain.model.MLPrediction;
import com.medchain.model.User;
import com.medchain.repository.MLAlertRepository;
import com.medchain.repository.MLPredictionRepository;
import com.medchain.repository.UserRepository;
import com.medchain.security.JwtUtil;
import com.medchain.service.MLService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("dev")
public class MLIntegrationTest {

    @Autowired
    private MLService mlService;

    @Autowired
    private MLPredictionRepository predictionRepository;

    @Autowired
    private MLAlertRepository alertRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private MockMvc mockMvc;

    private String adminEmail = "admin.ml.test@medchain.com";

    @BeforeEach
    void setUp() {
        if (!userRepository.existsByEmail(adminEmail)) {
            User admin = new User();
            admin.setName("ML Test Admin");
            admin.setEmail(adminEmail);
            admin.setPassword("Password123!");
            admin.setRole("ADMIN");
            admin.setEmailVerified(true);
            admin.setPseudonymId("0xadminmlpid");
            userRepository.save(admin);
        }
    }

    @Test
    @DisplayName("Phase 6: Model Training Artifact Generation Verification")
    void testModelArtifactGeneration() {
        File schemaFile = new File("ml/models/feature_schema.json");
        if (!schemaFile.exists()) schemaFile = new File("../ml/models/feature_schema.json");

        File versionFile = new File("ml/models/model_version.json");
        if (!versionFile.exists()) versionFile = new File("../ml/models/model_version.json");

        File metricsFile = new File("ml/models/metrics.json");
        if (!metricsFile.exists()) metricsFile = new File("../ml/models/metrics.json");

        assertTrue(schemaFile.exists(), "Feature schema JSON artifact must exist");
        assertTrue(versionFile.exists(), "Model version metadata JSON artifact must exist");
        assertTrue(metricsFile.exists(), "Evaluation metrics JSON artifact must exist");
    }

    @Test
    @DisplayName("Phase 6: Controlled Fixtures Risk Classification & OPEN Alert Persistence")
    void testRiskClassificationAndAlertPersistence() {
        // 1. Normal Test Fixture (Low Risk)
        MLService.AnomalyRequest lowReq = new MLService.AnomalyRequest();
        lowReq.setHour_of_day(14.0);
        lowReq.setDay_of_week(2.0);
        lowReq.setRecords_per_hour(2.0);
        lowReq.setTime_since_last(300.0);
        lowReq.setSpecialty_match(1.0);
        lowReq.setHospital_match(1.0);
        lowReq.setAccess_freq_7d(12.0);
        lowReq.setUnique_patients_1h(1.0);

        MLService.AnomalyResponse lowRes = mlService.scoreAccess(lowReq, "test_user_low", "rec_low");
        assertTrue(lowRes.getAnomaly_score() < 0.25, "Normal fixture must yield LOW risk score (<0.25)");
        assertEquals("LOW", lowRes.getRisk_level());
        assertEquals("SAFE", lowRes.getStatus());

        // 2. High/Critical Anomaly Test Fixture
        MLService.AnomalyRequest highReq = new MLService.AnomalyRequest();
        highReq.setHour_of_day(3.0);
        highReq.setDay_of_week(6.0);
        highReq.setRecords_per_hour(250.0);
        highReq.setTime_since_last(0.5);
        highReq.setSpecialty_match(0.0);
        highReq.setHospital_match(0.0);
        highReq.setAccess_freq_7d(500.0);
        highReq.setUnique_patients_1h(60.0);

        long initialAlertCount = alertRepository.countByStatus("OPEN");

        MLService.AnomalyResponse highRes = mlService.scoreAccess(highReq, "test_user_high", "rec_high");
        assertTrue(highRes.getAnomaly_score() >= 0.50, "Anomalous fixture must yield HIGH or CRITICAL risk score (>=0.50)");
        assertTrue(highRes.is_anomaly(), "Is Anomaly flag must be true");

        // Verify MLAlert persistence with status OPEN
        long newAlertCount = alertRepository.countByStatus("OPEN");
        assertTrue(newAlertCount > initialAlertCount, "HIGH/CRITICAL anomaly must automatically persist an MLAlert with status OPEN");
    }

    @Test
    @DisplayName("Phase 6: Sentinel AI Dashboard APIs & Alert Lifecycle Endpoint")
    void testSentinelDashboardEndpoints() throws Exception {
        String token = jwtUtil.generateToken(adminEmail, "ADMIN");

        // 1. GET /api/ml/stats
        mockMvc.perform(get("/api/ml/stats")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.modelVersion").value("v1.2.0"))
                .andExpect(jsonPath("$.modelHealth").exists())
                .andExpect(jsonPath("$.totalPredictions").exists())
                .andExpect(jsonPath("$.riskDistribution").exists());

        // 2. Trigger high risk alert
        MLService.AnomalyRequest highReq = new MLService.AnomalyRequest();
        highReq.setSpecialty_match(0.0);
        highReq.setHospital_match(0.0);
        highReq.setRecords_per_hour(300.0);
        highReq.setUnique_patients_1h(80.0);
        mlService.scoreAccess(highReq, "alert_test_user", "rec_alert");

        // 3. GET /api/ml/alerts
        String alertsJson = mockMvc.perform(get("/api/ml/alerts?status=OPEN")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andReturn().getResponse().getContentAsString();

        assertTrue(alertsJson.contains("OPEN"));

        // 4. Update status via POST /api/ml/alerts/{id}/status
        MLAlert alert = alertRepository.findByStatus("OPEN").stream().findFirst().orElseThrow();
        mockMvc.perform(post("/api/ml/alerts/" + alert.getId() + "/status?status=ACKNOWLEDGED")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ACKNOWLEDGED"));
    }
}
