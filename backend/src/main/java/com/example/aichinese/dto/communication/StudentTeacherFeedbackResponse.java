package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record StudentTeacherFeedbackResponse(
        Long id,
        String teacherName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
