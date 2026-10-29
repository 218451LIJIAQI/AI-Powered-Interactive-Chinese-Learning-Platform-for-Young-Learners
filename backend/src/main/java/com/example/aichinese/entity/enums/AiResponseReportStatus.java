package com.example.aichinese.entity.enums;

import java.util.Optional;

/** Open until an admin resolves it (the answer was wrong and the admin acted) or dismisses it (the answer was fine). */
public enum AiResponseReportStatus {

    PENDING_REVIEW,

    RESOLVED,

    DISMISSED;

    public static Optional<AiResponseReportStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(AiResponseReportStatus.class, value);
    }
}
