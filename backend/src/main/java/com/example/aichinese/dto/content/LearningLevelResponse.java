package com.example.aichinese.dto.content;

import java.util.Objects;

public record LearningLevelResponse(
        Long id,
        String name,
        String description) {

    public LearningLevelResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
