package com.example.aichinese.dto.learning;

import java.util.List;

public record ListeningExercisePlayResponse(
        Long id,
        Long lessonId,
        String title,
        String chineseText,
        String pinyin,
        String audioUrl,
        List<ListeningOptionResponse> options) {
}
