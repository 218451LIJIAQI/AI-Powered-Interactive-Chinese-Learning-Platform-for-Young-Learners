package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
 *       the class is currently in
 *       {@link com.example.aichinese.entity.enums.LiveClassStatus#SCHEDULED}
 *       or {@code ONGOING}; drives the "Join meeting" button.</li>
 *   <li>{@link #meetingLinkVisible} — controls whether
 *       {@link #meetingLink} is populated. The server redacts the
 *       link until the lobby window opens to prevent students from
 *       sharing it ahead of time.</li>
 * </ul>
 *
 * <h2>Identity / linkage</h2>
 * <p>{@link #teacher} is always present;
 * {@link #lesson} and {@link #coursePackage} are mutually
 * exclusive — at most one will be non-null. See the lesson/package
 * XOR rule on {@link CreateLiveClassRequest}.</p>
 *
 * <p>{@link #enrolledCount} is a denormalised count for the "12 / 30
 * enrolled" capacity indicator; it always reflects ENROLLED rows
 * only (cancellations are excluded).</p>
 */
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
}
