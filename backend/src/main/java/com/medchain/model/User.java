package com.medchain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Document(collection = "users")
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String email;

    private String password;
    private String name;
    private String role;            // PATIENT, DOCTOR, ADMIN
    private String phone;

    private String pseudonymId;
    private String pseudonymNonce;

    private String publicKey;
    private String encryptedPrivateKey;

    private Map<String, String> attributes;

    private boolean active = true;
    private boolean blocked = false;
    private LocalDateTime createdAt = LocalDateTime.now();
}
