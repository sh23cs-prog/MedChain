// SPDX-License-Identifier: MIT
pragma solidity ^0.8.19;

/// @title MedChain - Privacy-Preserving Medical Data Sharing
/// @notice Based on "Blockchain-Aided Privacy-Preserving Medical Data Sharing Scheme for E-Healthcare System"
/// @dev Uses pseudonyms (PIDs) — real identities never stored on-chain
contract MedChain {

    // ─── Structs ────────────────────────────────────────────────────────────────

    struct Record {
        bytes32 cidHash;        // keccak256 of IPFS CID (actual file stored off-chain)
        bytes32 policyHash;     // keccak256 of CP-ABE access policy string
        bytes32 ownerPID;       // patient pseudonym — real identity never on-chain
        uint256 uploadedAt;
        bool exists;
        bool revoked;
    }

    struct AccessEvent {
        bytes32 requesterPID;   // doctor pseudonym
        bytes32 recordId;
        bool granted;
        uint8 riskLevel;        // 0=LOW, 1=MEDIUM, 2=HIGH, 3=CRITICAL
        uint256 timestamp;
    }

    struct PseudonymEntry {
        bytes32 pid;
        bytes32 role;           // keccak256("PATIENT") or keccak256("DOCTOR")
        uint256 registeredAt;
        bool active;
    }

    // ─── State ──────────────────────────────────────────────────────────────────

    // recordId => Record
    mapping(bytes32 => Record) private records;

    // PID => PseudonymEntry (pseudonym registry)
    mapping(bytes32 => PseudonymEntry) private pseudonyms;

    // recordId => doctorPID => authorized
    mapping(bytes32 => mapping(bytes32 => bool)) private accessGrants;

    // All access events (immutable audit trail)
    AccessEvent[] public auditLog;

    address public admin;

    // ─── Events ─────────────────────────────────────────────────────────────────

    event PseudonymRegistered(bytes32 indexed pid, bytes32 role, uint256 timestamp);
    event RecordUploaded(bytes32 indexed recordId, bytes32 ownerPID, bytes32 policyHash, uint256 timestamp);
    event AccessGranted(bytes32 indexed recordId, bytes32 doctorPID, uint256 timestamp);
    event AccessRevoked(bytes32 indexed recordId, bytes32 doctorPID, uint256 timestamp);
    event AccessAttempt(bytes32 indexed recordId, bytes32 requesterPID, bool granted, uint8 riskLevel, uint256 timestamp);
    event RecordViewed(bytes32 indexed recordId, bytes32 doctorPID, uint8 riskLevel, uint256 timestamp);
    event PolicyUpdated(bytes32 indexed recordId, bytes32 newPolicyHash, uint256 timestamp);

    // ─── Modifiers ──────────────────────────────────────────────────────────────

    modifier onlyAdmin() { require(msg.sender == admin, "Not admin"); _; }
    modifier pidActive(bytes32 pid) { require(pseudonyms[pid].active, "PID not registered"); _; }

    constructor() { admin = msg.sender; }

    // ─── Admin Management ───────────────────────────────────────────────────────

    /// @notice Transfer admin role to a new address
    function transferAdmin(address newAdmin) external onlyAdmin {
        require(newAdmin != address(0), "Zero address");
        admin = newAdmin;
    }

    // ─── Pseudonym Registry ─────────────────────────────────────────────────────

    /// @notice Register a pseudonym (PID) on-chain — restricted to admin (backend wallet)
    function registerPseudonym(bytes32 pid, bytes32 role) external onlyAdmin {
        require(!pseudonyms[pid].active, "PID already registered");
        pseudonyms[pid] = PseudonymEntry(pid, role, block.timestamp, true);
        emit PseudonymRegistered(pid, role, block.timestamp);
    }

    // ─── Record Management ──────────────────────────────────────────────────────

    /// @notice Upload a record — stores only hashes, never plaintext or real IPFS CID
    function uploadRecord(
        bytes32 recordId,
        bytes32 cidHash,
        bytes32 policyHash,
        bytes32 ownerPID
    ) external pidActive(ownerPID) {
        require(!records[recordId].exists, "Record already exists");
        records[recordId] = Record(cidHash, policyHash, ownerPID, block.timestamp, true, false);
        emit RecordUploaded(recordId, ownerPID, policyHash, block.timestamp);
    }

    /// @notice Update access policy
    function updatePolicy(bytes32 recordId, bytes32 newPolicyHash, bytes32 ownerPID) external {
        require(records[recordId].ownerPID == ownerPID, "Not record owner");
        records[recordId].policyHash = newPolicyHash;
        emit PolicyUpdated(recordId, newPolicyHash, block.timestamp);
    }

    // ─── Access Control ─────────────────────────────────────────────────────────

    /// @notice Grant access to a doctor PID
    function grantAccess(bytes32 recordId, bytes32 doctorPID, bytes32 ownerPID) external pidActive(doctorPID) {
        require(records[recordId].ownerPID == ownerPID, "Not record owner");
        require(!records[recordId].revoked, "Record revoked");
        accessGrants[recordId][doctorPID] = true;
        emit AccessGranted(recordId, doctorPID, block.timestamp);
    }

    /// @notice Revoke access from a doctor PID
    function revokeAccess(bytes32 recordId, bytes32 doctorPID, bytes32 ownerPID) external {
        require(records[recordId].ownerPID == ownerPID, "Not record owner");
        accessGrants[recordId][doctorPID] = false;
        emit AccessRevoked(recordId, doctorPID, block.timestamp);
    }

    /// @notice Log an access attempt with risk level
    function logAccessAttempt(
        bytes32 recordId,
        bytes32 requesterPID,
        bool granted,
        uint8 riskLevel
    ) external pidActive(requesterPID) {
        require(records[recordId].exists, "Record not found");
        auditLog.push(AccessEvent(requesterPID, recordId, granted, riskLevel, block.timestamp));
        emit AccessAttempt(recordId, requesterPID, granted, riskLevel, block.timestamp);
        if (granted) {
            emit RecordViewed(recordId, requesterPID, riskLevel, block.timestamp);
        }
    }

    // ─── View Functions ─────────────────────────────────────────────────────────

    function isAuthorized(bytes32 recordId, bytes32 doctorPID) external view returns (bool) {
        return accessGrants[recordId][doctorPID] && !records[recordId].revoked;
    }

    function getRecord(bytes32 recordId) external view returns (bytes32 cidHash, bytes32 policyHash, bytes32 ownerPID, bool revoked) {
        Record storage r = records[recordId];
        require(r.exists, "Record not found");
        return (r.cidHash, r.policyHash, r.ownerPID, r.revoked);
    }

    function isPseudonymRegistered(bytes32 pid) external view returns (bool) {
        return pseudonyms[pid].active;
    }

    function getAuditLogCount() external view returns (uint256) {
        return auditLog.length;
    }

    function getAuditEntry(uint256 index) external view returns (
        bytes32 requesterPID, bytes32 recordId, bool granted, uint8 riskLevel, uint256 timestamp
    ) {
        AccessEvent storage e = auditLog[index];
        return (e.requesterPID, e.recordId, e.granted, e.riskLevel, e.timestamp);
    }
}
