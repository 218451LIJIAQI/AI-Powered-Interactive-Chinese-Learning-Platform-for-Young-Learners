package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
}
