# MedChain - System Architecture
# Based on: "Blockchain-Aided Privacy-Preserving Medical Data Sharing Scheme for E-Healthcare System"

## Overview
A privacy-preserving medical data sharing system combining:
- CP-ABE (Ciphertext-Policy Attribute-Based Encryption) for fine-grained access control
- Proxy Re-Encryption (PRE) for secure key delegation
- Blockchain for immutable audit trail and access policy enforcement
- Pseudonym-based identity privacy on-chain

---

## System Architecture

```
┌──────────────────────────────────────────────────────────────────────┐
│                          CLIENT LAYER                                │
│   ┌─────────────┐    ┌─────────────┐    ┌─────────────┐            │
│   │  Patient UI  │    │  Doctor UI  │    │  Admin UI   │            │
│   │ (Pseudonym)  │    │(Attributes) │    │ (Monitor)   │            │
│   └──────┬───────┘    └──────┬──────┘    └──────┬──────┘            │
│          └──────────────────┴──────────────────┘                    │
│                         React Frontend                               │
└─────────────────────────────┬────────────────────────────────────────┘
                               │ HTTPS + JWT
┌─────────────────────────────▼────────────────────────────────────────┐
│                      SPRING BOOT BACKEND                             │
│                                                                      │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────────────┐   │
│  │   Auth   │  │  Record  │  │  Admin   │  │  CP-ABE / PRE    │   │
│  │Controller│  │Controller│  │Controller│  │  Crypto Service  │   │
│  └──────────┘  └──────────┘  └──────────┘  └──────────────────┘   │
└──────┬──────────────┬──────────────────┬─────────────────────────────┘
       │              │                  │
┌──────▼──────┐ ┌─────▼──────┐  ┌───────▼──────────────────────────┐
│  BLOCKCHAIN  │ │  ML/AI     │  │         DATABASE LAYER           │
│   LAYER      │ │  LAYER     │  │                                  │
│              │ │            │  │  MongoDB                         │
│  Ethereum    │ │  FastAPI   │  │  - Users (pseudonyms)            │
│  Smart       │ │  (port     │  │  - Encrypted records (CP-ABE)    │
│  Contract:   │ │   8000)    │  │  - Re-encryption keys (PRE)      │
│  - Pseudonym │ │            │  │  - Access policies               │
│    registry  │ │  Anomaly   │  │                                  │
│  - Policy    │ │  Detection │  │  IPFS                            │
│    storage   │ │  (IF+AE)   │  │  - CP-ABE encrypted ciphertext   │
│  - Audit log │ │            │  │  - Policy metadata               │
│  - Access    │ │            │  │                                  │
│    control   │ └────────────┘  └──────────────────────────────────┘
└─────────────┘
```

---

## Key Entities (from the paper)

| Entity | Role |
|--------|------|
| Patient (Data Owner) | Encrypts data with CP-ABE, defines access policy, generates PRE key |
| Doctor (Data User) | Has attribute set, requests access, receives re-encrypted data |
| Proxy Server | Holds re-encryption key, transforms ciphertext — never sees plaintext |
| Blockchain | Stores pseudonym registry, access policies, audit logs |
| Cloud/IPFS | Stores CP-ABE encrypted ciphertext |
| Admin | Monitors anomalies, manages attribute authority |
| Attribute Authority (AA) | Issues attribute keys to doctors based on credentials |

---

## Data Flow

### 1. System Setup (Attribute Authority)
```
AA runs Setup(λ) → generates Master Public Key (MPK) + Master Secret Key (MSK)
AA issues attribute keys to doctors:
  KeyGen(MSK, attributes) → SK_doctor
  attributes = {specialty, hospital, license_level, department}
```

### 2. Patient Registration + Pseudonym Generation
```
Patient registers → Spring Boot generates:
  - Real identity stored in MongoDB (encrypted)
  - Pseudonym (PID) = Hash(identity || random_nonce)
  - PID registered on Blockchain (real identity never on-chain)
  - PRE key pair generated: (pk_patient, sk_patient)
```

### 3. Upload Medical Record (CP-ABE Encryption)
```
Patient defines access policy:
  policy = "(specialty=Cardiology AND hospital=CityHospital) OR (license=Senior)"

Patient encrypts:
  CT = CP-ABE.Encrypt(MPK, plaintext_record, policy)

Patient generates PRE re-encryption key for proxy:
  rk = PRE.ReKeyGen(sk_patient, pk_proxy)

Store:
  CT (ciphertext) → IPFS → returns CID
  CID + policy_hash + patient_PID → Blockchain
  rk (re-encryption key) → MongoDB (proxy server)
  record metadata → MongoDB
```

### 4. Doctor Access Request (PRE + CP-ABE Decryption)
```
Doctor requests record with attribute set:
  attrs = {specialty: "Cardiology", hospital: "CityHospital", license: "Senior"}

Spring Boot:
  1. Verify doctor attributes satisfy policy (CP-ABE policy check)
  2. Call ML anomaly detection → get risk_score
  3. If risk_score > 0.6 → BLOCK + log on blockchain
  4. If attributes match AND risk_score ≤ 0.6:
     a. Fetch CT from IPFS
     b. Proxy applies re-encryption: CT' = PRE.ReEncrypt(rk, CT)
     c. Doctor decrypts: plaintext = CP-ABE.Decrypt(SK_doctor, CT')
     d. Log access on Blockchain with doctor PID (not real identity)
```

### 5. Audit & Revocation
```
Revoke doctor access:
  Patient updates policy on Blockchain (excludes doctor's attributes)
  Old re-encryption key invalidated in MongoDB
  New rk generated if needed

Audit:
  All access events on Blockchain with pseudonyms
  Admin can trace PID → real identity via MongoDB mapping
  ML flags anomalous patterns
```

---

## Algorithm Explanation

### Algorithm 1 — CP-ABE (Ciphertext-Policy Attribute-Based Encryption)

```
SETUP:
  AA generates bilinear group G of prime order p
  MPK = (G, g, e(g,g)^α, h=g^β)
  MSK = (α, β)

KEY GENERATION (for doctor with attributes S):
  For each attribute a ∈ S:
    K = g^((α + r) / β),  L = g^r,  Kx = H(x)^r
  SK_doctor = {K, L, {Kx}x∈S}

ENCRYPTION (patient encrypts with policy tree T):
  Choose random s ∈ Zp
  CT = (T, C̃ = M·e(g,g)^(αs), C = h^s, {Cy = g^(qy(0)), C'y = H(att(y))^(qy(0))}y∈leaves(T))

DECRYPTION (doctor decrypts if attributes satisfy T):
  If attrs(doctor) satisfies T:
    Recover e(g,g)^(αs) using pairing operations
    M = C̃ / e(g,g)^(αs)
```

### Algorithm 2 — Proxy Re-Encryption (PRE)

```
Purpose: Patient delegates decryption to proxy WITHOUT revealing sk_patient

KEY GENERATION:
  Patient: (pk_p = g^sk_p, sk_p)
  Proxy:   (pk_proxy = g^sk_proxy, sk_proxy)

RE-ENCRYPTION KEY:
  rk = sk_p / sk_proxy   (patient computes, sends to proxy)

RE-ENCRYPTION:
  Original CT encrypted under pk_p:  CT = (A = M·e(g,g)^(αs), B = g^(s·sk_p))
  Proxy transforms:                  CT' = (A, B' = B^(rk) = g^(s·sk_p·(1/sk_proxy)))
                                          = (A, g^(s·sk_proxy))

DECRYPTION by doctor:
  M = A / e(B', g)^(1/sk_proxy)

Security: Proxy sees only CT and CT' — never plaintext or sk_patient
```

### Algorithm 3 — Pseudonym Generation (Privacy-Preserving Identity)

```
On registration:
  nonce = random 256-bit value
  PID = SHA3-256(real_identity || nonce)
  
  Store on Blockchain: PID (public)
  Store in MongoDB:    (PID → real_identity) mapping (private, admin only)

On each transaction:
  Use PID instead of real identity
  Different PID per session optional (unlinkability)
```

### Algorithm 4 — Blockchain Access Policy Enforcement (Solidity)

```solidity
// Policy stored as hash on-chain
// Actual policy string stored off-chain (MongoDB/IPFS)

struct Record {
  bytes32 cidHash;        // IPFS CID hash
  bytes32 policyHash;     // hash of CP-ABE access policy
  bytes32 ownerPID;       // patient pseudonym
  uint256 timestamp;
}

struct AccessEvent {
  bytes32 requesterPID;   // doctor pseudonym
  bytes32 recordId;
  bool granted;
  uint256 timestamp;
}

// Access granted only if:
// 1. Policy hash matches
// 2. Doctor attributes verified off-chain by Spring Boot
// 3. ML anomaly score ≤ threshold
```

### Algorithm 5 — Anomaly Detection (Isolation Forest + Autoencoder)

```
Features per access request (8 features, extended from paper):
  f1 = hour_of_day          (0-23)
  f2 = day_of_week          (0-6)
  f3 = records_per_hour     (count)
  f4 = time_since_last      (minutes)
  f5 = specialty_match      (0/1 — does doctor specialty match record type?)
  f6 = hospital_match       (0/1 — same hospital as patient?)
  f7 = access_frequency_7d  (accesses in last 7 days)
  f8 = unique_patients_1h   (distinct patients accessed in 1 hour)

Isolation Forest:
  anomaly_score_IF = normalize(decision_function(features))

Autoencoder:
  Train on normal patterns → learn reconstruction
  anomaly_score_AE = reconstruction_error(features)

Combined (from paper weights):
  risk_score = 0.5 × anomaly_score_IF + 0.5 × anomaly_score_AE

Thresholds:
  > 0.8 → CRITICAL: block + alert + log on blockchain
  > 0.6 → HIGH:     block + alert admin
  > 0.4 → MEDIUM:   allow + flag for review
  ≤ 0.4 → LOW:      allow normally
```

### Algorithm 6 — Combined Security Flow

```
risk_score = 0.5 × IF_score + 0.5 × AE_score

Access Decision:
  IF (doctor.attributes satisfies CT.policy)
    AND (risk_score ≤ 0.6)
    AND (blockchain.isRegistered(doctor.PID))
  THEN
    proxy.reEncrypt(CT) → CT'
    doctor.decrypt(CT') → plaintext
    blockchain.logAccess(doctor.PID, record.id, GRANTED)
  ELSE
    blockchain.logAccess(doctor.PID, record.id, DENIED)
    IF risk_score > 0.6: alert(admin)
```

---

## Security Properties (from paper)

| Property | Mechanism |
|----------|-----------|
| Data Confidentiality | CP-ABE encryption — only attribute-matching users decrypt |
| Fine-grained Access | Policy tree — AND/OR combinations of attributes |
| Forward Security | PRE key rotation — old keys invalidated on revocation |
| Identity Privacy | Pseudonyms on blockchain — real identity never exposed |
| Audit Integrity | Immutable blockchain log — tamper-proof access history |
| Collusion Resistance | CP-ABE keys are user-specific — combining keys gains no advantage |
| Proxy Security | PRE — proxy transforms but never decrypts |
