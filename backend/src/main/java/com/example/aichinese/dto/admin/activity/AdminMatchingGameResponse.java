package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminMatchingGameResponse(
        Long id,
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
        List<AdminMatchingGameItemResponse> items,
        String rejectionReason,
        LearningStage learningStage) {

    public AdminMatchingGameResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
