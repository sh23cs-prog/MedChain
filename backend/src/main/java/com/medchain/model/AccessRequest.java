package com.medchain.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@Document(collection = "access_requests")
public class AccessRequest {

    @Id
    private String id;

    private String recordId;
    private String doctorId;
    private String patientId;

    private String status; // PENDING, GRANTED, DENIED

    private LocalDateTime requestedAt = LocalDateTime.now();
    private LocalDateTime updatedAt;
}
