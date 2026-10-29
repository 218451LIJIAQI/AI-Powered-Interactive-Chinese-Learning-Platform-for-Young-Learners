package com.example.aichinese.dto.admin;

import java.util.Objects;

/** The deactivated account and what the deactivation did, for the confirmation notice. */
public record AdminDeactivationResponse(
        AdminUserDetailResponse user,
        long cancelledSessions,
        long endedBookings,
        long itemsBackToDraft) {

    public AdminDeactivationResponse {
        Objects.requireNonNull(user, "user must not be null");
    }
}
