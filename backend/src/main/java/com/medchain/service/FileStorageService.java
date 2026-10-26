package com.medchain.service;

import com.medchain.exception.MedChainException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    @Value("${file.storage.dir:./uploads/encrypted}")
    private String storageDir;

    public String storeEncryptedFile(byte[] encryptedBytes, String originalFilename) {
        try {
            Path storagePath = Paths.get(storageDir).toAbsolutePath().normalize();
            Files.createDirectories(storagePath);

            String storageFileName = UUID.randomUUID().toString() + ".enc";
            Path targetFile = storagePath.resolve(storageFileName);

            Files.write(targetFile, encryptedBytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            log.info("Encrypted payload stored securely at internal storage file: {}", storageFileName);

            return targetFile.toString();
        } catch (IOException e) {
            log.error("Failed to store encrypted file: {}", e.getMessage(), e);
            throw new MedChainException("Secure file storage failed: " + e.getMessage());
        }
    }

    public byte[] readEncryptedFile(String storagePathStr) {
        try {
            if (storagePathStr == null || storagePathStr.isBlank()) {
                return null;
            }
            Path path = Paths.get(storagePathStr).toAbsolutePath().normalize();
            if (!Files.exists(path) || !Files.isReadable(path)) {
                log.info("Storage file not found on disk at: {}. Will fall back to persistent database payload.", storagePathStr);
                return null;
            }
            return Files.readAllBytes(path);
        } catch (Exception e) {
            log.warn("Failed to read storage file {}: {}. Falling back to persistent database payload.", storagePathStr, e.getMessage());
            return null;
        }
    }
}
