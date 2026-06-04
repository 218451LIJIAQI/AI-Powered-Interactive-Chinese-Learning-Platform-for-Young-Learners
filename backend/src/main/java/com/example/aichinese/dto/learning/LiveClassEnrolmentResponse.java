package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "liveClassId",
        "studentId",
        "studentName",
        "studentEmail",
        "enrolmentStatus",
        "enrolledAt",
        "cancelledAt",
        "attendedAt"
})
public record LiveClassEnrolmentResponse(
        Long id,
        Long liveClassId,
        Long studentId,
        String studentName,
        String studentEmail,
        String enrolmentStatus,
        LocalDateTime enrolledAt,
        LocalDateTime cancelledAt,
        LocalDateTime attendedAt) {

    public LiveClassEnrolmentResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(liveClassId, "liveClassId must not be null");
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
    }
}
