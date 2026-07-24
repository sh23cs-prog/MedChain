"""
MedChain Anomaly Detection Service
Based on: Blockchain-Based Secure Medical Data Sharing with Intelligent Risk Analysis and Explainable Anomaly Detection

Model: Isolation Forest (primary) + Autoencoder
Features: 8 behavioral features per access request
"""

from contextlib import asynccontextmanager
from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import Optional, Dict
import numpy as np
from sklearn.ensemble import IsolationForest
from sklearn.preprocessing import StandardScaler
from sklearn.neural_network import MLPRegressor
import joblib
import os
import logging

logger = logging.getLogger(__name__)

# ─── Feature Schema ────────────────────────────────────────────────────────────
# f1: hour_of_day          — 3am access is suspicious
# f2: day_of_week          — weekend bulk access is suspicious
# f3: records_per_hour     — bulk record access
# f4: time_since_last      — rapid repeated access
# f5: specialty_match      — doctor specialty matches record type (0/1)
# f6: hospital_match       — doctor and patient same hospital (0/1)
# f7: access_freq_7d       — total accesses in last 7 days
# f8: unique_patients_1h   — distinct patients accessed in last hour

FEATURE_NAMES = [
    "hour_of_day", "day_of_week", "records_per_hour", "time_since_last",
    "specialty_match", "hospital_match", "access_freq_7d", "unique_patients_1h"
]
FEATURE_MEANS  = [12.0, 3.0, 2.0, 60.0, 0.8, 0.7, 10.0, 1.5]
FEATURE_STDEVS = [4.0,  1.5, 1.0, 30.0, 0.2, 0.2,  5.0, 0.8]

MODEL_PATH = "models/medchain_models.pkl"

scaler      = None
iso_forest  = None
autoencoder = None


def train_models(X=None):
    global scaler, iso_forest, autoencoder
    np.random.seed(42)
    if X is None:
        normal = np.random.normal(loc=FEATURE_MEANS, scale=FEATURE_STDEVS, size=(1000, 8))
        normal = np.clip(normal, 0, None)
        normal[:, 4] = np.clip(normal[:, 4], 0, 1)
        normal[:, 5] = np.clip(normal[:, 5], 0, 1)
        X = normal

    scaler      = StandardScaler()
    iso_forest  = IsolationForest(contamination=0.1, n_estimators=200, random_state=42)
    autoencoder = MLPRegressor(hidden_layer_sizes=(6, 3, 6), activation='relu', max_iter=500, random_state=42)

    scaler.fit(X)
    scaled = scaler.transform(X)
    iso_forest.fit(scaled)
    autoencoder.fit(scaled, scaled)

    os.makedirs("models", exist_ok=True)
    joblib.dump({"scaler": scaler, "iso_forest": iso_forest, "autoencoder": autoencoder}, MODEL_PATH)
    return len(X)


def load_or_train():
    global scaler, iso_forest, autoencoder
    if os.path.exists(MODEL_PATH):
        try:
            models = joblib.load(MODEL_PATH)
            scaler      = models["scaler"]
            iso_forest  = models["iso_forest"]
            autoencoder = models["autoencoder"]
            logger.info("Models loaded from disk: %s", MODEL_PATH)
            return
        except Exception as e:
            logger.warning("Failed to load models from disk: %s — retraining.", e)
    logger.info("Training new models...")
    train_models()
    logger.info("Models trained and saved.")


@asynccontextmanager
async def lifespan(app: FastAPI):
    load_or_train()
    yield


app = FastAPI(title="MedChain Anomaly Detection", version="1.0.0", lifespan=lifespan)
app.add_middleware(CORSMiddleware, allow_origins=["*"], allow_methods=["*"], allow_headers=["*"])


# ─── Schemas ──────────────────────────────────────────────────────────────────
class AccessRequest(BaseModel):
    hour: float
    day_of_week: float
    records_per_hour: float
    time_since_last: float
    specialty_match: float
    hospital_match: float
    access_freq_7d: float
    unique_patients_1h: float


class AnomalyResponse(BaseModel):
    riskScore: int             # 0 - 100 integer
    status: str                # SAFE, ELEVATED, LOCKDOWN
    anomaly_score: float       # raw decimal 0.0 - 1.0
    isolation_score: float
    autoencoder_score: float
    is_anomaly: bool
    risk_level: str
    action: str
    explainable_report: Dict[str, str]


class TrainRequest(BaseModel):
    samples: Optional[list] = None


class TrainResponse(BaseModel):
    status: str
    samples_used: int
    message: str


# ─── Endpoints ────────────────────────────────────────────────────────────────
@app.post("/detect", response_model=AnomalyResponse)
def detect_anomaly(req: AccessRequest):
    if scaler is None or iso_forest is None or autoencoder is None:
        raise HTTPException(status_code=503, detail="Models not loaded. Try /train first.")

    features = np.array([[
        req.hour, req.day_of_week, req.records_per_hour,
        req.time_since_last, req.specialty_match, req.hospital_match,
        req.access_freq_7d, req.unique_patients_1h
    ]])
    scaled = scaler.transform(features)

    # Isolation Forest score
    raw_if   = iso_forest.decision_function(scaled)[0]
    score_if = float(np.clip(1.0 - (raw_if + 0.5), 0.0, 1.0))

    # Autoencoder reconstruction error
    reconstructed = autoencoder.predict(scaled)
    recon_error    = float(np.mean((scaled - reconstructed) ** 2))
    score_ae       = float(np.clip(recon_error * 10, 0.0, 1.0))

    # Combined score
    combined = 0.5 * score_if + 0.5 * score_ae
    risk_score_int = int(round(combined * 100))

    # Flowchart Tier Evaluation: SAFE, ELEVATED, LOCKDOWN
    if combined > 0.65:
        risk_level, action, status = "HIGH", "DENY_ACCESS", "LOCKDOWN"
    elif combined > 0.35:
        risk_level, action, status = "MEDIUM", "EXTRA_VERIFICATION", "ELEVATED"
    else:
        risk_level, action, status = "LOW", "GRANT_ACCESS", "SAFE"

    # Explainable AI Analysis Report
    explainable_report = {
        "isolationForestAnalysis": f"Tree decision score: {round(score_if, 3)} (Outlier probability)",
        "autoencoderAnalysis": f"Neural reconstruction error: {round(recon_error, 4)} (Pattern deviation)",
        "specialtyMatch": "Verified Doctor Specialty Matches Record Domain" if req.specialty_match == 1 else "Specialty Mismatch Flagged",
        "timeAnomaly": "Access during normal operating hours" if 7 <= req.hour <= 20 else f"Off-hours access detected ({int(req.hour)}:00)",
        "frequencyCheck": f"Access rate normal ({int(req.records_per_hour)} rec/hr)" if req.records_per_hour < 5 else f"Elevated request rate ({int(req.records_per_hour)} rec/hr)",
        "decisionRationale": f"Status [{status}] assigned based on combined risk score of {risk_score_int}/100. Action: {action}."
    }

    return AnomalyResponse(
        riskScore=risk_score_int,
        status=status,
        anomaly_score=round(combined, 4),
        isolation_score=round(score_if, 4),
        autoencoder_score=round(score_ae, 4),
        is_anomaly=combined > 0.65,
        risk_level=risk_level,
        action=action,
        explainable_report=explainable_report
    )


@app.post("/train", response_model=TrainResponse)
def retrain(req: TrainRequest):
    try:
        X = np.array(req.samples) if req.samples else None
        n = train_models(X)
        return TrainResponse(status="success", samples_used=n, message="Models retrained and saved")
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@app.get("/health")
def health():
    return {
        "status": "running",
        "models": ["IsolationForest", "Autoencoder"],
        "models_loaded": scaler is not None,
        "features": 8,
        "flowchart_tiers": ["SAFE", "ELEVATED", "LOCKDOWN"],
        "version": "1.1.0"
    }


@app.get("/model-info")
def model_info():
    if iso_forest is None or autoencoder is None:
        raise HTTPException(status_code=503, detail="Models not loaded")
    return {
        "isolation_forest": {
            "n_estimators": iso_forest.n_estimators,
            "contamination": iso_forest.contamination
        },
        "autoencoder": {
            "hidden_layers": autoencoder.hidden_layer_sizes,
            "activation": autoencoder.activation
        },
        "features": FEATURE_NAMES,
        "flowchart_thresholds": {"LOCKDOWN": 0.65, "ELEVATED": 0.35, "SAFE": 0.0}
    }
