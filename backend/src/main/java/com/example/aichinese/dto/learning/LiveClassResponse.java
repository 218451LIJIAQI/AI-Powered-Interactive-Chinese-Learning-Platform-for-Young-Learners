package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;
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
        "remainingSeats",
        "full",
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

    /**
     * Seats still open under the participant limit (FR-LIVE-05), or {@code null}
     * when the class has no limit. {@code enrolledCount} counts the seats taken.
     */
    @JsonProperty("remainingSeats")
    public Integer remainingSeats() {
        if (maxParticipants == null) {
            return null;
        }
        return (int) Math.max(0L, maxParticipants - enrolledCount);
    }

    /** True when a participant limit is set and every seat is taken. */
    @JsonProperty("full")
    public boolean full() {
        Integer remaining = remainingSeats();
        return remaining != null && remaining == 0;
    }
}
