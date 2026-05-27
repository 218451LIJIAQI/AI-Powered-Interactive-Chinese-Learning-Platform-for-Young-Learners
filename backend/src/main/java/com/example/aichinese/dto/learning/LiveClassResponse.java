package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Detail payload for one live class, returned by:
 * <ul>
 *   <li>{@code GET /api/v1/students/live-classes} (paginated list)
 *       and {@code GET /api/v1/students/live-classes/{id}} (detail)</li>
 *   <li>{@code POST /api/v1/students/live-classes/{id}/enrol} and
 *       {@code POST .../cancel-enrolment} (returns the refreshed
 *       state)</li>
 *   <li>{@code POST}, {@code PUT}, {@code PATCH} on the teacher
 *       endpoints under {@code /api/v1/teachers/live-classes}
 *       (returns the freshly-saved state)</li>
 * </ul>
 *
 * <p>Carries the full editorial header plus a small bundle of
 * server-derived booleans that drive the student-facing UI:</p>
 *
 * <h2>Derived booleans</h2>
 * <ul>
 *   <li>{@link #enrolled} — {@code true} when the calling student
 *       currently has an
 *       {@link com.example.aichinese.entity.enums.LiveClassEnrolmentStatus#ENROLLED}
 *       row for this class.</li>
 *   <li>{@link #accessAllowed} — {@code true} when the student's
 *       package / lesson access grants them this class
 *       (free-tier classes are always {@code true}; paid classes
 *       require an active subscription).</li>
 *   <li>{@link #canEnrol} — {@code !enrolled && accessAllowed}; the
 *       front-end uses this to enable/disable the "Enrol" button.</li>
 *   <li>{@link #canJoin} — {@code enrolled && accessAllowed} AND
 *       the class is in its protected join window; drives the
 *       "Join meeting" button.</li>
 *   <li>{@link #meetingLinkVisible} — controls whether
 *       {@link #meetingLink} is populated. The server redacts the
 *       link until the lobby window opens to prevent students from
 *       sharing it ahead of time.</li>
 * </ul>
 *
 * <h2>Identity / linkage</h2>
 * <p>{@link #teacher} is nominally always present (the entity column
 * has no foreign-key {@code NOT NULL} constraint, so a degenerate
 * historic row may surface {@code null}); {@link #lesson} and
 * {@link #coursePackage} are mutually exclusive — at most one will
 * be non-null. See the lesson/package XOR rule on
 * {@link CreateLiveClassRequest}.</p>
 *
 * <p>{@link #enrolledCount} is a denormalised count for the "12 / 30
 * enrolled" capacity indicator; it always reflects ENROLLED rows
 * only (cancellations are excluded), and is non-negative by the
 * canonical constructor.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #id} is non-{@code null} — every row projected from
 *       a persisted {@link com.example.aichinese.entity.LiveClass}
 *       has a primary-key id.</li>
 *   <li>{@link #enrolledCount} is non-negative (guarded by the
 *       canonical constructor).</li>
 *   <li>All other reference fields may be {@code null} for legacy
 *       data or by design (the meeting link is redacted when
 *       {@link #meetingLinkVisible} is {@code false};
 *       {@link #lesson} and {@link #coursePackage} are mutually
 *       exclusive).</li>
 * </ul>
 *
 * @param id                 {@link com.example.aichinese.entity.LiveClass}
 *                           identifier; never {@code null}
 * @param title              short session title; nominally non-null
 * @param description        long-form description; may be
 *                           {@code null}
 * @param meetingPlatform    enum-name string from
 *                           {@link com.example.aichinese.entity.enums.MeetingPlatform};
 *                           nominally non-null
 * @param liveClassStatus    enum-name string from
 *                           {@link com.example.aichinese.entity.enums.LiveClassStatus};
 *                           nominally non-null
 * @param startTime          scheduled start instant
 *                           (Asia/Singapore); may be {@code null}
 *                           on partially-scheduled rows
 * @param endTime            scheduled end instant
 *                           (Asia/Singapore); may be {@code null}
 *                           on partially-scheduled rows
 * @param durationMinutes    derived duration (end − start) in
 *                           minutes; {@code null} when either
 *                           endpoint is missing
 * @param maxParticipants    optional soft cap on enrolments;
 *                           {@code null} for uncapped classes
 * @param enrolledCount      current count of {@code ENROLLED} rows;
 *                           non-negative
 * @param enrolled           {@code true} when the calling student
 *                           has an {@code ENROLLED} row for this
 *                           class
 * @param canEnrol           {@code !enrolled && accessAllowed}
 * @param accessAllowed      {@code true} when the student's
 *                           package / lesson access grants this
 *                           class
 * @param canJoin            {@code true} when the student is
 *                           enrolled, has access, and the class is
 *                           {@code SCHEDULED}/{@code ONGOING}
 * @param meetingLinkVisible {@code true} when the lobby window is
 *                           open and the link may be returned
 * @param meetingLink        full {@code http(s)://} URL when
 *                           {@link #meetingLinkVisible} is
 *                           {@code true}, otherwise {@code null}
 * @param teacher            embedded teacher summary; nominally
 *                           non-null
 * @param lesson             embedded lesson summary when the live
 *                           class is lesson-linked; mutually
 *                           exclusive with {@link #coursePackage}
 * @param coursePackage      embedded package summary when the live
 *                           class is package-linked; mutually
 *                           exclusive with {@link #lesson}
 * @param createdAt          row creation instant
 *                           (Asia/Singapore); never {@code null}
 *                           for persisted rows
 * @param updatedAt          last-modification instant
 *                           (Asia/Singapore); never {@code null}
 *                           for persisted rows
 */
@JsonPropertyOrder({
        "id",
        "title",
        "description",
        "meetingPlatform",
        "liveClassStatus",
        "startTime",
        "endTime",
        "durationMinutes",
        "maxParticipants",
        "enrolledCount",
        "enrolled",
        "canEnrol",
        "accessAllowed",
        "canJoin",
        "meetingLinkVisible",
        "meetingLink",
        "teacher",
        "lesson",
        "coursePackage",
        "createdAt",
        "updatedAt"
})
public record LiveClassResponse(
        Long id,
        String title,
        String description,
        String meetingPlatform,
        String liveClassStatus,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer durationMinutes,
        Integer maxParticipants,
        long enrolledCount,
        boolean enrolled,
        boolean canEnrol,
        boolean accessAllowed,
        boolean canJoin,
        boolean meetingLinkVisible,
        String meetingLink,
        LiveClassTeacherSummaryResponse teacher,
        LiveClassLessonSummaryResponse lesson,
        LiveClassPackageSummaryResponse coursePackage,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity field that the front-end uses as a list key, and
     * enforces the non-negative invariant on {@link #enrolledCount}
     * (a regression that propagates a negative count would skew
     * the "X / Y enrolled" capacity indicator). All other reference
     * fields are intentionally permitted to be {@code null} per the
     * invariants documented above.
     */
    public LiveClassResponse {
        Objects.requireNonNull(id, "id must not be null");
        if (enrolledCount < 0) {
            throw new IllegalArgumentException("enrolledCount must be non-negative, was " + enrolledCount);
        }
    }
}
