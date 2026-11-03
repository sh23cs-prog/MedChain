# MedChain — Official Video Demonstration Script & Scene Guide

## 1. Overview & Recording Specifications

- **Total Duration**: Approximately 8 to 9 minutes.
- **Resolution**: 1080p Full HD (1920x1080) at 60 FPS.
- **Audio Setup**: Clear microphone audio, minimal background noise.
- **Presenter Role**: Pair programming / lead developer narrating the core problem, cryptographic workflow, user portals, and AI threat mitigation.

> [!NOTE]
> This document is the **official, scene-by-scene recording script and storyboard**. It outlines exact onscreen actions, narration dialogue, timing cues, and transition markers for creating the final project submission video.

---

## 2. Scene-by-Scene Demonstration Plan

```
00:00 ── Scene 1: Introduction & Architecture
01:00 ── Scene 2: Registration & Multi-Factor OTP Verification
02:15 ── Scene 3: Patient Portal & Secure File Upload
03:30 ── Scene 4: Cryptographic Verification (AES-256-GCM & SHA-256)
04:15 ── Scene 5: Doctor Authorization & CP-ABE Delegation
05:15 ── Scene 6: Doctor Portal & Authorized Record Decryption
06:15 ── Scene 7: Sentinel AI Anomaly & Threat Detection Demo
07:15 ── Scene 8: Administrator Portal & Immutable Audit Trail
08:15 ── Scene 9: Conclusion & Architectural Wrap-Up
```

---

### Scene 1: Introduction & Architecture Overview (0:00 – 1:00)
- **Onscreen Display**: MedChain Landing Page (`http://localhost:3000` or `https://med-chain-amber.vercel.app`).
- **Visual Focus**: Scroll through the hero section, highlighting the system features: Zero-Trust Encryption, CP-ABE, Blockchain Auditability, and Sentinel AI.
- **Narration Dialogue**:
  > *"Hello everyone, and welcome to the demonstration of MedChain — an enterprise-grade, privacy-preserving electronic medical data sharing platform. In modern healthcare, patient records are either trapped in isolated hospital databases or exposed to insider threats and single-point-of-failure breaches. MedChain solves this by combining client-centric AES-256-GCM encryption, Ciphertext-Policy Attribute-Based Encryption (CP-ABE), an immutable EVM blockchain audit trail, and real-time machine learning threat detection via Sentinel AI. Today, we will walk you through the entire lifecycle from patient registration to secure medical record decryption and threat mitigation."*

---

### Scene 2: Registration & Multi-Factor OTP Verification (1:00 – 2:15)
- **Onscreen Display**: Registration Page (`/register`), followed by the OTP Verification screen (`/verify-otp`).
- **Actions**:
  1. Click **"Get Started"** or **"Register"**.
  2. Register a new user:
     - Name: `Jane Doe`
     - Email: `medchainteam23@gmail.com`
     - Role: `PATIENT`
     - Phone: `+1-555-0199`
     - Password: `Password123!`
  3. Click **"Create Account"**. The system submits the request to Spring Boot.
  4. Notice the screen transitions to the 6-digit OTP verification screen.
  5. Show that entering an incorrect code displays `"Invalid OTP code. 2 attempt(s) remaining."`
  6. Enter the correct 6-digit OTP code received via Resend / Brevo and submit.
  7. Show the success notification and automated redirect to the login page.
- **Narration Dialogue**:
  > *"We begin by registering a new patient. When we submit the registration form, our backend generates a cryptographically secure 6-digit one-time password, hashes it using BCrypt, and dispatches it over HTTPS port 443 via our integrated transactional email providers — Resend and Brevo. Notice that no raw OTP is returned in the API response or exposed on client storage. Entering an incorrect code decrements our attempt counter, and after 3 failures, the token is invalidated. Once the correct code is verified, our backend confirms the user's email and issues a secure HMAC-SHA256 JWT token."*

---

### Scene 3: Patient Portal & Secure Medical Record Upload (2:15 – 3:30)
- **Onscreen Display**: Patient Dashboard (`/patient`), logged in as `patient@medchain.com` (Alice Johnson).
- **Actions**:
  1. Showcase the Patient Dashboard: active records, authorized doctors, and access requests.
  2. Click **"Upload Medical Record"**.
  3. Enter record details:
     - Title: `Annual Cardiology Evaluation`
     - Record Type: `LAB_REPORT`
     - Description: `Complete ECG and lipid panel screening.`
     - Access Policy: `(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)`
  4. Select a sample PDF medical report (`blood_test_results.pdf`).
  5. Click **"Encrypt & Anchor on MedChain"**.
  6. Show the upload progress indicator and the instant appearance of the new record in the table.
- **Narration Dialogue**:
  > *"Now we are in the Patient Portal. As a patient, I have sovereign control over my medical data. When uploading a record, I don't just store a file; I define a cryptographic access policy using CP-ABE syntax. Here, we specify that only senior doctors or cardiologists at CityHospital can decrypt this report. The file is encrypted on the client and backend using authenticated AES-256-GCM. The plaintext never touches untrusted storage, and an immutable record registration transaction is anchored on the blockchain."*

---

### Scene 4: Cryptographic Verification (AES-256-GCM & SHA-256) (3:30 – 4:15)
- **Onscreen Display**: Record Details Drawer / Modal.
- **Actions**:
  1. Click on the uploaded record to open details.
  2. Highlight the **SHA-256 Hash Digest**, the **Policy Hash**, and the **Blockchain Transaction Hash**.
  3. Show the **Download File** action and demonstrate that the downloaded file matches the original byte-for-byte.
- **Narration Dialogue**:
  > *"Let's inspect the cryptographic properties of this record. Before encryption, MedChain computes a 256-bit SHA-256 cryptographic digest of the original file. When a user downloads or views the file, MedChain decrypts the AES-256 ciphertext and re-computes the hash on the fly. If even a single bit of the file were altered or corrupted on disk, the integrity check would immediately trigger a tamper alert and block file presentation."*

---

### Scene 5: Doctor Authorization & CP-ABE Delegation (4:15 – 5:15)
- **Onscreen Display**: Patient Portal &rarr; Doctor Authorization Section.
- **Actions**:
  1. Scroll down to the **"Authorized Doctors"** panel.
  2. Search for `Dr. Robert Smith` (Cardiology, CityHospital).
  3. Click **"Grant Access"**. Show confirmation dialog explaining Proxy Re-Encryption key derivation.
  4. Confirm the grant. Notice the doctor appears with the active status badge.
  5. Demonstrate the **"Revoke Access"** button, showing that access can be revoked at any time.
- **Narration Dialogue**:
  > *"Patients can also explicitly delegate access to specific physicians. When Alice grants access to Dr. Robert Smith, MedChain generates a Proxy Re-Encryption key delegation token. This allows the proxy server to transform ciphertext encrypted under Alice's key into a form decryptable by Dr. Robert's private key — without the proxy ever seeing the plaintext. Furthermore, access can be revoked with a single click, instantly invalidating the delegation token."*

---

### Scene 6: Doctor Portal & Authorized Record Decryption (5:15 – 6:15)
- **Onscreen Display**: Log out of Patient, log in as `doctor@medchain.com` (Dr. Robert Smith).
- **Actions**:
  1. Enter Doctor Portal (`/doctor`).
  2. Show the **"Authorized Patient Records"** tab.
  3. Locate Alice Johnson's cardiology report.
  4. Click **"View Medical Record"**.
  5. Show the drawer opening with the decrypted document viewer and the **RiskScoreGauge** displaying a green `LOW RISK` score (e.g. 0.12).
- **Narration Dialogue**:
  > *"Now we log in as Dr. Robert Smith. Dr. Robert's profile has the attributes: specialty equals Cardiology and hospital equals CityHospital. Because Dr. Robert satisfies the CP-ABE policy, the system authorizes decryption. Notice the interactive Risk Score Gauge beside the document: it reads 12% Low Risk. The transaction is verified, and Dr. Robert can review the clinical PDF directly in the browser."*

---

### Scene 7: Sentinel AI Anomaly & Threat Detection (6:15 – 7:15)
- **Onscreen Display**: Doctor Portal with unauthorized/anomalous access attempt, followed by Swagger UI / API inspection.
- **Actions**:
  1. Demonstrate access by a doctor whose attributes do NOT match (e.g. `david@medchain.com`, Dermatology).
  2. Attempt to view Alice's Cardiology prescription.
  3. Show the immediate rejection: `HTTP 403 Forbidden: Doctor attributes do not satisfy CP-ABE policy`.
  4. Demonstrate Sentinel AI simulating an off-hours rapid burst query (bulk harvesting simulation).
  5. Show the risk gauge shifting to **CRITICAL RISK** ($\ge 0.75$), triggering an immediate access termination and security alert.
- **Narration Dialogue**:
  > *"Now let's test our AI threat detection. If an unauthorized doctor tries to access this record, the ABAC engine rejects it immediately. But what if a legitimate doctor's credentials are stolen and used at 3 AM to harvest dozens of records in seconds? This is where Sentinel AI steps in. Using our trained Isolation Forest model, Sentinel AI evaluates 8 behavioral telemetry features in less than 15 milliseconds. It detects the velocity anomaly, calculates a Critical Risk score of 88%, and blocks the download before exfiltration can occur."*

---

### Scene 8: Administrator Portal & Immutable Audit Trail (7:15 – 8:15)
- **Onscreen Display**: Log in as `admin@medchain.com` &rarr; Admin Dashboard (`/admin`) and Monitoring (`/monitoring`).
- **Actions**:
  1. View the **Admin Dashboard**: Total Users, Total Records, Audit Logs, Flagged Anomalies.
  2. Scroll down to the **Audit Log Table**: show real-time access events, IP addresses, anomaly scores, and blockchain transaction hashes.
  3. Open the **System Monitoring** page: show live health checks for Spring Boot (UP), PostgreSQL (UP), Sentinel AI (UP), and EVM Ledger.
  4. Show the **User Management** page: demonstrate user deactivation, session revocation, and legal record retention checks.
- **Narration Dialogue**:
  > *"Finally, we log in as the Administrator. The Admin Dashboard provides total oversight without violating patient privacy. The Audit Log captures every system interaction alongside its Sentinel AI anomaly score and blockchain transaction hash. On the User Management page, an admin can immediately deactivate compromised accounts, which revokes all active JWT tokens in real time. Notice that if an admin tries to delete a patient with active clinical records, MedChain blocks the deletion to preserve HIPAA legal record retention mandates."*

---

### Scene 9: Conclusion & Summary (8:15 – 8:45)
- **Onscreen Display**: MedChain Architecture Slide / Landing Page.
- **Narration Dialogue**:
  > *"In summary, MedChain bridges the gap between healthcare data utility and patient privacy. By unifying AES-256-GCM encryption, CP-ABE access policies, EVM smart contracts, and real-time AI anomaly detection into a single containerized Docker solution, MedChain sets a new benchmark for secure, patient-centric healthcare informatics. Thank you for watching!"*
