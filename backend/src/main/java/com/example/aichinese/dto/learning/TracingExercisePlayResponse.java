package com.example.aichinese.dto.learning;

public record TracingExercisePlayResponse(
        Long id,
        Long lessonId,
        String title,
        String targetCharacter,
        String pinyin,
        String meaning,
        String worksheetUrl,
        String instructions) {
}
