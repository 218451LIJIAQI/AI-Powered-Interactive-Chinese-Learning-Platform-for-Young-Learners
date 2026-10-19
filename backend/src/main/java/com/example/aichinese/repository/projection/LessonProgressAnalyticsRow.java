package com.example.aichinese.repository.projection;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.LearningType;

public interface LessonProgressAnalyticsRow {

    Long getId();

    Long getLessonId();

    LearningType getLearningType();

    boolean isCompleted();

    LocalDateTime getLastAccessedAt();

    LocalDateTime getUpdatedAt();
}
