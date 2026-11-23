package com.example.aichinese.dto.ai;

/**
 * The student's available AI features, per-file upload limits in bytes, daily usage and AI notice.
 */
public record AiHelperStatusResponse(
        boolean available,
        boolean speechCheckAvailable,
        boolean handwritingCheckAvailable,
        long audioUploadMaxBytes,
        long imageUploadMaxBytes,
        int usedToday,
        int dailyLimit,
        String aiNotice) {
}
