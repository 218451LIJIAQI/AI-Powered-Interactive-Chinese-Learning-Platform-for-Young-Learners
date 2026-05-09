package com.example.aichinese.dto.learning;

import java.util.List;

public record StudentQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<StudentQuizQuestionResponse> questions) {
}
