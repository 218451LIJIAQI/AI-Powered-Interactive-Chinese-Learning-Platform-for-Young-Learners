package com.example.aichinese.dto.admin;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.example.aichinese.dto.DtoNormalization;
import com.example.aichinese.dto.support.SupportRequestSummaryResponse;
import com.example.aichinese.entity.enums.ReviewContentType;

/** The admin dashboard: what needs attention (with its first items), the platform in numbers, last week's sign-ups. */
public record AdminDashboardResponse(
        Queue<AdminTeacherApplicationSummaryResponse> teacherApplications,
        Queue<ReviewItem> reviewQueue,
        Queue<AiReportItem> aiReports,
        Queue<SupportRequestSummaryResponse> support,
        AdminPlatformNumbers numbers,
        List<AdminPlatformNumbers.DayCount> registrations) {

    public AdminDashboardResponse {
        Objects.requireNonNull(teacherApplications, "teacherApplications must not be null");
        Objects.requireNonNull(reviewQueue, "reviewQueue must not be null");
        Objects.requireNonNull(aiReports, "aiReports must not be null");
        Objects.requireNonNull(support, "support must not be null");
        Objects.requireNonNull(numbers, "numbers must not be null");
        registrations = DtoNormalization.immutableListOrEmpty(registrations);
    }

    /** How many items wait in one queue, and the first of them. */
    public record Queue<T>(long count, List<T> items) {

        public Queue {
            items = DtoNormalization.immutableListOrEmpty(items);
        }
    }

    public record ReviewItem(
            ReviewContentType type,
            Long id,
            String title,
            String ownerName,
            String parentTitle,
            LocalDateTime submittedAt) {
    }

    public record AiReportItem(
            Long id,
            String reason,
            String featureKey,
            String studentName,
            LocalDateTime reportedAt) {
    }
}
