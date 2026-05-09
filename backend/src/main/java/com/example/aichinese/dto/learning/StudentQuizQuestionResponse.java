package com.example.aichinese.dto.learning;

import java.util.List;

public record StudentQuizQuestionResponse(
        Long id,
        String questionType,
        String questionText,
        Integer points,
        Integer displayOrder,
        List<QuizOptionResponse> options) {
}
