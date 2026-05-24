package com.example.aichinese.dto.content;

/**
 * Reference taxonomy row for one
 * {@link com.example.aichinese.entity.LearningCategory} (e.g.
 * "Daily Conversation", "Travel Vocabulary",
 * "Chinese for Young Children"), returned by:
 * <ul>
 *   <li>{@code GET /api/v1/public/categories} — full catalogue for
 *       the public course-discovery UI.</li>
 *   <li>Embedded inside content responses parallel to
 *       {@link LearningLevelResponse}.</li>
 * </ul>
 *
 * <p>This is the read-only projection of
 * {@link com.example.aichinese.entity.LearningCategory}. Categories
 * are admin-curated and serve as the second axis (alongside
 * {@link LearningLevelResponse}) for course filtering. Mirrors the
 * level DTO field-for-field on purpose — the front-end binds the
 * same chip component to either taxonomy.</p>
 */
public record LearningCategoryResponse(
        Long id,
        String name,
        String description) {
}
