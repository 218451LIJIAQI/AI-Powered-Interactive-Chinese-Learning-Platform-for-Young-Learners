package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Compact lesson reference embedded inside
 * {@link LiveClassResponse#lesson()} when the live class is linked
 * to a specific lesson.
 *
 * <p>Surfaces just enough metadata for the front-end to show
 * "Live class for Lesson 3: Greetings" with a back-link to the
 * lesson player. {@link #learningType} carries the enum name from
 * {@link com.example.aichinese.entity.enums.LearningType}
 * ("VIDEO", "INTERACTIVE", etc.) so the front-end picks the right
 * lesson icon.</p>
 *
 * <p>{@link #accessType} carries the enum name from
 * {@link com.example.aichinese.entity.enums.AccessType}
 * ("FREE", "PAID") so the student-facing card can show a paywall
 * badge when needed. Note that the lesson's access type may
 * <em>differ</em> from the live class's effective access (a free
 * lesson may have a paid live session attached); the resolution
 * happens server-side in
 * {@code StudentLearningAccessService.hasLiveClassAccess}.</p>
 *
 * <p>{@link LiveClassResponse#lesson()} is {@code null} when the
 * live class is package-linked or stand-alone — see the
 * lesson/package XOR rule on {@link CreateLiveClassRequest}.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #lessonId} and {@link #lessonTitle} are
 *       non-{@code null} — when the producer constructs this DTO
 *       it has just dereferenced a real
 *       {@link com.example.aichinese.entity.Lesson} that the
 *       schema guarantees has both columns populated.</li>
 *   <li>{@link #learningType} and {@link #accessType} may be
 *       {@code null} for legacy lesson rows authored before the
 *       enum columns were introduced.</li>
 * </ul>
 *
 * @param lessonId     {@code Lesson} identifier (drives the
 *                     "Open lesson" deep-link); never {@code null}
 * @param lessonTitle  human-readable lesson title shown on the
 *                     live-class card; never {@code null}
 * @param learningType enum-name string from
 *                     {@link com.example.aichinese.entity.enums.LearningType};
 *                     {@code null} on legacy rows
 * @param accessType   enum-name string from
 *                     {@link com.example.aichinese.entity.enums.AccessType};
 *                     {@code null} on legacy rows
 */
@JsonPropertyOrder({ "lessonId", "lessonTitle", "learningType", "accessType" })
public record LiveClassLessonSummaryResponse(
        Long lessonId,
        String lessonTitle,
        String learningType,
        String accessType) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the live-class card renders
     * unconditionally. {@link #learningType} and
     * {@link #accessType} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public LiveClassLessonSummaryResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(lessonTitle, "lessonTitle must not be null");
    }
}
