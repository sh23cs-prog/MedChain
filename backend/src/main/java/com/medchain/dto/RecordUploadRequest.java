package com.medchain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request DTO for uploading a medical record.
 * Content can be provided as:
 *   - base64-encoded file data (content field)
 *   - or the backend handles AES encryption before IPFS upload
 */
@Data
public class RecordUploadRequest {

    @NotBlank(message = "Title is required")
    private String title;

    private String description;

    @NotBlank(message = "Record type is required")
    private String recordType;   // LAB_REPORT, PRESCRIPTION, SCAN, DISCHARGE, CONSULTATION

    private String accessPolicy; // e.g., "specialty:CARDIOLOGY AND hospital:GENERAL"
    private String ipfsCid;      // Optional: if client already uploaded to IPFS
    private String content;      // Base64-encoded file content for AES encryption
    private String fileName;
    private String fileSize;
    private String fileMimeType; // MIME type for proper decryption/rendering
}
