package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

public record BadgeSummaryResponse(
        Long badgeId,
        String name,
        String description,
        String iconKey,
        LocalDateTime awardedAt) {
}
