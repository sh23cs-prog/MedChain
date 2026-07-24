# MedChain — Privacy-Preserving Medical Data Sharing Platform

> **IEEE Research Prototype & Healthcare Demonstration**  
> Based on the paper: *"Blockchain-Aided Privacy-Preserving Medical Data Sharing Scheme for E-Healthcare System"*

---

## 🏛️ System Architecture Overview

```
┌────────────────────────────────────────────────────────────────────────┐
│  CLIENT LAYER (React 18 + Material-UI)                                 │
│  Patient Portal · Doctor Portal · Admin Portal · Explorer · Monitoring │
└───────────────────────────────────┬────────────────────────────────────┘
                                    │ HTTPS + JWT Bearer
┌───────────────────────────────────▼────────────────────────────────────┐
│  SPRING BOOT 3 BACKEND (Port 8080)                                     │
│  Auth · Records · AES-256 Pipeline · CP-ABE · PRE · PDF/CSV Reports   │
└────────┬──────────────┬───────────────────┬───────────────────┬────────┘
         │              │                   │                   │
  ┌──────▼─────┐  ┌─────▼─────┐       ┌─────▼─────┐       ┌─────▼──────────────┐
  │ Ethereum   │  │   IPFS    │       │  MongoDB  │       │  Python FastAPI ML │
  │ Hardhat    │  │ (Kubo)    │       │   7.0     │       │  Isolation Forest  │
  │ (Port 8545)│  │(Port 5001)│       │(Port 27017│       │  (Port 8000)       │
  └────────────┘  └───────────┘       └───────────┘       └────────────────────┘
```

---

## 🔐 Key Security Concepts & Technologies

1. **AES-256-GCM Encryption**: Files are encrypted with 256-bit AES in Galois/Counter Mode for confidentiality and integrity before IPFS upload.
2. **CP-ABE (Ciphertext-Policy Attribute-Based Encryption)**: Enforces access control policies like `"specialty:CARDIOLOGY AND hospital:City Hospital"`.
3. **Proxy Re-Encryption (PRE)**: Allows key transformation so authorized doctors can decrypt records without revealing master private keys.
4. **SHA3-256 Identity Pseudonymisation**: Real identities remain strictly off-chain; only cryptographically generated pseudonyms (PIDs) exist on the ledger.
5. **Isolation Forest Anomaly Scoring**: Real-time behavioral scoring evaluating 8 access features (time of access, request frequency, hospital match, etc.).
6. **Ethereum Immutable Audit Trail**: Smart contract events (`RecordUploaded`, `AccessGranted`, `AccessRevoked`, `RecordViewed`) record all system actions.

---

## 🚀 Quick Start (Single Command)

Run the full 6-service stack via Docker Compose:

```bash
docker-compose up --build
```

### Access Ports
- **Frontend SPA**: `http://localhost:3000`
- **Swagger API Docs**: `http://localhost:8080/swagger-ui.html`
- **FastAPI ML Service**: `http://localhost:8000/health`
- **Ethereum Node**: `http://localhost:8545`
- **IPFS Kubo Node**: `http://localhost:5001`

---

## 👥 Demo Credentials

| Role | Email | Password | Attribute Set |
|------|-------|----------|---------------|
| **Patient (Alice)** | `patient@medchain.com` | `patient123` | N/A |
| **Doctor (Robert)** | `doctor@medchain.com` | `doctor123` | `specialty: CARDIOLOGY` |
| **Doctor (Emily)** | `emily@medchain.com` | `doctor123` | `specialty: NEUROLOGY` |
| **Admin** | `admin@medchain.com` | `admin123` | `role: ADMIN` |

---

## 📋 Comprehensive Feature Checklist

- [x] **Material-UI Healthcare Theme**: Modern palette (`#1976D2` primary, `#26A69A` secondary), 12px rounded cards, dark/light mode toggle.
- [x] **Landing Page**: Hero section, system features, workflow stepper, and tech stack chips.
- [x] **Patient Portal**: File upload, AES-256 + IPFS pipeline, grant/revoke doctor access, access request approvals.
- [x] **Doctor Portal**: Authorized records table, access request modal, view record drawer with real-time **RiskScoreGauge** and decrypted file renderer.
- [x] **Admin Portal**: 6 KPI cards, Recharts access velocity area chart, user management block/unblock, access log anomaly table, PDF & CSV export buttons.
- [x] **System Health Monitoring**: Live latency indicators (Encryption time, Blockchain consensus, ML scoring), CPU/Memory gauges, subsystem status grid.
- [x] **Blockchain Explorer**: Search transactions by hash or PID, filter by event type (`RecordUploaded`, `AccessGranted`, `AccessRevoked`, `RecordViewed`).
- [x] **Swagger UI Integration**: Full OpenAPI 3.0 specification with interactive JWT Bearer authentication.

---

## 📁 Repository Structure

```
MedChain/
├── backend/                       Spring Boot 3 + Java 17
│   ├── src/main/java/com/medchain/
│   │   ├── config/                SecurityConfig, SwaggerConfig
│   │   ├── controller/            Admin, Auth, Record, Metrics, Blockchain, Report
│   │   ├── dto/                   Request & Response DTOs
│   │   ├── model/                 MedicalRecord, User, AccessLog
│   │   ├── repository/            MongoDB Repositories
│   │   ├── security/              JWT Filter & Token Utility
│   │   └── service/               AESEncryption, IPFSService, CPABEService, MLService
├── frontend/                      React 18 + Material-UI v5
│   └── src/
│       ├── components/shared/     Navbar, ConfirmDialog, RiskScoreGauge, SkeletonCard
│       ├── context/               AuthContext, ThemeContext
│       ├── pages/                 Landing, Login, Register, Patient, Doctor, Admin, Monitoring, BlockchainExplorer
│       ├── services/              api.js (Axios + Mock mode)
│       └── theme/                 theme.js (MUI Custom Palette & Components)
├── ml/                            Python 3.11 + FastAPI + Scikit-learn
│   └── api/anomaly_service.py     Isolation Forest Anomaly Scoring
├── blockchain/                    Solidity 0.8.19 + Hardhat
│   └── contracts/MedicalRecords.sol MedChain Smart Contract
├── docs/                          API & Installation documentation
└── docker-compose.yml             6-container orchestration file
```

---

## 📜 Documentation Links

- [Comprehensive API Specification](file:///d:/Coding/MedChain/docs/API.md)
- [Installation & Deployment Guide](file:///d:/Coding/MedChain/docs/INSTALLATION.md)
- [Architecture Technical Overview](file:///d:/Coding/MedChain/docs/architecture/ARCHITECTURE.md)
