package com.example.aichinese.dto.admin;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 * the account after deactivation. The action is also audit-logged
 * via {@code AuditLogService.record} so a future review can attribute
 * the change to the calling admin.</p>
 *
 * <p><b>Self-deactivation guard.</b> The service layer rejects
 * deactivation when the calling admin matches the target user, so an
 * admin cannot accidentally lock themselves out of the platform. The
 * guard lives in the service rather than the DTO because the DTO has
 * no knowledge of the calling principal.</p>
 *
 * <h2>Wire format</h2>
 * <p>The single-field property order is locked via
 * {@link JsonPropertyOrder} for forward-compatibility — any future
 * fields added to this DTO (e.g. an audit-friendly {@code reason})
 * will append cleanly after {@code active} rather than reorder
 * relative to consumers that already pin on field positions.</p>
 *
 * @param active target activation state; required (validation rejects
 *               {@code null})
 */
@JsonPropertyOrder({ "active" })
public record AdminUserStatusUpdateRequest(
        @NotNull(message = "Active flag is required.")
        Boolean active) {
}
