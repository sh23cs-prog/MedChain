# MedChain — Comprehensive REST API Specification

## 1. Overview & Base URLs

MedChain exposes a secure, documented RESTful API conforming to OpenAPI 3.0 standards:

- **Local Docker Compose**: `http://localhost:8080/api`
- **Reverse Proxy Route**: `http://localhost:3000/api`
- **Production Backend**: `https://medchain-backend-kgmq.onrender.com/api`
- **Interactive Swagger UI**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Schema (JSON)**: `http://localhost:8080/api-docs`

### Authentication Model
Protected endpoints require an `Authorization` header containing a valid JSON Web Token (JWT) signed with HMAC-SHA256:
```http
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

---

## 2. Authentication Controller (`/api/auth`)

| Endpoint | Method | Role | Summary |
|---|---|---|---|
| `/api/auth/register` | `POST` | Public | Register new Patient or Doctor account |
| `/api/auth/verify-otp` | `POST` | Public | Verify 6-digit OTP code & issue JWT token |
| `/api/auth/resend-otp` | `POST` | Public | Resend fresh OTP code (subject to 60s cooldown) |
| `/api/auth/login` | `POST` | Public | Authenticate user credentials & issue JWT |
| `/api/auth/forgot-password` | `POST` | Public | Request password reset OTP code |
| `/api/auth/reset-password` | `POST` | Public | Reset password using verified OTP code |
| `/api/auth/change-password` | `POST` | Authenticated | Change password for logged-in user |
| `/api/auth/me` | `GET` | Authenticated | Retrieve authenticated user profile |
| `/api/auth/logout` | `POST` | Authenticated | Invalidate JWT token via TokenBlacklistService |

### `POST /api/auth/register`
**Request Payload**:
```json
{
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "password": "Password123!",
  "role": "PATIENT",
  "phone": "+1-555-0199",
  "attributes": {}
}
```
**Response (200 OK)**:
```json
{
  "token": null,
  "id": "b784ebc6-5a1b-4214-9343-7146b1720549",
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "role": "PATIENT",
  "pseudonymId": "0x5bfcb45a6805d620328872f384c8345d9f6e1736a682664fe3555f92427674d0",
  "active": true,
  "blocked": false,
  "message": "Account registered successfully. A 6-digit verification code has been sent to your email."
}
```

### `POST /api/auth/verify-otp`
**Request Payload**:
```json
{
  "email": "jane.doe@example.com",
  "otp": "482910",
  "type": "REGISTRATION"
}
```
**Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": "b784ebc6-5a1b-4214-9343-7146b1720549",
  "name": "Jane Doe",
  "email": "jane.doe@example.com",
  "role": "PATIENT",
  "pseudonymId": "0x5bfcb45a6805d620328872f384c8345d9f6e1736a682664fe3555f92427674d0",
  "message": "Email verified successfully."
}
```

### `POST /api/auth/login`
**Request Payload**:
```json
{
  "email": "patient@medchain.com",
  "password": "Password123!"
}
```
**Response (200 OK)**:
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "id": "74c69b76-f5b6-48cc-9518-994a4c8c4d25",
  "name": "Alice Johnson",
  "email": "patient@medchain.com",
  "role": "PATIENT",
  "pseudonymId": "0xa0fd1e25629296936ee4b138b788ef6df76d2b2ea2c4fa8c1de8714d5feb6a87",
  "attributes": {}
}
```

---

## 3. Medical Record Controller (`/api/records`)

| Endpoint | Method | Role | Summary |
|---|---|---|---|
| `/api/records/upload` | `POST` | `PATIENT`, `ADMIN` | Upload & encrypt medical record with CP-ABE policy |
| `/api/records/upload-file` | `POST` | `PATIENT`, `ADMIN` | Multipart file upload with automated encryption pipeline |
| `/api/records/patient/{patientId}` | `GET` | Authenticated | Get all records owned by a patient |
| `/api/records/doctor/authorized` | `GET` | `DOCTOR`, `ADMIN` | Get records accessible to the authenticated doctor |
| `/api/records/{recordId}/view` | `GET` | `PATIENT`, `DOCTOR`, `ADMIN` | View record content with real-time AI anomaly scoring |
| `/api/records/{recordId}/download` | `GET` | `PATIENT`, `DOCTOR`, `ADMIN` | Decrypt & download original file with integrity check |
| `/api/records/{recordId}/grant/{doctorId}` | `POST` | `PATIENT`, `ADMIN` | Explicitly authorize a doctor and generate PRE key |
| `/api/records/{recordId}/revoke/{doctorId}` | `POST` | `PATIENT`, `ADMIN` | Revoke a doctor's access & invalidate PRE key |

### `POST /api/records/upload`
**Request Payload**:
```json
{
  "title": "Complete Blood Count & Metabolic Panel",
  "description": "Routine annual clinical screening lab report",
  "recordType": "LAB_REPORT",
  "accessPolicy": "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)",
  "content": "data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9ia...",
  "fileName": "blood_test_results.pdf",
  "fileSize": "1.2 MB",
  "fileMimeType": "application/pdf"
}
```
**Response (200 OK)**:
```json
{
  "id": "e035a925-26e6-4a0a-b268-34ec60b4e28e",
  "title": "Complete Blood Count & Metabolic Panel",
  "fileName": "blood_test_results.pdf",
  "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
  "accessPolicy": "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)",
  "policyHash": "0x4b8e21a0",
  "blockchainTxHash": "0x9e4a36da194028fa27ffca410db31c3d78ceaecebbd9f5fd928b12a857423d00",
  "uploadedAt": "2026-11-03T10:15:30"
}
```

### `GET /api/records/{recordId}/view`
**Response (200 OK)**:
```json
{
  "record": {
    "id": "e035a925-26e6-4a0a-b268-34ec60b4e28e",
    "title": "Complete Blood Count & Metabolic Panel",
    "fileName": "blood_test_results.pdf",
    "content": "data:application/pdf;base64,JVBERi0xLjQKJeLjz9MKMSAwIG9ia...",
    "sha256Hash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
  },
  "anomalyScore": 0.12,
  "riskLevel": "LOW",
  "accessGranted": true,
  "message": "Access authorized. Real-time threat evaluation verified normal behavior."
}
```

---

## 4. Administrator Controller (`/api/admin`)

| Endpoint | Method | Role | Summary |
|---|---|---|---|
| `/api/admin/dashboard` | `GET` | `ADMIN` | Get system overview KPIs & subsystem statuses |
| `/api/admin/users` | `GET` | `ADMIN` | List all registered users with role & record counts |
| `/api/admin/users/{userId}/deactivate` | `POST` | `ADMIN` | Deactivate account & immediately revoke sessions |
| `/api/admin/users/{userId}/activate` | `POST` | `ADMIN` | Reactivate user account |
| `/api/admin/users/{userId}/toggle-active` | `POST` | `ADMIN` | Toggle user active/inactive status |
| `/api/admin/users/{userId}` | `DELETE` | `ADMIN` | Permanently remove user (preserves audit records) |
| `/api/admin/users/{userId}/role` | `POST` | `ADMIN` | Assign new role (`PATIENT`, `DOCTOR`, `ADMIN`) |
| `/api/admin/logs` | `GET` | `ADMIN` | View audit trail with pagination and anomaly filters |
| `/api/admin/alerts` | `GET` | `ADMIN` | View unresolved Sentinel AI threat alerts |

### `DELETE /api/admin/users/{userId}`
**Response (200 OK)**:
```json
{
  "message": "User 'John Doe' (john@example.com) was permanently removed.",
  "userId": "b8b9a787-f372-443c-be67-929d8e9568de",
  "status": "DELETED"
}
```

---

## 5. Metrics & Sentinel AI Controller (`/api/metrics`)

| Endpoint | Method | Summary |
|---|---|---|
| `/api/metrics/health` | `GET` | Health status of Spring Boot, PostgreSQL, Hardhat EVM, and Sentinel AI |
| `/api/metrics/live` | `GET` | Live telemetry (CPU load, memory allocation, request latency, active threads) |
| `/api/metrics/threat-level` | `GET` | Aggregate anomaly score across recent accesses |

---

## 6. Standard Error Response Structure

All exceptions return a standardized JSON error format:

```json
{
  "message": "User-friendly description of the error",
  "timestamp": "2026-11-03T14:44:00"
}
```

| HTTP Status | Meaning | Typical Scenario |
|---|---|---|
| `400 Bad Request` | Validation Failure | Invalid email format, incorrect OTP, cooldown active |
| `401 Unauthorized` | Authentication Failure | Missing or expired JWT token, invalid password |
| `403 Forbidden` | Access Denied | Doctor does not satisfy CP-ABE policy, non-admin on admin route |
| `404 Not Found` | Entity Missing | User ID or Record ID does not exist |
| `500 Server Error` | Infrastructure Error | Database connectivity loss, unexpected runtime error |
