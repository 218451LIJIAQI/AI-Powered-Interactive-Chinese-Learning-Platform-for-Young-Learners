package com.example.aichinese.dto.admin.activity;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Aggregate-root response payload returned by the admin story
 * endpoints under {@code /api/v1/admin/activities/story-activities}.
 *
 * <p>Mirrors {@link AdminStoryActivityRequest} field-for-field plus
 * server-computed audit metadata. The embedded {@link #questions} list
 * is emitted in {@code ORDER BY sort_order ASC, created_at ASC} (see
 * {@code StoryQuestionRepository}) so admin and student renderers see
 * the same canonical ordering before optional shuffling.</p>
 *
 * <h2>Field origin</h2>
 * <p>The first seven components ({@link #title}…{@link #imageUrl})
 * mirror the request payload verbatim. The remaining components are
 * server-computed and are never accepted from client input.</p>
 *
 * <p>The canonical-form constructor null-coalesces both {@link #tags}
 * and {@link #questions} to empty lists and wraps non-{@code null}
 * lists in unmodifiable defensive copies via
 * {@link List#copyOf(java.util.Collection)}. This matches the
 * frontend types ({@code tags: string[]},
 * {@code questions: AdminStoryQuestionResponse[]} — neither expects
 * {@code null}) and prevents downstream callers from mutating a
 * cached response. {@code List.copyOf} additionally fails fast with
 * {@link NullPointerException} on {@code null} elements — an
 * intentional invariant given that the mapper only emits well-formed
 * children.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
 *
 * @param id                    database-assigned primary key
 * @param title                 story title
 * @param instructions          optional learner-facing instructions
 * @param storyText             Mandarin narrative
 * @param pinyin                optional pinyin transliteration
 * @param translation           optional English/Bahasa translation
 * @param imageUrl              optional cover image URL
 * @param status                editorial lifecycle state
 * @param hskLevel              HSK 1-6 band; nullable
 * @param ageBand               target age cohort; nullable
 * @param difficulty            easy/medium/hard tier; nullable
 * @param topic                 free-form short topic label; nullable
 * @param tags                  free-form tags; never {@code null}
 *                              (empty list when none); unmodifiable
 * @param xpReward              base XP on completion
 * @param estimatedSeconds      UX hint; nullable
 * @param createdByUserId       admin who authored the row; nullable
 *                              for legacy/seed data
 * @param createdByDisplayName  display name for the author user id
 * @param createdAt             creation timestamp
 * @param updatedAt             last-modified timestamp
 * @param questions             child comprehension prompts in canonical
 *                              sort order; never {@code null};
 *                              unmodifiable
 */
public record AdminStoryActivityResponse(
        Long id,
        String title,
        String instructions,
        String storyText,
        String pinyin,
        String translation,
        String imageUrl,
        ContentStatus status,
        Integer hskLevel,
        AgeBand ageBand,
        ActivityDifficulty difficulty,
        String topic,
        List<String> tags,
        Integer xpReward,
        Integer estimatedSeconds,
        Long createdByUserId,
        String createdByDisplayName,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<AdminStoryQuestionResponse> questions) {

    public AdminStoryActivityResponse {
        tags = tags == null ? List.of() : List.copyOf(tags);
        questions = questions == null ? List.of() : List.copyOf(questions);
    }
}
