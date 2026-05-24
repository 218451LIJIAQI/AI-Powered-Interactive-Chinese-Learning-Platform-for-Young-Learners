package com.example.aichinese.dto.admin;

import jakarta.validation.constraints.NotNull;

/**
 * Activate-or-deactivate payload for
 * {@code PATCH /api/v1/admin/users/{userId}/status}.
 *
 * <p>{@link #active} is the boxed {@link Boolean} (not the primitive)
 * deliberately so {@link NotNull} can distinguish "field absent" from
 * "field explicitly false". A JSON body of {@code {"active": false}}
 * deactivates the account; an absent field returns
 * {@code 400 Bad Request} via Bean Validation.</p>
 *
 * <p><b>Side effects of deactivation.</b> Beyond setting
 * {@code User.active = false}, the service rotates the user's
 * {@code sessionVersion} so every previously-issued JWT is invalidated
 * atomically — an attacker holding a stolen token cannot keep using
 * the account after deactivation.</p>
 *
 * @param active target activation state; required (validation rejects
 *               {@code null})
 */
public record AdminUserStatusUpdateRequest(
        @NotNull(message = "Active flag is required.")
        Boolean active) {
}
