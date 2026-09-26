package com.example.aichinese.repository.projection;

import java.time.LocalDateTime;

import com.example.aichinese.entity.enums.AccessType;

public interface VideoProgressAnalyticsRow {

    Long getId();

    Long getLessonId();

    AccessType getLessonAccessType();

    Long getModuleId();

    Long getPackageId();

    AccessType getPackageAccessType();

    boolean isCompleted();

    LocalDateTime getLastWatchedAt();

    LocalDateTime getUpdatedAt();
}
