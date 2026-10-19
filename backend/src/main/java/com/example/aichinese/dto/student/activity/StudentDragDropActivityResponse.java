package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

/** Drag-and-drop activity: shuffled items and the distinct zones (alphabetical), without the item-to-zone key. */
public record StudentDragDropActivityResponse(
        Long id,
        String title,
        String instructions,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentDragDropItemResponse> items,
        List<String> zones,
        LearningStage learningStage) {

    public StudentDragDropActivityResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
        zones = zones == null ? List.of() : List.copyOf(zones);
    }
}