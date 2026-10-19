package com.example.aichinese.dto.student.activity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

public record StudentActivitySummaryResponse(
        Long id,
        ActivityType type,
        String title,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        LocalDateTime updatedAt,
        boolean completed,
        LearningStage learningStage) {

    public StudentActivitySummaryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
