package com.medchain.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class UserResponse {
    private String id;
    private String email;
    private String name;
    private String role;
    private String phone;
    private String pseudonymId;
    private Map<String, String> attributes;
    private boolean active;
    private boolean blocked;
    private boolean emailVerified;
    private long recordCount;
    private LocalDateTime createdAt;
}
