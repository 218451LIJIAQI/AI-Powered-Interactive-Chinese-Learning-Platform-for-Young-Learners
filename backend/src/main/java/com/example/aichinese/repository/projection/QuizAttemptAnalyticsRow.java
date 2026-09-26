package com.example.aichinese.repository.projection;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AccessType;

public interface QuizAttemptAnalyticsRow {

    Long getId();

    Long getStudentId();

    Long getLessonId();

    AccessType getLessonAccessType();

    Long getModuleId();

    Integer getScore();

    Integer getMaxScore();

    boolean isSubmitted();

    LocalDateTime getCompletedAt();

    LocalDateTime getCreatedAt();
}
