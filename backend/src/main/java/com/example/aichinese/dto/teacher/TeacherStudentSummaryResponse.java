package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;

public record TeacherStudentSummaryResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String displayName,
        String currentLevel,
        Integer progressPercent,
        Double quizAveragePercent,
        boolean atRisk,
        List<String> weakAreas,
        String latestFeedbackType,
        LocalDateTime latestFeedbackAt,
        LocalDateTime latestActivityAt,
        LocalDateTime joinedAt) {
}
