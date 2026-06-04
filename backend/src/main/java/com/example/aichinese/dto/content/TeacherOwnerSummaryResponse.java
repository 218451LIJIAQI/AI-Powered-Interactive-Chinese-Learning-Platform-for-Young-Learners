package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

@JsonPropertyOrder({ "teacherId", "userId", "fullName", "teacherStatus" })
public record TeacherOwnerSummaryResponse(
        Long teacherId,
        Long userId,
        String fullName,
        TeacherStatus teacherStatus) {

    public TeacherOwnerSummaryResponse {
        Objects.requireNonNull(teacherId, "teacherId must not be null");
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
    }
}
