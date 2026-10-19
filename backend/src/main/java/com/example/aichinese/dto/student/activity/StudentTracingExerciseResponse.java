package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

/** Tracing exercise (practice only, never graded); worksheet and target character are optional. */
public record StudentTracingExerciseResponse(
        Long id,
        String title,
        String worksheetUrl,
        String targetCharacter,
        String pinyin,
        String meaning,
        String instructions,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        LearningStage learningStage) {

    public StudentTracingExerciseResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}