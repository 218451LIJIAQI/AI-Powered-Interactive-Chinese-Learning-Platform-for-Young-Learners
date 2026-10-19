package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LearningStage;

/**
 * Listening exercise for a student. The stored question payload is parsed server side and its
 * answers are removed; {@code freeResponse} is true for legacy free-text prompts without an answer
 * key (the learner writes what they heard). {@code audioUrl} may be null.
 */
public record StudentListeningExerciseResponse(
        Long id,
        String title,
        String audioUrl,
        String prompt,
        List<StudentListeningQuestionResponse> questions,
        boolean freeResponse,
        Integer hskLevel,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed,
        LearningStage learningStage) {

    public StudentListeningExerciseResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        questions = questions == null ? List.of() : List.copyOf(questions);
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}