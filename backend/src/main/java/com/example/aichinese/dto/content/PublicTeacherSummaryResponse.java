package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({ "teacherId", "displayName", "verified" })
public record PublicTeacherSummaryResponse(
        Long teacherId,
        String displayName,
        boolean verified) {

    public PublicTeacherSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
    }
}
