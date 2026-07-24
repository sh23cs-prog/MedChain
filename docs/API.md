# MedChain — Comprehensive API Specification

## Base URL
- **Local Development**: `http://localhost:8080/api`
- **Docker Network**: `http://backend:8080/api`
- **Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI JSON**: `http://localhost:8080/api-docs`

---

## 1. Authentication Endpoints

### `POST /api/auth/login`
Authenticate a user and issue a JWT token.

**Request Body**:
```json
{
  "email": "doctor@medchain.com",
  "password": "doctor123"
}
```

**Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "id": "65b...",
  "name": "Dr. Robert Smith",
  "email": "doctor@medchain.com",
  "role": "DOCTOR",
  "pseudonymId": "0x4f8a9b1c...",
  "attributes": {
    "specialty": "CARDIOLOGY",
    "hospital": "City General Hospital"
  }
}
```

### `POST /api/auth/register`
Register a new Patient or Doctor account.

**Request Body**:
```json
{
  "name": "Alice Johnson",
  "email": "patient@medchain.com",
  "password": "patient123",
  "role": "PATIENT",
  "phone": "+1 555-0199",
  "attributes": {
    "hospital": "City General Hospital"
  }
}
```

---

## 2. Medical Record Endpoints

### `POST /api/records/upload` (Role: PATIENT)
Upload a new medical file. The backend executes:
1. AES-256-GCM authenticated file encryption
2. IPFS upload via Kubo API
3. CP-ABE key protection
4. Ethereum smart contract record registration

**Request Body**:
```json
{
  "title": "Echocardiogram Lab Report",
  "description": "Routine cardiac checkup results",
  "recordType": "LAB_REPORT",
  "accessPolicy": "specialty:CARDIOLOGY AND hospital:City General Hospital",
  "content": "data:text/plain;base64,SGVhbHRoIFJlY29yZCBDb250ZW50...",
  "fileName": "cardio_report.pdf",
  "fileSize": "142.5 KB",
  "fileMimeType": "text/plain"
}
```

### `GET /api/records/patient/{patientId}` (Authenticated)
Retrieve all records uploaded by a patient.

### `GET /api/records/doctor/authorized` (Role: DOCTOR)
Retrieve all records for which the doctor has been granted access.

### `POST /api/records/{recordId}/grant/{doctorId}` (Role: PATIENT)
Grant doctor access to a record and generate Proxy Re-Encryption (PRE) delegation key.

### `POST /api/records/{recordId}/revoke/{doctorId}` (Role: PATIENT)
Revoke access and invalidate the PRE key for forward security.

### `GET /api/records/{recordId}/view` (Role: DOCTOR)
Request record decryption. Performs real-time Isolation Forest anomaly scoring and returns:
```json
{
  "record": { "id": "...", "title": "..." },
  "riskScore": 14,
  "status": "SAFE",
  "riskLevel": "LOW",
  "decryptionStatus": "AES_PRE_APPLIED",
  "blockchainTxHash": "0x8f2a9b...",
  "decryptedContent": "data:text/plain;base64,..."
}
```

---

## 3. Metrics & Monitoring Endpoints

### `GET /api/metrics` (Authenticated)
Get live latency, memory, CPU, and service health metrics:
```json
{
  "encryptionTimeMs": 12,
  "blockchainLatencyMs": 245,
  "apiResponseMs": 35,
  "mlResponseMs": 62,
  "cpuUsage": 32.4,
  "memoryUsage": 58.6,
  "heapUsedMB": 240,
  "heapMaxMB": 512,
  "services": {
    "backend": true,
    "mongodb": true,
    "ml": true,
    "blockchain": true,
    "ipfs": true
  }
}
```

---

## 4. Blockchain Explorer Endpoints

### `GET /api/blockchain/transactions`
List recent Ethereum audit log events (`RecordUploaded`, `AccessGranted`, `AccessRevoked`, `RecordViewed`).

### `GET /api/blockchain/stats`
Summary statistics of on-chain ledger activity.

---

## 5. Report Generation Endpoints (Role: ADMIN)

- `GET /api/reports/access-logs.csv` — CSV download of all access logs
- `GET /api/reports/access-logs.pdf` — Formatted PDF report download
- `GET /api/reports/user-activity.csv` — CSV summary of user actions
