package com.example.aichinese.entity.enums;

import java.util.Optional;

/** Where a Help & support request stands. */
public enum SupportRequestStatus {

    /** Waiting for support: a new request, or a solved one reopened before support replied. */
    OPEN,

    /** Support replied; the requester's turn. Closes by itself 7 days after the last support reply. */
    ANSWERED,

    /** Marked solved, or closed by itself. It takes no reply; support can reopen it (the Undo of "Mark as solved"). */
    SOLVED;

    public static Optional<SupportRequestStatus> tryFrom(String value) {
        return EnumLookups.tryFrom(SupportRequestStatus.class, value);
    }
}
