package com.example.aichinese.dto.student.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;

/**
 * Cross-type summary card shown in the student activity library feed. The
 * representation deliberately omits the admin-side audit metadata
 * (createdByUserId / createdByDisplayName). The student-facing update
 * timestamp is included so clients can explain and preserve the global
 * freshness ordering of the feed.
 */
public record StudentActivitySummaryResponse(
        Long id,
        ActivityType type,
        String title,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        LocalDateTime updatedAt,
        boolean completed) {
}
