package com.medchain.service;

import com.medchain.exception.MedChainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;

@Service
@Slf4j
public class FileValidationService {

    public static final long MAX_FILE_SIZE_BYTES = 50 * 1024 * 1024; // 50 MB

    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "pdf", "jpg", "jpeg", "png", "doc", "docx"
    );

    private static final Map<String, Set<String>> ALLOWED_MIME_TYPES = Map.of(
            "pdf", Set.of("application/pdf"),
            "jpg", Set.of("image/jpeg", "image/jpg"),
            "jpeg", Set.of("image/jpeg", "image/jpg"),
            "png", Set.of("image/png"),
            "doc", Set.of("application/msword", "application/x-msword"),
            "docx", Set.of("application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/zip", "application/x-zip-compressed")
    );

    public void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new MedChainException("Uploaded file cannot be null or empty.");
        }

        // 1. File size validation
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new MedChainException("File size exceeds maximum permitted limit of 50 MB. Uploaded size: " + (file.getSize() / (1024 * 1024)) + " MB.");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new MedChainException("Uploaded file name is invalid.");
        }

        // 2. Extension validation
        String extension = getFileExtension(originalFilename).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new MedChainException("File extension '" + extension + "' is not supported. Allowed formats: PDF, JPG, JPEG, PNG, DOC, DOCX.");
        }

        // 3. MIME type validation
        String mimeType = file.getContentType();
        Set<String> validMimes = ALLOWED_MIME_TYPES.getOrDefault(extension, Set.of());
        if (mimeType != null && !mimeType.isBlank()) {
            String cleanMime = mimeType.toLowerCase().split(";")[0].trim();
            if (!validMimes.contains(cleanMime)) {
                log.warn("MIME type mismatch for file {}: provided '{}', allowed for extension '{}': {}", originalFilename, cleanMime, extension, validMimes);
                throw new MedChainException("MIME type mismatch. The content type '" + cleanMime + "' does not match extension '" + extension + "'.");
            }
        }
    }

    public String detectMimeType(byte[] data, String originalFilename, String fallbackMime) {
        if (data != null && data.length >= 4) {
            // PDF Magic Header: %PDF- (0x25, 0x50, 0x44, 0x46)
            if (data[0] == 0x25 && data[1] == 0x50 && data[2] == 0x44 && data[3] == 0x46) {
                return "application/pdf";
            }
            // JPEG Magic Header: 0xFF, 0xD8, 0xFF
            if ((data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
                return "image/jpeg";
            }
            // PNG Magic Header: 0x89, 0x50, 0x4E, 0x47
            if ((data[0] & 0xFF) == 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
                return "image/png";
            }
            // OLE2 / DOC Magic Header: 0xD0, 0xCF, 0x11, 0xE0
            if ((data[0] & 0xFF) == 0xD0 && (data[1] & 0xFF) == 0xCF && (data[2] & 0xFF) == 0x11 && (data[3] & 0xFF) == 0xE0) {
                return "application/msword";
            }
            // PK ZIP / DOCX Magic Header: PK\x03\x04 (0x50, 0x4B, 0x03, 0x04)
            if (data[0] == 0x50 && data[1] == 0x4B && data[2] == 0x03 && data[3] == 0x04) {
                if (originalFilename != null && originalFilename.toLowerCase().endsWith(".docx")) {
                    return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                }
            }
        }

        if (originalFilename != null && originalFilename.contains(".")) {
            String ext = getFileExtension(originalFilename).toLowerCase();
            switch (ext) {
                case "pdf": return "application/pdf";
                case "jpg": case "jpeg": return "image/jpeg";
                case "png": return "image/png";
                case "doc": return "application/msword";
                case "docx": return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            }
        }

        return (fallbackMime != null && !fallbackMime.isBlank() && !fallbackMime.equalsIgnoreCase("application/octet-stream"))
                ? fallbackMime
                : "application/pdf";
    }

    public String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "unnamed_file.bin";
        }

        // Strip directory path sequences (Windows and Unix)
        String basename = filename;
        int lastPos = Math.max(basename.lastIndexOf('/'), basename.lastIndexOf('\\'));
        if (lastPos >= 0) {
            basename = basename.substring(lastPos + 1);
        }

        // Remove path traversal sequences
        basename = basename.replaceAll("\\.\\.", "");

        // Retain alphanumeric, hyphens, underscores, dots
        String sanitized = basename.replaceAll("[^a-zA-Z0-9._-]", "_");

        if (sanitized.isBlank() || sanitized.startsWith(".")) {
            sanitized = "sanitized_" + System.currentTimeMillis() + "_" + sanitized;
        }

        return sanitized;
    }

    public String calculateSHA256(byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("Data payload cannot be null for SHA-256 calculation.");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(data);
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 MessageDigest algorithm not available.", e);
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex >= 0 && dotIndex < filename.length() - 1)
                ? filename.substring(dotIndex + 1)
                : "";
    }
}
