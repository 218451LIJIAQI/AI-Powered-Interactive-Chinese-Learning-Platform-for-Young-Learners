package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * One row of the teacher's "my students" roster returned by
 * {@code GET /api/v1/teachers/students} (paginated, default size 20).
 *
 * <p>The roster lists every {@code StudentProfile} that has ever
 * been linked to this teacher through a lesson, live class, quiz
 * attempt or feedback record — the join logic lives in
 * {@code TeacherStudentService.getTeacherStudents}, which aggregates
 * across these sources into one row per student.</p>
 *
 * <h2>Roster ordering</h2>
 * <p>Server-side sort is
 * {@link #latestActivityAt} {@code DESC NULLS LAST}, then
 * {@link #fullName} {@code ASC} (case-insensitive). Recently active
 * students rise to the top so the teacher can prioritise follow-up;
 * never-active students sink to the bottom alphabetically.</p>
 *
 * <h2>{@link #atRisk} derivation</h2>
 * <p>Computed server-side as the boolean OR of three triggers
 * (see {@code TeacherStudentService.StudentAggregate.toResponse}):</p>
 * <ul>
 *   <li>{@link #progressPercent} {@code > 0 && < 60} — the student
 *       has started lessons but is below the 60% completion bar.</li>
 *   <li>{@link #quizAveragePercent} {@code != null && < 70} — the
 *       student has submitted at least one quiz and is below the
 *       70% pass mark.</li>
 *   <li>{@link #weakAreas} non-empty — the teacher has logged
 *       feedback against this student.</li>
 * </ul>
 * <p>A student with zero activity ({@code progressPercent == 0},
 * {@code quizAveragePercent == null}, {@code weakAreas} empty) is
 * <em>not</em> marked at risk — they need outreach, not remediation.
 * Primitive {@code boolean} so the JSON always emits a value.</p>
 *
 * <h2>{@link #weakAreas}</h2>
 * <p>Server-capped to 4 entries (LinkedHashSet preserves order) so
 * the front-end can render the chips inline without overflow.
 * Derived from the distinct {@code feedbackType} values across the
 * student's {@code teacher_feedback} rows.</p>
 *
 * <h2>Why two id columns and two name fields</h2>
 * <p>{@link #studentId} is the {@code student_profiles.id} (used by
 * student-progress endpoints), {@link #userId} is the underlying
 * {@code users.id} (used by messaging / notification endpoints) —
 * the front-end needs both.</p>
 *
 * <p>{@link #fullName} and {@link #displayName} both carry display
 * names but from different rows:</p>
 * <ul>
 *   <li>{@link #fullName} — populated from {@code users.display_name}
 *       when a user account is linked, falling back to
 *       {@code student_profiles.display_name} otherwise. The record
 *       field is named "full name" for historical reasons (the
 *       {@code users} table has no {@code full_name} column); think
 *       of it as "the canonical name on the platform login".</li>
 *   <li>{@link #displayName} — always
 *       {@code student_profiles.display_name}, the nickname the child
 *       prefers in class. Surfaced separately so the teacher's
 *       student-detail screens can address the child by their
 *       classroom name even when it differs from the parent's login.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the five fields
 * the service's {@code StudentAggregate.toResponse} guarantees
 * ({@link #studentId}, {@link #fullName}, {@link #displayName},
 * {@link #progressPercent}, {@link #joinedAt}) and defensively copies
 * {@link #weakAreas}. {@link #userId}, {@link #email},
 * {@link #currentLevel}, {@link #quizAveragePercent},
 * {@link #latestFeedbackType}, {@link #latestFeedbackAt} and
 * {@link #latestActivityAt} remain nullable per the documented
 * contextual rules above.</p>
 *
 * @param studentId             {@code student_profiles.id}; never
 *                              {@code null}
 * @param userId                {@code users.id}; nullable when the
 *                              student row exists without a user
 *                              account (legacy data)
 * @param fullName              {@code users.display_name} when linked,
 *                              otherwise the student's own display
 *                              name (see class-level Javadoc); never
 *                              {@code null}
 * @param email                 contact email from {@code users.email};
 *                              nullable when no user account is linked
 * @param displayName           classroom nickname from
 *                              {@code student_profiles.display_name};
 *                              never {@code null}
 * @param currentLevel          name of the student's current learning
 *                              level; nullable when unset
 * @param progressPercent       average lesson completion 0-100; never
 *                              {@code null} (defaults to {@code 0})
 * @param quizAveragePercent    average quiz score 0-100; {@code null}
 *                              when no quizzes submitted yet
 * @param atRisk                derived risk flag (see class Javadoc)
 * @param weakAreas             distinct feedback types, capped at 4;
 *                              never {@code null}; the wrapped list
 *                              is unmodifiable
 * @param latestFeedbackType    feedback type of the most recent
 *                              feedback entry; nullable
 * @param latestFeedbackAt      timestamp of the most recent feedback;
 *                              nullable
 * @param latestActivityAt      timestamp of the most recent activity
 *                              of any kind; drives roster sorting;
 *                              nullable for never-active students
 * @param joinedAt              {@code student_profiles.created_at};
 *                              never {@code null}
 */
@JsonPropertyOrder({
        "studentId",
        "userId",
        "fullName",
        "email",
        "displayName",
        "currentLevel",
        "progressPercent",
        "quizAveragePercent",
        "atRisk",
        "weakAreas",
        "latestFeedbackType",
        "latestFeedbackAt",
        "latestActivityAt",
        "joinedAt"
})
public record TeacherStudentSummaryResponse(
        Long studentId,
        Long userId,
        String fullName,
        String email,
        String displayName,
        String currentLevel,
        Integer progressPercent,
        Double quizAveragePercent,
        boolean atRisk,
        List<String> weakAreas,
        String latestFeedbackType,
        LocalDateTime latestFeedbackAt,
        LocalDateTime latestActivityAt,
        LocalDateTime joinedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the five
     * fields the service's {@code StudentAggregate.toResponse}
     * guarantees, defensively copies {@link #weakAreas}
     * (with {@code null} → {@link List#of()} normalisation) so
     * external mutation cannot leak into an already-serialised payload,
     * and leaves the contextual nullable fields untouched.
     */
    public TeacherStudentSummaryResponse {
        Objects.requireNonNull(studentId, "studentId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        Objects.requireNonNull(progressPercent, "progressPercent must not be null");
        Objects.requireNonNull(joinedAt, "joinedAt must not be null");
        weakAreas = weakAreas == null ? List.of() : List.copyOf(weakAreas);
    }
}
