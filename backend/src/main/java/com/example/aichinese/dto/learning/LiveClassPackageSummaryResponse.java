package com.example.aichinese.dto.learning;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Compact course-package reference embedded inside
 * {@link LiveClassResponse#coursePackage()} when the live class is
 * linked to a course package rather than a single lesson.
 *
 * <p>Mirrors {@link LiveClassLessonSummaryResponse} field-for-field
 * but points at the package row instead. Package-linked live
 * classes appear under the student's "My subscribed packages"
 * sidebar and are gated by the package's
 * {@link #accessType} (typically PAID, since packages are how
 * paid content is bundled).</p>
 *
 * <p>{@link LiveClassResponse#coursePackage()} is {@code null} when
 * the live class is lesson-linked or stand-alone — see the
 * lesson/package XOR rule on {@link CreateLiveClassRequest}.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #packageId} and {@link #packageTitle} are
 *       non-{@code null} — when the producer constructs this DTO
 *       it has just dereferenced a real
 *       {@link com.example.aichinese.entity.CoursePackage} that
 *       the schema guarantees has both columns populated.</li>
 *   <li>{@link #learningType} and {@link #accessType} may be
 *       {@code null} for legacy package rows authored before the
 *       enum columns were introduced.</li>
 * </ul>
 *
 * @param packageId    {@code CoursePackage} identifier (drives the
 *                     "Open package" deep-link); never {@code null}
 * @param packageTitle human-readable package title shown on the
 *                     live-class card; never {@code null}
 * @param learningType enum-name string from
 *                     {@link com.example.aichinese.entity.enums.LearningType};
 *                     {@code null} on legacy rows
 * @param accessType   enum-name string from
 *                     {@link com.example.aichinese.entity.enums.AccessType};
 *                     {@code null} on legacy rows
 */
@JsonPropertyOrder({ "packageId", "packageTitle", "learningType", "accessType" })
public record LiveClassPackageSummaryResponse(
        Long packageId,
        String packageTitle,
        String learningType,
        String accessType) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the live-class card renders
     * unconditionally. {@link #learningType} and
     * {@link #accessType} are intentionally permitted to be
     * {@code null} per the invariants documented above.
     */
    public LiveClassPackageSummaryResponse {
        Objects.requireNonNull(packageId, "packageId must not be null");
        Objects.requireNonNull(packageTitle, "packageTitle must not be null");
    }
}
