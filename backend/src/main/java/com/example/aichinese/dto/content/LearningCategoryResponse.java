package com.example.aichinese.dto.content;

import java.util.Objects;

public record LearningCategoryResponse(
        Long id,
        String name,
        String description) {

    public LearningCategoryResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(name, "name must not be null");
    }
}
