package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record LiveClassResponse(
        Long id,
        String title,
        String description,
        String meetingPlatform,
        String liveClassStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
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
}
