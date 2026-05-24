package com.example.aichinese.dto.commerce;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/admin/payout-requests/{id}/reject}
 * — the admin declines a teacher's payout request and supplies a
 * mandatory reason that the teacher sees on
 * {@link TeacherPayoutRequestResponse#decisionReason()}.
 *
 * <p>The other admin payout actions take no body:</p>
 * <ul>
 *   <li>{@code POST .../approve} — flips to
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#APPROVED}
 *       (admin approved; bank transfer pending).</li>
 *   <li>{@code POST .../mark-paid} — terminal transition to
 *       {@link com.example.aichinese.entity.enums.PayoutStatus#PAID}
 *       once the bank transfer settles.</li>
 * </ul>
 *
 * <p>{@link #reason} is {@link NotBlank} so the teacher always
 * receives a justification when their withdrawal is refused.
 * Field-for-field identical to {@link RefundDecisionRequest}; the
 * separate type exists so the refund and payout APIs can evolve
 * independently.</p>
 */
public record PayoutDecisionRequest(
        @NotBlank(message = "Decision reason is required.")
        @Size(max = 1000, message = "Decision reason must be 1000 characters or fewer.")
        String reason) {
}
