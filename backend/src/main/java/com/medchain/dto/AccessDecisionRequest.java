package com.medchain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccessDecisionRequest {

    @NotBlank(message = "Request ID is required")
    private String requestId;

    private Integer durationDays;
}
