package com.example.aichinese.dto.learning;

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
 * <p>This DTO is null when the live class is lesson-linked or
 * stand-alone — see the lesson/package XOR rule on
 * {@link CreateLiveClassRequest}.</p>
 */
public record LiveClassPackageSummaryResponse(
        Long packageId,
        String packageTitle,
        String learningType,
        String accessType) {
}
