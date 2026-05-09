package com.example.aichinese.dto.communication;

public record FeedbackCandidateResponse(
        Long studentId,
        String studentName,
        String studentEmail,
        Long lessonId,
        String lessonTitle,
        Long quizAttemptId,
        String quizTitle) {
}
