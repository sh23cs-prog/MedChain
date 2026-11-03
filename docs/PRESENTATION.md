# MedChain: Blockchain-Aided Privacy-Preserving Medical Data Sharing with AI Anomaly Detection

## Final College Project Presentation Deck (15 Slides)

---

### Slide 1: Title Slide
- **Project Title**: **MedChain**
- **Subtitle**: A Blockchain-Aided Privacy-Preserving Medical Data Sharing Scheme with AI Threat Detection
- **Academic Domain**: Healthcare Informatics, Applied Cryptography, Distributed Systems & Machine Learning
- **Institution**: CMR Institute of Technology (CMRIT), Department of Computer Science & Engineering
- **Team**: Final Year Project Batch 2026
- **Live URLs**:
  - Frontend SPA: `https://med-chain-amber.vercel.app`
  - Backend API: `https://medchain-backend-kgmq.onrender.com`
  - Sentinel AI: `https://medchain-sentinel-ml.onrender.com`
  - GitHub: `https://github.com/sh23cs-prog/MedChain`

---

### Slide 2: Introduction
- **The Healthcare Data Paradox**: Modern digital healthcare generates petabytes of Electronic Health Records (EHRs), but clinical sharing between hospitals remains hindered by privacy and compliance roadblocks.
- **Regulatory Framework**: Compliance with **HIPAA** (Health Insurance Portability and Accountability Act) and **GDPR** mandates patient consent, strict access control, and complete auditability.
- **The MedChain Vision**: A decentralized, patient-centric ecosystem where patients maintain absolute ownership over their medical records, doctors access data on a verifiable need-to-know basis, and AI continuously polices for insider threats.

---

### Slide 3: Problem Statement
1. **Centralized Vulnerabilities**: Traditional hospital EHR servers represent single points of failure vulnerable to ransomware, data tampering, and outage.
2. **Patient Disenfranchisement**: Patients have zero visibility into who accesses their medical records, when, or why.
3. **Insider Threats & Unauthorized Harvesting**: Credential theft or compromised medical personnel can harvest bulk patient histories without triggering static rule alarms.
4. **Data Breaches & Identity Tracking**: Storing plain medical files on public cloud providers or recording real patient identities on public blockchains destroys patient anonymity.

---

### Slide 4: Project Objectives
- **Objective 1 — Patient-Centric Access Control**: Empower patients to define fine-grained cryptographic access policies using **Ciphertext-Policy Attribute-Based Encryption (CP-ABE)**.
- **Objective 2 — Cryptographic Confidentiality & Integrity**: Secure all medical records off-chain using **AES-256-GCM** encryption with automated **SHA-256** digest verification.
- **Objective 3 — Identity Privacy on Blockchain**: Decouple real identities from ledger transactions using cryptographic **blinding nonces and Keccak-256 pseudonyms (PIDs)**.
- **Objective 4 — Immutable Auditability**: Anchor every upload, grant, revoke, and view transaction on an **EVM smart contract ledger**.
- **Objective 5 — Real-Time Anomaly Detection**: Integrate **Sentinel AI (Isolation Forest)** to evaluate access telemetry in $<15\text{ms}$ and prevent data exfiltration.

---

### Slide 5: Existing System vs. Proposed System

| Dimension | Existing Hospital Systems | Proposed MedChain Solution |
|---|---|---|
| **Data Custody** | Centralized hospital database (Siloed) | Decentralized, Patient-Centric Ownership |
| **Encryption Model** | Storage-at-rest only; accessible to DB admins | End-to-End AES-256-GCM + CP-ABE (Zero Trust) |
| **Access Enforcement** | Basic Role-Based Access Control (RBAC) | Fine-Grained Attribute-Based Access Control (ABAC) |
| **Audit Trail** | Mutable internal server logs | Immutable, Non-Repudiable Blockchain Ledger |
| **Identity Privacy** | Real patient IDs exposed in logs | Cryptographic Pseudonyms (PIDs) on Ledger |
| **Threat Detection** | Post-incident manual log inspection | Real-Time Sentinel AI Anomaly Scoring ($<15\text{ms}$) |

---

### Slide 6: Proposed System Overview
- **Hybrid Multi-Tier Architecture**:
  - **Decentralized Storage**: High-security off-chain persistence storing AES-256-GCM encrypted payloads in PostgreSQL `BYTEA` and dedicated volumes.
  - **On-Chain Audit Ledger**: Smart contracts track state transitions, pseudonym registrations, and cryptographic policy hashes without storing raw PHI.
  - **Proxy Re-Encryption (PRE)**: Enables patients to delegate access to doctors without exposing their master private keys.
  - **Multi-Channel Transactional MFA**: 6-digit BCrypt-hashed OTPs via Resend & Brevo HTTPS APIs (bypassing cloud SMTP restrictions).

---

### Slide 7: System Architecture
```
[React 18 + Material-UI Frontend] 
         │ (HTTPS / JWT Bearer)
[Nginx Reverse Proxy / Load Balancer]
         │ (Forwarded Requests)
[Spring Boot 3 Core Backend]
   ├── AES-256-GCM Cryptographic Engine
   ├── CP-ABE Access Policy Parser
   ├── Multi-Provider Email Engine (Resend/Brevo/SMTP)
   └── Identity Pseudonymization Service (Keccak-256)
   ├───► [PostgreSQL 16 Database] (Relational Store & Encrypted Payloads)
   ├───► [Sentinel AI Microservice] (FastAPI + Isolation Forest on Port 8000)
   └───► [Hardhat EVM Blockchain Node] (Smart Contracts on Port 8545)
```

---

### Slide 8: Technology Stack

- **Frontend**: React 18, Material-UI v5, Recharts, Axios, Notistack, Emotion CSS
- **Backend**: Spring Boot 3.5.3, Java 17, Spring Security 6, Spring Data JPA, Hibernate 6, Web3j
- **Database**: PostgreSQL 16 (Production), H2 Database (Testing), HikariCP
- **AI / Machine Learning**: Python 3.11, FastAPI, Uvicorn, Scikit-Learn 1.9, NumPy 2.4, Joblib
- **Blockchain**: Solidity 0.8.20, Hardhat, Ethers.js, Web3j JSON-RPC
- **Cloud & DevOps**: Docker, Docker Compose v2, Nginx, Render (Docker Web Service), Vercel

---

### Slide 9: Module Description
1. **Authentication & Identity Module**: Multi-factor registration with 6-digit OTP, BCrypt hashing, JWT issuance, and Keccak-256 pseudonym generation.
2. **Patient Management Module**: Medical file upload, automated AES-256-GCM encryption, CP-ABE policy definition, and doctor authorization delegation.
3. **Doctor Clinical Module**: View authorized records, inspect decrypted medical reports, submit emergency access requests, and review clinical histories.
4. **Sentinel AI Microservice**: Continuous behavioral analysis evaluating 8 telemetry dimensions in real time.
5. **Administrator Oversight Module**: Live system telemetry, user activation/deactivation, token revocation, audit log explorer, and compliance reporting.

---

### Slide 10: Security Architecture
- **Authenticated Encryption**: AES-256-GCM with 96-bit initialization vectors and 128-bit authentication tags prevents ciphertext tampering.
- **Pre-Decryption Integrity Check**: Computes SHA-256 digest on decrypted bytes and matches against on-chain hash before presenting files.
- **Identity Privacy**:
  $$\text{PID} = \text{Keccak-256}(\text{Email} \parallel \text{Random 128-bit Nonce})$$
- **Stateless Authorization**: JWT signed with minimum 256-bit HMAC-SHA256, verified per-request with active token blacklist enforcement.

---

### Slide 11: AI Threat Detection (Sentinel AI)
- **Model**: Unsupervised `IsolationForest` ($150$ decision trees, contamination rate $= 0.08$).
- **Benchmark**: Trained using the **CICIDS2017** intrusion dataset behavioral anomaly profiles mapped to an 8-feature clinical access vector:
  1. `hour_of_day` (0-23)
  2. `day_of_week` (0-6)
  3. `records_per_hour` (velocity)
  4. `time_since_last` (burst rate)
  5. `specialty_match` (clinical relevance)
  6. `hospital_match` (institutional locality)
  7. `access_freq_7d` (weekly baseline)
  8. `unique_patients_1h` (harvesting indicator)
- **Scoring Pipeline**: Evaluated synchronously in $<15\text{ms}$; scores $\ge 0.75$ trigger immediate access revocation and administrative security alerts.

---

### Slide 12: Implementation Screenshots & UI Walkthrough
- **Admin System Dashboard**: Real-time KPI summary (Total Users, Active Records, Audit Logs, Flagged Anomalies).
- **Subsystem Health Status**: Live indicators for Spring Boot REST API, PostgreSQL Store, FastAPI Sentinel AI, and EVM Ledger.
- **Patient Portal**: Upload file interface with CP-ABE access policy builder and doctor authorization table.
- **Doctor Portal & Risk Gauge**: Real-time interactive gauge visualizing Sentinel AI risk score alongside decrypted medical records.

---

### Slide 13: Testing & Experimental Results
- **Automated Test Results**:
  - Unit & Integration Test Suites: **100% Passed (16/16 tests)**.
  - Multi-Provider Email Suite (`EmailServiceTest`): **9/9 Passed**.
  - Authentication Security Suite (`AuthSecurityTest`): **3/3 Passed**.
- **Model Evaluation Performance**:
  - **Precision**: `91.67%` (Low false alarm rate)
  - **Recall**: `99.00%` (99/100 simulated attack patterns caught)
  - **F1-Score**: `95.19%`
  - **ROC-AUC**: `99.84%`
  - **Inference Latency**: `12.4ms` per transaction

---

### Slide 14: Limitations & Future Enhancements
- **Current Limitations**:
  - Resend free-tier sandbox requires domain verification for sending emails to arbitrary public recipients.
  - Local Hardhat EVM requires active RPC node; public testnet deployment requires managing gas faucet limits.
- **Future Enhancements**:
  - **Zero-Knowledge Proofs (ZK-SNARKs)**: Enable verification of doctor credentials without revealing attributes.
  - **Decentralized Identifier (DID) W3C Standard**: Integrate self-sovereign identity wallets (e.g. MetaMask, Polygon ID).
  - **Federated Machine Learning**: Train Sentinel AI across multiple hospital clusters without centralizing telemetry.

---

### Slide 15: Conclusion & References
- **Conclusion**: MedChain successfully proves that decentralized blockchain architectures, advanced cryptography (AES-256-GCM + CP-ABE), and real-time machine learning can coexist to create a HIPAA-compliant, zero-trust healthcare data exchange.
- **Key References**:
  1. *Sharafaldin et al.*, "Toward Generating a New Intrusion Detection Dataset and Intrusion Traffic Characterization" (CICIDS2017), ICISSP 2018.
  2. *Bethencourt et al.*, "Ciphertext-Policy Attribute-Based Encryption", IEEE S&P 2007.
  3. *Blaze et al.*, "Divertible Protocols and Atomic Proxy Cryptography", EUROCRYPT 1998.
  4. *NIST SP 800-38D*, "Recommendation for Block Cipher Modes of Operation: Galois/Counter Mode (GCM)".
