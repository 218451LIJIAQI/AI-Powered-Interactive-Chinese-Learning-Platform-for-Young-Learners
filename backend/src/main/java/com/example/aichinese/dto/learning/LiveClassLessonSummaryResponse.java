package com.example.aichinese.dto.learning;

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
 * <p>This DTO is null when the live class is package-linked or
 * stand-alone — see the lesson/package XOR rule on
 * {@link CreateLiveClassRequest}.</p>
 */
public record LiveClassLessonSummaryResponse(
        Long lessonId,
        String lessonTitle,
        String learningType,
        String accessType) {
}
