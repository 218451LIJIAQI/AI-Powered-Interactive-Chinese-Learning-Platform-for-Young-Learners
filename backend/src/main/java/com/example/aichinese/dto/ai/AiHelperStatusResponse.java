package com.example.aichinese.dto.ai;

/**
 * What the AI Helper can do for this student right now: whether SparkBot answers at all and can score recordings
 * and handwriting photos, the requests used today against the daily limit, and the admin's AI notice text.
 */
public record AiHelperStatusResponse(
        boolean available,
        boolean speechCheckAvailable,
        boolean handwritingCheckAvailable,
        int usedToday,
        int dailyLimit,
        String aiNotice) {
}
