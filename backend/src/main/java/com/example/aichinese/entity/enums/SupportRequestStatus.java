package com.example.aichinese.entity.enums;

import java.util.Optional;

/** Where a Help & support request stands. */
public enum SupportRequestStatus {

    /** Waiting for support: a new request, or the requester replied. */
    OPEN,

    /** Support replied; the requester's turn. Closes by itself 7 days after the last support reply. */
    ANSWERED,

    /** Marked solved by the requester or support, or closed by itself. Read-only. */
    SOLVED;

    public static Optional<SupportRequestStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(SupportRequestStatus.class, value);
    }
}
