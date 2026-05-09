package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record TeacherQuizAttemptSummaryResponse(
        Long attemptId,
        Long studentId,
        String studentName,
        Integer score,
        Integer maxScore,
        LocalDateTime submittedAt) {
}
