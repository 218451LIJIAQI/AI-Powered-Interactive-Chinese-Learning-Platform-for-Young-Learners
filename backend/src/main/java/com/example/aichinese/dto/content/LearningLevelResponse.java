package com.example.aichinese.dto.content;

/**
 * Reference taxonomy row for one
 * {@link com.example.aichinese.entity.LearningLevel} (e.g.
 * "Beginner / HSK 1", "Intermediate / HSK 3-4"), returned by:
 * <ul>
 *   <li>{@code GET /api/v1/public/levels} — full catalogue for the
 *       public course-discovery UI.</li>
 *   <li>Embedded inside content responses
 *       ({@link LearningModuleResponse}, {@link LessonResponse},
 *       {@link CoursePackageResponse},
 *       {@link ContentSubmissionResponse},
 *       {@link PublicCourseSummaryResponse}, etc.) so the front-end
 *       can render the level chip without a follow-up lookup.</li>
 * </ul>
 *
 * <p>This is the read-only projection of
 * {@link com.example.aichinese.entity.LearningLevel}. Levels are
 * curated by admins and effectively static at the wire-format level
 * — the front-end caches them locally and refreshes on app
 * version change.</p>
 *
 * <p>{@link #name} is the public-facing label (i18n-ready in a
 * future iteration; currently English-only). {@link #description}
 * is the optional explanatory blurb shown on hover.</p>
 */
public record LearningLevelResponse(
        Long id,
        String name,
        String description) {
}
