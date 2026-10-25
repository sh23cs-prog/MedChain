package com.medchain.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AccessRequestDto {

    @NotBlank(message = "Record ID is required")
    private String recordId;

    private String reason;
}
