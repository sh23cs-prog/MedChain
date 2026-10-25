"""
MedChain Sentinel AI — Model Evaluation Pipeline
Evaluates the trained Isolation Forest anomaly detection model on evaluation datasets
and calculates precision, recall, F1-score, ROC-AUC, and risk tier distribution metrics.
"""

import os
import json
import joblib
import numpy as np
from datetime import datetime
from sklearn.metrics import precision_score, recall_score, f1_score, roc_auc_score
from train import generate_telemetry_dataset, MODEL_DIR, SCALER_PATH, FOREST_PATH, METRICS_PATH


def evaluate():
    if not os.path.exists(SCALER_PATH) or not os.path.exists(FOREST_PATH):
        raise FileNotFoundError("Trained model files not found. Run train.py first.")

    scaler = joblib.load(SCALER_PATH)
    iso_forest = joblib.load(FOREST_PATH)

    print("Generating evaluation test dataset (1000 normal, 100 anomaly)...")
    X_test, y_true = generate_telemetry_dataset(n_normal=1000, n_anomaly=100, random_state=99)

    X_scaled = scaler.transform(X_test)
    raw_if = iso_forest.decision_function(X_scaled)

    # Convert decision score to 0.0 - 1.0 anomaly score
    # IsolationForest decision_function: lower (negative) values = anomalies
    # Transform: anomaly_score = clip(0.5 - raw_if, 0.0, 1.0)
    anomaly_scores = np.clip(0.5 - raw_if, 0.0, 1.0)

    # Predictions: 1 if anomaly_score >= 0.50 (HIGH/CRITICAL), else 0
    y_pred = (anomaly_scores >= 0.50).astype(int)

    precision = float(precision_score(y_true, y_pred, zero_division=1))
    recall = float(recall_score(y_true, y_pred, zero_division=1))
    f1 = float(f1_score(y_true, y_pred, zero_division=1))
    roc_auc = float(roc_auc_score(y_true, anomaly_scores))

    # Risk Tier Distribution
    low_count = int(np.sum(anomaly_scores < 0.25))
    med_count = int(np.sum((anomaly_scores >= 0.25) & (anomaly_scores < 0.50)))
    high_count = int(np.sum((anomaly_scores >= 0.50) & (anomaly_scores < 0.75)))
    crit_count = int(np.sum(anomaly_scores >= 0.75))

    metrics = {
        "evaluated_at": datetime.now().isoformat(),
        "total_samples": len(y_true),
        "normal_samples": int(np.sum(y_true == 0)),
        "anomaly_samples": int(np.sum(y_true == 1)),
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1_score": round(f1, 4),
        "roc_auc": round(roc_auc, 4),
        "risk_distribution": {
            "LOW (< 0.25)": low_count,
            "MEDIUM (0.25 - 0.49)": med_count,
            "HIGH (0.50 - 0.74)": high_count,
            "CRITICAL (>= 0.75)": crit_count
        }
    }

    with open(METRICS_PATH, "w") as f:
        json.dump(metrics, f, indent=2)

    print("=" * 60)
    print("MEDCHAIN SENTINEL AI MODEL EVALUATION REPORT")
    print("=" * 60)
    print(f"Total Test Samples : {len(y_true)}")
    print(f"Precision          : {precision:.4f}")
    print(f"Recall             : {recall:.4f}")
    print(f"F1-Score           : {f1:.4f}")
    print(f"ROC-AUC            : {roc_auc:.4f}")
    print("Risk Distribution  :")
    print(f"  LOW (< 0.25)        : {low_count}")
    print(f"  MEDIUM (0.25 - 0.49): {med_count}")
    print(f"  HIGH (0.50 - 0.74)  : {high_count}")
    print(f"  CRITICAL (>= 0.75)  : {crit_count}")
    print("=" * 60)
    print(f"Metrics persisted to {METRICS_PATH}")


if __name__ == "__main__":
    evaluate()
