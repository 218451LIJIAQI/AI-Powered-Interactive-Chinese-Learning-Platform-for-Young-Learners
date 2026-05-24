package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
 * ("ENROLLED", "CANCELLED", "ATTENDED").</p>
 *
 * <p>Lifecycle timestamps carry the audit trail:</p>
 * <ul>
 *   <li>{@link #enrolledAt} — set on every enrol/re-enrol; never
 *       {@code null}.</li>
 *   <li>{@link #cancelledAt} — set when the student cancels;
 *       cleared when they re-enrol.</li>
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
 */
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
}
