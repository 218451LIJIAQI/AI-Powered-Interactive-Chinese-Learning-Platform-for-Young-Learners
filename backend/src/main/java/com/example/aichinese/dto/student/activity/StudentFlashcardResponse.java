package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

public record StudentFlashcardResponse(
        Long id,
        String title,
        String frontText,
        String backText,
        String pinyin,
        String englishMeaning,
        String malayMeaning,
        String imageUrl,
        String audioUrl,
        Integer sortOrder,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        LearningStage learningStage) {

    public StudentFlashcardResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(frontText, "frontText must not be null");
        Objects.requireNonNull(backText, "backText must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
