package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminActivitySummaryResponse(
        Long id,
        ActivityType type,
        String title,
        ContentStatus status,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        Long createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String rejectionReason,
        LearningStage learningStage) {

    public AdminActivitySummaryResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
