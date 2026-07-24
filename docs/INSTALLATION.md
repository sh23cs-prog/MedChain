# MedChain — Installation & Deployment Guide

## Prerequisites

- **Docker Desktop** (version 24.0+ recommended with Docker Compose v2)
- **Node.js** 18+ (for manual frontend/blockchain setup)
- **Java 17 JDK** + **Maven 3.9+** (for manual backend setup)
- **Python 3.11+** (for manual ML service setup)

---

## Option A — Docker Compose (Recommended for IEEE Demo)

To launch the complete 6-container platform in a single command:

```bash
docker-compose up --build
```

### Container Stack Status
When all services complete startup:

| Service | Port | Endpoint / Health |
|---------|------|-------------------|
| **React Frontend** | 3000 | `http://localhost:3000` |
| **Spring Boot Backend** | 8080 | `http://localhost:8080/swagger-ui.html` |
| **Python FastAPI ML Engine** | 8000 | `http://localhost:8000/health` |
| **Hardhat Ethereum Node** | 8545 | `http://localhost:8545` |
| **IPFS Kubo Node** | 5001 / 8081 | `http://localhost:5001/api/v0/id` |
| **MongoDB 7.0 Database** | 27017 | `mongodb://localhost:27017/medchain` |

---

## Option B — Manual Step-by-Step Setup

### 1. MongoDB Database
```bash
mongod --dbpath /data/db
```

### 2. Hardhat Ethereum Node
```bash
cd blockchain
npm install
npx hardhat node --hostname 0.0.0.0
# In a second terminal:
npx hardhat run scripts/deploy.js --network localhost
```

### 3. Python FastAPI ML Service
```bash
cd ml
pip install -r requirements.txt
uvicorn api.anomaly_service:app --reload --port 8000
```

### 4. Spring Boot Backend
```bash
cd backend
mvn spring-boot:run
```

### 5. React Frontend
```bash
cd frontend
npm install
npm start
```

---

## Demo Accounts

| Role | Email | Password | Attribute Policy |
|------|-------|----------|------------------|
| **Patient** | `patient@medchain.com` | `patient123` | N/A |
| **Doctor** | `doctor@medchain.com` | `doctor123` | `specialty: CARDIOLOGY` |
| **Admin** | `admin@medchain.com` | `admin123` | `role: ADMIN` |
