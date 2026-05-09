package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;

public record TeacherQuizResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String description,
        ContentStatus contentStatus,
        Integer timeLimitMinutes,
        Integer totalPoints,
        List<TeacherQuizQuestionResponse> questions,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
