# MedChain — System Architecture & Data Flow Specification

## 1. Executive Summary

**MedChain** is an enterprise-grade, privacy-preserving electronic medical data sharing platform designed to solve the critical vulnerabilities of modern healthcare systems: fragmented medical records, single-point-of-failure centralized databases, unauthorized clinical data access, and insider threats.

MedChain integrates five foundational security paradigms:
1. **Asymmetric Identity Pseudonymization**: Real identities are completely decoupled from on-chain transactions; only cryptographically generated pseudonyms (PIDs) exist on the ledger.
2. **CP-ABE (Ciphertext-Policy Attribute-Based Encryption)**: Fine-grained access control policies (e.g. `(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)`) govern data decryption.
3. **AES-256-GCM Off-Chain Storage**: Payloads are encrypted using 256-bit symmetric encryption with 128-bit authentication tags and SHA-256 integrity verification.
4. **Hardhat EVM Blockchain Audit Trail**: Smart contracts provide an immutable, non-repudiable audit ledger recording all upload, grant, revoke, and view events.
5. **Sentinel AI (FastAPI Isolation Forest)**: Real-time behavioral anomaly scoring detects credential compromise, abnormal off-hours access, and mass exfiltration.

---

## 2. System Architecture Diagram

```mermaid
graph TD
    subgraph ClientLayer ["1. Presentation Layer (React 18 + Material-UI)"]
        PatientUI["Patient Portal<br/>(Upload, Policy Def, Grant/Revoke)"]
        DoctorUI["Doctor Portal<br/>(ABAC Access, View/Decrypt)"]
        AdminUI["Admin Portal<br/>(Monitoring, Audit Logs, User Mgmt)"]
    end

    subgraph GatewayLayer ["2. Nginx Reverse Proxy / Load Balancer"]
        Nginx["Nginx Reverse Proxy<br/>Port 3000 / 80"]
    end

    subgraph AppLayer ["3. Application & Security Layer (Spring Boot 3 + Java 17)"]
        SecurityGateway["Spring Security Gateway<br/>(Stateless JWT, CORS, BCrypt)"]
        AuthController["Auth Controller<br/>(OTP, Login, MFA)"]
        RecordController["Record Controller<br/>(AES-256-GCM, Policy Parser)"]
        AdminController["Admin Controller<br/>(Session Revocation, RBAC)"]
        
        subgraph CoreServices ["Core Services"]
            AESService["AES-256-GCM Cryptographic Engine"]
            ABACEngine["CP-ABE Policy Evaluation Engine"]
            EmailService["Multi-Provider Email Dispatcher<br/>(Resend / Brevo / SMTP)"]
            PseudonymService["Cryptographic Blinding & PID Generator"]
        end
    end

    subgraph IntelligenceLayer ["4. Sentinel AI Threat Detection (FastAPI + Python 3.11)"]
        MLService["FastAPI Microservice<br/>Port 8000"]
        IsoForest["Isolation Forest Anomaly Model<br/>(150 Trees, Contamination=0.08)"]
        StandardScaler["Z-Score Telemetry Preprocessor"]
    end

    subgraph BlockchainLayer ["5. Decentralized Ledger (Hardhat EVM Node)"]
        EVMNode["EVM JSON-RPC Node<br/>Port 8545"]
        MedChainContract["MedChain Smart Contract<br/>(Pseudonym Registry, Audit Trail)"]
    end

    subgraph DataLayer ["6. Storage & Persistence Layer"]
        PostgresDB[("PostgreSQL 16 Database<br/>(Users, Encrypted Payloads, Audit Logs)")]
        FileStorage[("Persistent Volume Storage<br/>(/app/uploads/encrypted)")]
    end

    %% Client to Gateway
    PatientUI -->|HTTP / SPA| Nginx
    DoctorUI -->|HTTP / SPA| Nginx
    AdminUI -->|HTTP / SPA| Nginx

    %% Gateway to App
    Nginx -->|Reverse Proxy /api| SecurityGateway

    %% App Layer Internal
    SecurityGateway --> AuthController
    SecurityGateway --> RecordController
    SecurityGateway --> AdminController

    AuthController --> PseudonymService
    AuthController --> EmailService

    RecordController --> AESService
    RecordController --> ABACEngine
    RecordController --> MLService

    %% Services to Data & Blockchain
    SecurityGateway --> PostgresDB
    AESService --> FileStorage
    AESService --> PostgresDB
    RecordController --> EVMNode
    EVMNode --> MedChainContract
    MLService --> IsoForest
    IsoForest --> StandardScaler
```

---

## 3. End-to-End Data Flow Diagrams

### 3.1. Medical Record Upload & Encryption Flow

```mermaid
sequenceDiagram
    autonumber
    actor Patient as Patient (Data Owner)
    participant Front as React Frontend
    participant Back as Spring Boot Backend
    participant AES as AES-256-GCM Engine
    participant DB as PostgreSQL DB
    participant Chain as EVM Blockchain

    Patient->>Front: Selects file, inputs title & defines access policy<br/>e.g. (specialty=Cardiology AND hospital=CityHospital)
    Front->>Back: POST /api/records/upload (JWT + File Payload)
    Back->>AES: Generate 256-bit encryption key & 96-bit IV
    AES->>AES: Encrypt plaintext bytes -> Ciphertext + 128-bit Auth Tag
    AES->>AES: Compute SHA-256 integrity hash of original file
    Back->>DB: Persist Record Metadata + Encrypted Bytes (BYTEA)
    Back->>Chain: smartContract.uploadRecord(recordId, sha256Hash, policyHash, patientPID)
    Chain-->>Back: Emit RecordUploaded event & return Transaction Hash
    Back->>DB: Store Blockchain Transaction Hash & Record ID
    Back-->>Front: HTTP 200 OK (Record ID, SHA-256 Hash, Tx Hash)
    Front-->>Patient: Display Confirmation & Blockchain Proof
```

### 3.2. Record Access Request & Sentinel AI Threat Evaluation

```mermaid
sequenceDiagram
    autonumber
    actor Doctor as Doctor (Data Requester)
    participant Front as React Frontend
    participant Back as Spring Boot Backend
    participant ML as Sentinel AI (FastAPI)
    participant AES as AES-256-GCM Engine
    participant DB as PostgreSQL DB
    participant Chain as EVM Blockchain

    Doctor->>Front: Requests to view patient record
    Front->>Back: GET /api/records/{recordId}/view (JWT)
    Back->>Back: Verify Doctor role & evaluate CP-ABE policy attributes
    alt Attributes Do Not Match Policy
        Back-->>Front: HTTP 403 Forbidden (Policy mismatch)
    else Attributes Satisfy Policy
        Back->>ML: POST /predict (8-feature access telemetry vector)
        ML->>ML: Preprocess with StandardScaler & score via Isolation Forest
        ML-->>Back: Return anomaly score (e.g. 0.12) & risk tier ("LOW")
        
        alt Anomaly Score >= 0.75 (CRITICAL)
            Back->>DB: Log CRITICAL event & trigger security alert
            Back->>Chain: smartContract.recordAccess(recordId, doctorPID, "DENIED")
            Back-->>Front: HTTP 403 Forbidden (Security anomaly detected)
        else Anomaly Score < 0.75 (Authorized)
            Back->>DB: Retrieve encrypted bytes & verify SHA-256 digest
            Back->>AES: Decrypt ciphertext using record key
            Back->>DB: Insert AccessLog (Granted = true, Score = 0.12)
            Back->>Chain: smartContract.recordAccess(recordId, doctorPID, "GRANTED")
            Back-->>Front: HTTP 200 OK (Decrypted payload + Anomaly Score)
            Front-->>Doctor: Render medical record & Risk Gauge
        end
    end
```

---

## 4. Key Security & Cryptographic Mechanisms

| Mechanism | Technical Implementation | Purpose |
|---|---|---|
| **Identity Pseudonymization** | $\text{PID} = \text{Keccak-256}(\text{Identity} \parallel \text{Nonce})$ | Prevents patient tracking and profiling on public or consortium ledgers. |
| **Data Encryption at Rest** | AES-256 in Galois/Counter Mode (GCM) | Provides authenticated encryption with non-malleable 128-bit verification tags. |
| **Integrity Verification** | SHA-256 Cryptographic Hash Digest | Guarantees tamper detection before and after file decryption. |
| **Access Control (ABAC)** | Ciphertext-Policy Attribute-Based Encryption | Enforces attribute-based access independent of central storage servers. |
| **Key Delegation** | Proxy Re-Encryption (PRE) Key Transformation | Enables patient to grant access to doctors without revealing their private keys. |
| **Immutable Ledger** | Solidity 0.8.20 Smart Contract on EVM | Provides immutable, tamper-resistant access trails for HIPAA audit compliance. |
| **Anomaly Detection** | Scikit-Learn Isolation Forest ($150$ trees) | Evaluates 8 behavioral dimensions in $<15\text{ms}$ to detect unauthorized harvesting. |
| **Multi-Factor Auth** | 6-Digit BCrypt-Hashed OTP (Resend/Brevo/SMTP) | Prevents brute force credential attacks and enforces single-use verification. |
