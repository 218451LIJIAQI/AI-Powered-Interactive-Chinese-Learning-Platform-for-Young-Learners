package com.example.aichinese.dto.communication;

import java.time.LocalDateTime;

public record TeacherFeedbackResponse(
        Long id,
        Long teacherId,
        String teacherName,
        Long studentId,
        String studentName,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle,
        String feedbackType,
        String message,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
