package com.medchain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;

@Data
@Document(collection = "reencryption_keys")
public class ReEncryptionKey {

    @Id
    private String id;

    private String recordId;
    private String doctorId;
    private String doctorPID;
    private String tempKey;
    private String encryptedAesKey;
    private LocalDateTime expiry;
    private LocalDateTime createdAt = LocalDateTime.now();
    private boolean active = true;
}
