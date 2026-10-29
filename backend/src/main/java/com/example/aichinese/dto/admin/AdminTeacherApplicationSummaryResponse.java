package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;

/** One application waiting for a decision (the Teacher applications list and the dashboard). */
public record AdminTeacherApplicationSummaryResponse(
        Long teacherId,
        Long userId,
        String fullName,
        String email,
        LocalDateTime submittedAt,
        long documentCount) {
}
