package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "teacherId", "userId", "displayName" })
public record LiveClassTeacherSummaryResponse(
        Long teacherId,
        Long userId,
        String displayName) {

    public LiveClassTeacherSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
    }
}
