package com.example.aichinese.dto.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.ContentStatus;

/** Admin analytics for the last {@code days} days (7 or 30); content and AI totals are all time. */
public record AdminAnalyticsResponse(
        int days,
        LocalDate from,
        LocalDate to,
        AdminPlatformNumbers numbers,
        long newRegistrations,
        List<AdminPlatformNumbers.DayCount> registrations,
        Learning learning,
        /** Classes and lessons per status. */
        Map<ContentStatus, Long> contentByStatus,
        Map<AiRequestStatus, Long> aiRequestsByStatus) {

    public AdminAnalyticsResponse {
        Objects.requireNonNull(numbers, "numbers must not be null");
        Objects.requireNonNull(learning, "learning must not be null");
        registrations = DtoNormalization.immutableListOrEmpty(registrations);
        contentByStatus = Map.copyOf(Objects.requireNonNull(contentByStatus, "contentByStatus must not be null"));
        aiRequestsByStatus = Map.copyOf(Objects.requireNonNull(aiRequestsByStatus, "aiRequestsByStatus must not be null"));
    }

    /**
     * Learning in the period: students who opened a lesson or finished a quiz, lessons completed, the quiz average
     * (null without attempts) and the students whose quiz average is below 60%.
     */
    public record Learning(
            long activeStudents,
            long totalStudents,
            long lessonsCompleted,
            Integer quizAverage,
            long needsSupport) {
    }
}
