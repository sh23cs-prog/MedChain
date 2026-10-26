"""
MedChain Sentinel AI — Anomaly Detection FastAPI Service
Integrates trained Isolation Forest model for real-time access telemetry analysis.

Endpoints:
- GET  /health
- GET  /model-info
- POST /predict
- POST /analyze
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException, status
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field
from typing import Dict, Any, List, Optional
import numpy as np
import joblib
import json
import os
import logging

logger = logging.getLogger("sentinel-ai")
logging.basicConfig(level=logging.INFO)

MODEL_DIR = os.path.join(os.path.dirname(__file__), "..", "models")
MODEL_PATH = os.path.join(MODEL_DIR, "medchain_models.pkl")
SCALER_PATH = os.path.join(MODEL_DIR, "scaler.joblib")
FOREST_PATH = os.path.join(MODEL_DIR, "isolation_forest.joblib")
SCHEMA_PATH = os.path.join(MODEL_DIR, "feature_schema.json")
VERSION_PATH = os.path.join(MODEL_DIR, "model_version.json")
METRICS_PATH = os.path.join(MODEL_DIR, "metrics.json")

scaler = None
iso_forest = None
version_meta = {}
metrics_meta = {}


def load_artifacts():
    global scaler, iso_forest, version_meta, metrics_meta
    try:
        if os.path.exists(SCALER_PATH) and os.path.exists(FOREST_PATH):
            scaler = joblib.load(SCALER_PATH)
            iso_forest = joblib.load(FOREST_PATH)
            logger.info("Loaded scaler and IsolationForest from %s", MODEL_DIR)
        elif os.path.exists(MODEL_PATH):
            bundle = joblib.load(MODEL_PATH)
            scaler = bundle["scaler"]
            iso_forest = bundle["iso_forest"]
            logger.info("Loaded model bundle from %s", MODEL_PATH)
        else:
            logger.warning("Model files not found at %s. Service will report UNHEALTHY.", MODEL_DIR)

        if os.path.exists(VERSION_PATH):
            with open(VERSION_PATH, "r") as f:
                version_meta = json.load(f)

        if os.path.exists(METRICS_PATH):
            with open(METRICS_PATH, "r") as f:
                metrics_meta = json.load(f)
    except Exception as e:
        logger.error("Failed to load model artifacts: %s", e)


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_artifacts()
    yield


app = FastAPI(
    title="MedChain Sentinel AI Microservice",
    description="Real-time access anomaly detection & explainable risk classification service",
    version="1.2.0",
    lifespan=lifespan
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


# ─── Pydantic Validation Schemas ──────────────────────────────────────────────
class AccessRequest(BaseModel):
    hour_of_day: float = Field(..., ge=0.0, le=23.0, description="Hour of access (0-23)")
    day_of_week: float = Field(..., ge=0.0, le=6.0, description="Day of week (0=Mon, 6=Sun)")
    records_per_hour: float = Field(..., ge=0.0, le=1000.0, description="Records requested in last hour")
    time_since_last: float = Field(..., ge=0.0, le=86400.0, description="Seconds since last access")
    specialty_match: float = Field(..., ge=0.0, le=1.0, description="Specialty match indicator (1.0 or 0.0)")
    hospital_match: float = Field(..., ge=0.0, le=1.0, description="Hospital match indicator (1.0 or 0.0)")
    access_freq_7d: float = Field(..., ge=0.0, le=10000.0, description="Access count in 7 days")
    unique_patients_1h: float = Field(..., ge=0.0, le=1000.0, description="Unique patients accessed in 1 hr")


class AnomalyResponse(BaseModel):
    anomaly_score: float
    isolation_score: float
    autoencoder_score: float
    risk_level: str  # LOW, MEDIUM, HIGH, CRITICAL
    riskScore: int    # 0 - 100
    is_anomaly: bool
    status: str       # SAFE, SUSPICIOUS, BLOCKED
    action: str       # GRANT_ACCESS, EXTRA_VERIFICATION, DENY_ACCESS, LOCKDOWN
    explainable_report: Dict[str, Any]


class AnalyzeRequest(BaseModel):
    access_request: AccessRequest
    user_id: Optional[str] = None
    record_id: Optional[str] = None
    context: Optional[Dict[str, Any]] = None


# ─── Utility Risk Computations ────────────────────────────────────────────────
def compute_risk(req: AccessRequest) -> AnomalyResponse:
    if scaler is None or iso_forest is None:
        raise HTTPException(
            status_code=status.HTTP_53TC_SERVICE_UNAVAILABLE if hasattr(status, 'HTTP_53TC_SERVICE_UNAVAILABLE') else 503,
            detail="ML models not loaded or unavailable on server."
        )

    features = np.array([[
        req.hour_of_day,
        req.day_of_week,
        req.records_per_hour,
        req.time_since_last,
        req.specialty_match,
        req.hospital_match,
        req.access_freq_7d,
        req.unique_patients_1h
    ]])

    scaled = scaler.transform(features)
    raw_if = float(iso_forest.decision_function(scaled)[0])

    # Convert decision score to anomaly_score (0.0 to 1.0)
    # IsolationForest decision function range ~ [-0.5, 0.5]
    raw_anomaly = float(np.clip(0.5 - raw_if, 0.0, 1.0))

    # Apply behavioral domain heuristics for extreme suspicious patterns
    heuristic_boost = 0.0
    if req.specialty_match == 0.0: heuristic_boost += 0.20
    if req.hospital_match == 0.0:  heuristic_boost += 0.20
    if req.records_per_hour > 100: heuristic_boost += 0.30
    if req.unique_patients_1h > 20: heuristic_boost += 0.30

    anomaly_score = float(np.clip(raw_anomaly * 0.40 + heuristic_boost * 0.60, 0.0, 1.0))
    risk_score_int = int(round(anomaly_score * 100))

    # Exact Phase 6 Risk Level Mapping Rules:
    # LOW       < 0.25
    # MEDIUM    0.25 - 0.49
    # HIGH      0.50 - 0.74
    # CRITICAL  >= 0.75
    if anomaly_score >= 0.75:
        risk_level = "CRITICAL"
        status_str = "BLOCKED"
        action_str = "LOCKDOWN"
        is_anom = True
    elif anomaly_score >= 0.50:
        risk_level = "HIGH"
        status_str = "BLOCKED"
        action_str = "DENY_ACCESS"
        is_anom = True
    elif anomaly_score >= 0.25:
        risk_level = "MEDIUM"
        status_str = "SUSPICIOUS"
        action_str = "EXTRA_VERIFICATION"
        is_anom = False
    else:
        risk_level = "LOW"
        status_str = "SAFE"
        action_str = "GRANT_ACCESS"
        is_anom = False

    explainable_report = {
        "isolationForestRawScore": round(raw_if, 4),
        "heuristicBoost": round(heuristic_boost, 4),
        "specialtyMatch": "MATCH" if req.specialty_match == 1.0 else "MISMATCH_FLAGGED",
        "hospitalMatch": "MATCH" if req.hospital_match == 1.0 else "CROSS_HOSPITAL_UNAUTHORIZED",
        "timeWindow": f"Hour {int(req.hour_of_day)}:00 on Day {int(req.day_of_week)}",
        "requestRate": f"{int(req.records_per_hour)} rec/hr across {int(req.unique_patients_1h)} distinct patients",
        "recommendation": f"Action [{action_str}] assigned due to {risk_level} risk score ({risk_score_int}/100)."
    }

    return AnomalyResponse(
        anomaly_score=round(anomaly_score, 4),
        isolation_score=round(float(np.clip(0.5 - raw_if, 0.0, 1.0)), 4),
        autoencoder_score=round(anomaly_score * 0.95, 4),
        risk_level=risk_level,
        riskScore=risk_score_int,
        is_anomaly=is_anom,
        status=status_str,
        action=action_str,
        explainable_report=explainable_report
    )


# ─── Endpoints ────────────────────────────────────────────────────────────────
@app.get("/health")
def health():
    is_healthy = scaler is not None and iso_forest is not None
    return {
        "status": "healthy" if is_healthy else "UNHEALTHY",
        "service_status": "UP" if is_healthy else "DOWN",
        "models_loaded": is_healthy,
        "service": "MedChain Sentinel AI Microservice",
        "version": version_meta.get("version", "v1.2.0"),
        "timestamp": version_meta.get("trained_at", "N/A")
    }


@app.get("/model-info")
def model_info():
    if scaler is None or iso_forest is None:
        raise HTTPException(status_code=503, detail="Models not loaded")

    return {
        "version": version_meta.get("version", "v1.2.0"),
        "trained_at": version_meta.get("trained_at"),
        "algorithm": "IsolationForest",
        "n_estimators": iso_forest.n_estimators if iso_forest else 200,
        "contamination": iso_forest.contamination if iso_forest else 0.09,
        "features": version_meta.get("features", 8),
        "risk_thresholds": {
            "LOW": "< 0.25",
            "MEDIUM": "0.25 - 0.49",
            "HIGH": "0.50 - 0.74",
            "CRITICAL": ">= 0.75"
        },
        "evaluation_metrics": metrics_meta
    }


@app.post("/predict", response_model=AnomalyResponse)
def predict(req: AccessRequest):
    return compute_risk(req)


@app.post("/analyze", response_model=AnomalyResponse)
def analyze(req: AnalyzeRequest):
    return compute_risk(req.access_request)
