package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ContentStatus;

public record ListeningExerciseResponse(
        Long id,
        Long lessonId,
        String lessonTitle,
        String title,
        String chineseText,
        String pinyin,
        String audioUrl,
        ContentStatus contentStatus,
        List<TeacherListeningOptionResponse> options,
        String explanation,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
