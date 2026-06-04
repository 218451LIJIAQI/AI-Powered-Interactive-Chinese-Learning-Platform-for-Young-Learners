package com.example.aichinese.dto.student.activity;

import java.util.List;
import java.util.Objects;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

public record StudentListeningExerciseResponse(
        Long id,
        String title,
        String audioUrl,
        String questionPayload,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        boolean completed) {

    public StudentListeningExerciseResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(title, "title must not be null");
        Objects.requireNonNull(audioUrl, "audioUrl must not be null");
        Objects.requireNonNull(questionPayload, "questionPayload must not be null");
        tags = tags == null ? List.of() : List.copyOf(tags);
    }
}
