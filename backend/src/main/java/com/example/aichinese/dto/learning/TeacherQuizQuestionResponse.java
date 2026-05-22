package com.example.aichinese.dto.learning;

import java.util.List;

public record TeacherQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        String correctAnswerText,
        Integer points,
        Integer displayOrder,
        String explanation,
        List<TeacherQuizOptionResponse> options) {
}
