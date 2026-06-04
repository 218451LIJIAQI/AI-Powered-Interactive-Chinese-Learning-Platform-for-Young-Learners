package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "id",
        "title",
        "description",
        "meetingPlatform",
        "liveClassStatus",
        "startTime",
        "endTime",
        "timeZone",
        "durationMinutes",
        "maxParticipants",
        "enrolledCount",
        "enrolled",
        "canEnrol",
        "accessAllowed",
        "canJoin",
        "meetingLinkVisible",
        "meetingLink",
        "teacher",
        "lesson",
        "coursePackage",
        "createdAt",
        "updatedAt"
})
public record LiveClassResponse(
        Long id,
        String title,
        String description,
        String meetingPlatform,
        String liveClassStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        String timeZone,
        Integer durationMinutes,
        Integer maxParticipants,
        long enrolledCount,
        boolean enrolled,
        boolean canEnrol,
        boolean accessAllowed,
        boolean canJoin,
        boolean meetingLinkVisible,
        String meetingLink,
        LiveClassTeacherSummaryResponse teacher,
        LiveClassLessonSummaryResponse lesson,
        LiveClassPackageSummaryResponse coursePackage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public LiveClassResponse {
        Objects.requireNonNull(id, "id must not be null");
        if (enrolledCount < 0) {
            throw new IllegalArgumentException("enrolledCount must be non-negative, was " + enrolledCount);
        }
    }
}
