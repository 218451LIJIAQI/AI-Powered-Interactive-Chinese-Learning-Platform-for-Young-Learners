package com.example.aichinese.repository.projection;

import java.time.LocalDateTime;

public interface QuizAttemptAnalyticsRow {

    Long getId();

    Long getStudentId();

    Long getLessonId();

    Integer getScore();

    Integer getMaxScore();

    boolean isSubmitted();

    LocalDateTime getCompletedAt();

    LocalDateTime getCreatedAt();
}
