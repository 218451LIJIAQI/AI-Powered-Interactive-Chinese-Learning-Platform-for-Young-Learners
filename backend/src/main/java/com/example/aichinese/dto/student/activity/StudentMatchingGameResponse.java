package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

/**
 * Matching game for a student: left items (with ids) and shuffled right options without pair links,
 * so the answer key is never sent before an attempt. The student submits leftItemId/rightText pairs.
 */
public record StudentMatchingGameResponse(
        Long id,
        String title,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentMatchingGameItemResponse> leftItems,
        List<StudentMatchingOptionResponse> rightOptions,
        LearningStage learningStage) {

    public StudentMatchingGameResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
        leftItems = leftItems == null ? List.of() : List.copyOf(leftItems);
        rightOptions = rightOptions == null ? List.of() : List.copyOf(rightOptions);
    }
}