package com.example.aichinese.repository.projection;

import java.time.LocalDateTime;

public interface VideoProgressAnalyticsRow {

    Long getId();

    Long getLessonId();

    boolean isCompleted();

    LocalDateTime getLastWatchedAt();

    LocalDateTime getUpdatedAt();
}
