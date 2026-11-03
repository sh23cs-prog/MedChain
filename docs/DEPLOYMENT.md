# MedChain — Deployment & Operations Guide

## 1. Overview

MedChain is engineered for multi-environment execution:
- **Local Demonstration (Docker Compose)**: Complete 5-tier architecture (`frontend`, `backend`, `sentinel-ml`, `blockchain`, `postgres`) orchestrated locally via Docker.
- **Production Cloud Deployment**: Publicly deployed on **Render** (Docker Web Services) and **Vercel** (React Edge Network).

---

## 2. Local Docker Deployment (Single Command)

### 2.1. Prerequisites
- **Docker Desktop** (version 24.0+ recommended with Docker Compose v2).
- **Git** (for repository cloning).

> [!IMPORTANT]
> **Docker is the ONLY host-level prerequisite.** You do NOT need to install Java, Maven, Python, Node.js, PostgreSQL, or Hardhat on your host machine. Everything runs containerized inside Docker.

### 2.2. Step-by-Step Execution

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/sh23cs-prog/MedChain.git
   cd MedChain
   ```

2. **Initialize Environment Variables**:
   ```bash
   # Copy template to .env
   cp .env.example .env      # Linux/macOS
   copy .env.example .env    # Windows Command Prompt
   Copy-Item .env.example .env # Windows PowerShell
   ```

3. **Build and Launch All Containers**:
   ```bash
   docker compose up --build
   ```
   *(To run in detached background mode, append `-d`: `docker compose up --build -d`)*

### 2.3. Service Endpoints & Health Status

Once startup completes, all services are accessible on your host machine:

| Container / Service | Port | Host URL | Health Endpoint |
|---|---|---|---|
| **React Frontend SPA** | `3000` | [http://localhost:3000](http://localhost:3000) | `http://localhost:3000/` |
| **Spring Boot Backend** | `8080` | [http://localhost:8080](http://localhost:8080) | [http://localhost:8080/actuator/health](http://localhost:8080/actuator/health) |
| **FastAPI Sentinel AI** | `8000` | [http://localhost:8000](http://localhost:8000) | [http://localhost:8000/health](http://localhost:8000/health) |
| **Hardhat EVM Blockchain** | `8545` | [http://localhost:8545](http://localhost:8545) | JSON-RPC `eth_blockNumber` |
| **PostgreSQL 16 Database** | `5432` | `localhost:5432` | `pg_isready` |
| **Interactive Swagger UI** | `8080` | [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) | OpenAPI 3.0 UI |

### 2.4. Verify Startup & Container Health

Run the following command to check that all services are healthy:
```bash
docker compose ps
```

Expected Output:
```text
NAME                     IMAGE                   STATUS                  PORTS
medchain-postgres        postgres:16-alpine      Up (healthy)            0.0.0.0:5432->5432/tcp
medchain-blockchain      medchain-blockchain     Up (healthy)            0.0.0.0:8545->8545/tcp
medchain-sentinel-ml     medchain-sentinel-ml    Up (healthy)            0.0.0.0:8000->8000/tcp
medchain-backend         medchain-backend        Up (healthy)            0.0.0.0:8080->8080/tcp
medchain-frontend        medchain-frontend       Up                      0.0.0.0:3000->80/tcp
```

### 2.5. Stopping & Cleaning Up
```bash
# Stop all containers
docker compose down

# Stop containers and delete persistent volumes
docker compose down -v
```

---

## 3. Demo Credentials (Pre-Seeded)

The system automatically initializes standard clinical demo accounts on startup:

| Role | Name | Email Address | Password | Attributes / Specialization |
|---|---|---|---|---|
| **Administrator** | Admin User | `admin@medchain.com` | `Password123!` | System Oversight & User Management |
| **Patient** | Alice Johnson | `patient@medchain.com` | `Password123!` | Cardiology & Radiology Records |
| **Patient** | Bob Williams | `bob@medchain.com` | `Password123!` | Orthopedics & Oncology Records |
| **Doctor** | Dr. Robert Smith | `doctor@medchain.com` | `Password123!` | Specialty: `Cardiology`, Hospital: `CityHospital`, License: `Senior` |
| **Doctor** | Dr. Emily Chen | `emily@medchain.com` | `Password123!` | Specialty: `Radiology`, Hospital: `CityHospital`, License: `Senior` |
| **Doctor** | Dr. James Wilson | `james@medchain.com` | `Password123!` | Specialty: `Neurology`, Hospital: `CityHospital`, License: `Senior` |
| **Doctor** | Dr. Sarah Patel | `sarah@medchain.com` | `Password123!` | Specialty: `Orthopedics`, Hospital: `MedicalCenter`, License: `Junior` |

---

## 4. Production Cloud Deployment (Render & Vercel)

### 4.1. Architecture in Production
- **Frontend SPA**: Hosted on **Vercel** (`https://med-chain-amber.vercel.app`) with edge caching and SSL.
- **Backend API**: Hosted on **Render** as a Docker Web Service (`https://medchain-backend-kgmq.onrender.com`).
- **Sentinel AI**: Hosted on **Render** as a Docker Web Service (`https://medchain-sentinel-ml.onrender.com`).
- **Database**: Managed PostgreSQL on Render with connection pooling.
- **Email Delivery**: Resend HTTPS API / Brevo HTTPS API over port 443 (bypassing cloud provider SMTP firewall limitations).

### 4.2. Render Environment Variables Configuration
In your Render Dashboard for `medchain-backend`, configure:

```bash
SPRING_PROFILES_ACTIVE=prod
DATABASE_URL=jdbc:postgresql://<render-postgres-host>:5432/<dbname>
SPRING_DATASOURCE_USERNAME=<db-user>
SPRING_DATASOURCE_PASSWORD=<db-password>
JWT_SECRET=<min-256-bit-secret>
AES_SECRET_KEY=<32-byte-secret>
ML_SERVICE_URL=https://medchain-sentinel-ml.onrender.com
CORS_ALLOWED_ORIGINS=https://med-chain-amber.vercel.app,https://*.vercel.app,http://localhost:3000
BLOCKCHAIN_ENABLED=false
MAIL_PROVIDER=AUTO
SPRING_MAIL_FROM=onboarding@resend.dev
RESEND_API_KEY=re_your_api_key_here
BREVO_API_KEY=xkeysib-your_brevo_key_here
BREVO_FROM_EMAIL=your_verified_sender@domain.com
```

### 4.3. Vercel Environment Variables Configuration
In your Vercel Project Settings for `med-chain-amber`:

```bash
REACT_APP_API_URL=https://medchain-backend-kgmq.onrender.com/api
REACT_APP_MOCK_MODE=false
```
