package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;

public record AdminStoryActivityResponse(
        Long id,
        String title,
        String instructions,
        String storyText,
        String pinyin,
        String translation,
        String imageUrl,
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
        List<AdminStoryQuestionResponse> questions,
        String rejectionReason,
        LearningStage learningStage) {

    public AdminStoryActivityResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
