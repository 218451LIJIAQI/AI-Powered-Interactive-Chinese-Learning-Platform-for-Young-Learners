package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

public record StudentStoryActivityResponse(
        Long id,
        String title,
        String instructions,
        String storyText,
        String pinyin,
        String translation,
        String imageUrl,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        List<StudentStoryQuestionResponse> questions,
        LearningStage learningStage) {

    public StudentStoryActivityResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(storyText, "storyText must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
