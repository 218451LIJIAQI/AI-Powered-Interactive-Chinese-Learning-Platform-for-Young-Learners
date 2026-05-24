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
 * is emitted in {@code ORDER BY sort_order ASC, created_at ASC}.</p>
 *
 * <p>The canonical-form constructor null-coalesces both {@link #tags}
 * and {@link #questions} to empty lists and wraps non-{@code null}
 * lists in unmodifiable defensive copies. This matches the frontend
 * types ({@code tags: string[]},
 * {@code questions: AdminStoryQuestionResponse[]} — neither expects
 * {@code null}) and prevents downstream callers from mutating a
 * cached response.</p>
 *
 * <p>See {@link AdminActivitySummaryResponse} for the shared metadata
 * vocabulary contract.</p>
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
