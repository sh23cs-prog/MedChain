package com.medchain.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.util.Map;

@Data
public class RegisterRequest {
    @NotBlank private String name;
    @Email @NotBlank private String email;
    @NotBlank private String password;
    @NotBlank private String role;
    private String phone;
    private Map<String, String> attributes;
}
