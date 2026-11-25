package com.example.aichinese.entity.enums;

/** Open until an admin resolves it (the answer was wrong and the admin acted) or dismisses it (the answer was fine). */
public enum AiResponseReportStatus {

    PENDING_REVIEW,

    RESOLVED,

    DISMISSED
}
