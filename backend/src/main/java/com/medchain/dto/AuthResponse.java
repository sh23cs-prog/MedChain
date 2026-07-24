package com.medchain.dto;

import lombok.Data;
import java.util.Map;

@Data
public class AuthResponse {
    private String token;
    private String id;
    private String name;
    private String email;
    private String role;
    private String pseudonymId;
    private Map<String, String> attributes;
    private boolean active;
    private boolean blocked;
}
