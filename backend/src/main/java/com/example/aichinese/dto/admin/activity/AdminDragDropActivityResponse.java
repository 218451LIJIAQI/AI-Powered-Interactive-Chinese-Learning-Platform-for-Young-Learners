package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminDragDropActivityResponse(
        Long id,
        String title,
        String instructions,
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
        List<AdminDragDropItemResponse> items,
        String rejectionReason,
        LearningStage learningStage) {

    public AdminDragDropActivityResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        items = items == null ? List.of() : List.copyOf(items);
    }
}
