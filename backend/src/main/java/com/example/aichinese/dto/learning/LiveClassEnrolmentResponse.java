package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One enrolment row returned by
 * {@code GET /api/v1/teachers/live-classes/{id}/enrolments} — the
 * teacher's roster screen for one live class.
 *
 * <p>Shows the full enrolment lifecycle for a student-class pair —
 * including cancelled rows so the teacher can see the historical
 * roster, not just the currently-enrolled cohort. The
 * {@link #enrolmentStatus} string carries the enum name from
 * {@link com.example.aichinese.entity.enums.LiveClassEnrolmentStatus}
 * ("ENROLLED", "CANCELLED", "ATTENDED", "MISSED").</p>
 *
 * <p>Lifecycle timestamps carry the audit trail:</p>
 * <ul>
 *   <li>{@link #enrolledAt} — stamped by the service on every
 *       enrol/re-enrol; nominally always set, but
 *       {@link com.example.aichinese.entity.LiveClassEnrolment}'s
 *       column is technically nullable, so legacy rows may be
 *       {@code null}.</li>
 *   <li>{@link #cancelledAt} — set when the student cancels;
 *       preserved (not cleared) on re-enrol so the audit trail
 *       remains complete.</li>
 *   <li>{@link #attendedAt} — set when the student joins via
 *       {@link JoinLiveClassResponse}; doubles as the attendance
 *       record.</li>
 * </ul>
 *
 * <p>{@link #studentName} and {@link #studentEmail} are
 * denormalised from the {@code users} row so the teacher can
 * compose follow-up emails without an extra lookup. Only the
 * teacher who owns the live class can access this endpoint
 * (see {@code LiveLearningAccessService.requireTeacherOwnedLiveClass}).</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id}, {@link #liveClassId}, {@link #studentId} and
 *       {@link #studentName} are non-{@code null} — the roster
 *       table renders them unconditionally and the deep-link uses
 *       the ids.</li>
 *   <li>{@link #studentEmail} may be {@code null} for legacy
 *       student-only profiles that have no linked {@code User}.</li>
 *   <li>{@link #enrolmentStatus} is nominally non-null (entity
 *       defaults to {@code ENROLLED} with {@code nullable=false})
 *       but the producer defensively serialises {@code null} for
 *       a degenerate row.</li>
 *   <li>{@link #cancelledAt} and {@link #attendedAt} are
 *       {@code null} until the corresponding lifecycle transition
 *       occurs.</li>
 * </ul>
 *
 * @param id              {@code LiveClassEnrolment} identifier;
 *                        never {@code null}
 * @param liveClassId     parent {@code LiveClass} identifier; never
 *                        {@code null}
 * @param studentId       enrolled {@code StudentProfile}
 *                        identifier; never {@code null}
 * @param studentName     learner display name (falls back to the
 *                        profile display name if no linked
 *                        {@code User}); never {@code null}
 * @param studentEmail    linked-user e-mail for "follow up" CTA;
 *                        {@code null} for unlinked profiles
 * @param enrolmentStatus enum-name string from
 *                        {@link com.example.aichinese.entity.enums.LiveClassEnrolmentStatus};
 *                        nominally non-null
 * @param enrolledAt      wall-clock instant the enrolment was
 *                        first recorded (Asia/Singapore zone);
 *                        {@code null} only for legacy rows
 * @param cancelledAt     wall-clock instant the student cancelled;
 *                        {@code null} when never cancelled
 * @param attendedAt      wall-clock instant the student joined the
 *                        meeting; {@code null} when never attended
 */
@JsonPropertyOrder({
        "id",
        "liveClassId",
        "studentId",
        "studentName",
        "studentEmail",
        "enrolmentStatus",
        "enrolledAt",
        "cancelledAt",
        "attendedAt"
})
public record LiveClassEnrolmentResponse(
        Long id,
        Long liveClassId,
        Long studentId,
        String studentName,
        String studentEmail,
        String enrolmentStatus,
        LocalDateTime enrolledAt,
        LocalDateTime cancelledAt,
        LocalDateTime attendedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the roster table renders
     * unconditionally. Optional and lifecycle-staged fields are
     * intentionally permitted to be {@code null} per the invariants
     * documented above.
     */
    public LiveClassEnrolmentResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(liveClassId, "liveClassId must not be null");
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(studentName, "studentName must not be null");
    }
}
