package com.example.aichinese.dto.learning;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Aggregate-root payload for the student-facing lesson player —
 * returned by {@code GET /api/v1/students/lessons/{lessonId}}.
 *
 * <p>Carries everything the player needs in a single round-trip:
 * the lesson's authored content, the student's progress against it,
 * and every embedded learning artefact (live classes, recorded
 * videos) attached to it. This DTO is the central pivot of the
 * student learning experience; quizzes, by contrast, are fetched
 * separately via {@code /api/v1/students/quizzes?lessonId=...}.</p>
 *
 * <h2>Derived flags</h2>
 * <ul>
 *   <li>{@link #completionPercent} — average lesson completion
 *       percentage across all the student's progress signals
 *       ({@link com.example.aichinese.entity.LessonProgress}, video
 *       progress, etc.) computed by
 *       {@code StudentLearningProgressService.lessonCompletionPercent};
 *       clamped to {@code [0, 100]} by the canonical constructor.</li>
 *   <li>{@link #completed} — derived as
 *       {@link #completionPercent} {@code >= 100}; primitive
 *       {@code boolean} so the JSON wire format always emits a
 *       value.</li>
 *   <li>{@link #locked} — {@code true} when the student does NOT
 *       currently have access to this lesson's content (paid lesson
 *       on a free plan, or membership lapsed). The endpoint still
 *       returns {@link #title}/{@link #description} as a teaser
 *       even when locked, but {@link #basicContent} is stripped
 *       server-side. Note that the controller will still 200-OK
 *       this response even on locked lessons — the player UI uses
 *       the flag to render a paywall overlay.</li>
 * </ul>
 *
 * <h2>Aggregate child lists</h2>
 * <ul>
 *   <li>{@link #liveClasses} — live classes whose
 *       {@code lessonId == this.id}. Each carries its own access
 *       and enrolment booleans (see {@link LiveClassResponse}).
 *       Defensively copied by the canonical constructor.</li>
 *   <li>{@link #recordedVideos} — published, non-archived recorded
 *       videos for this lesson, in {@code sortOrder} ascending.
 *       Defensively copied by the canonical constructor.</li>
 * </ul>
 *
 * <h2>{@link #learningType}</h2>
 * <p>Enum-string from
 * {@link com.example.aichinese.entity.enums.LearningType}
 * ("VIDEO", "INTERACTIVE", "TEXT", etc.) so the player UI can pick
 * the right rendering pipeline.</p>
 *
 * <h2>Invariants</h2>
 * <ul>
 *   <li>{@link #lessonId} and {@link #title} are non-{@code null}
 *       — they identify the row the front-end deep-links to and
 *       render unconditionally even when {@link #locked} is
 *       {@code true}.</li>
 *   <li>{@link #completionPercent} is clamped into {@code [0, 100]}.</li>
 *   <li>{@link #liveClasses} and {@link #recordedVideos} are
 *       non-{@code null} (empty list rather than {@code null}) and
 *       defensively copied.</li>
 * </ul>
 *
 * @param lessonId                 {@code Lesson} identifier; never
 *                                 {@code null}
 * @param title                    lesson title shown atop the
 *                                 player; never {@code null}
 * @param description              short lesson summary; may be
 *                                 {@code null}
 * @param basicContent             authored lesson body text;
 *                                 stripped to {@code null} when
 *                                 {@link #locked} is {@code true}
 * @param estimatedDurationMinutes optional curator-supplied duration
 *                                 estimate
 * @param completionPercent        0–100 rolled-up completion;
 *                                 defensively clamped
 * @param completed                {@code true} when
 *                                 {@link #completionPercent}
 *                                 reaches 100
 * @param locked                   {@code true} when the student
 *                                 has no access to the body
 * @param learningType             enum-name string from
 *                                 {@link com.example.aichinese.entity.enums.LearningType}
 * @param liveClasses              attached live classes; never
 *                                 {@code null}
 * @param recordedVideos           attached recorded videos; never
 *                                 {@code null}
 */
@JsonPropertyOrder({
        "lessonId",
        "title",
        "description",
        "basicContent",
        "estimatedDurationMinutes",
        "completionPercent",
        "completed",
        "locked",
        "learningType",
        "liveClasses",
        "recordedVideos"
})
public record StudentLessonPlayerResponse(
        Long lessonId,
        String title,
        String description,
        String basicContent,
        Integer estimatedDurationMinutes,
        int completionPercent,
        boolean completed,
        boolean locked,
        String learningType,
        List<LiveClassResponse> liveClasses,
        List<RecordedLessonVideoResponse> recordedVideos) {

    /**
     * Defensive canonical constructor: enforces non-null on the
     * identity / display fields the lesson player renders
     * unconditionally; clamps {@link #completionPercent} into the
     * {@code [0, 100]} window (a regression in the roll-up formula
     * that emitted {@code 101} would break the radial progress
     * widget); and defensively copies the list fields (with
     * {@code null} → {@link List#of()} normalisation) so external
     * mutation cannot leak into the bound payload after
     * construction.
     */
    public StudentLessonPlayerResponse {
        Objects.requireNonNull(lessonId, "lessonId must not be null");
        Objects.requireNonNull(title, "title must not be null");
        completionPercent = Math.max(0, Math.min(100, completionPercent));
        liveClasses = liveClasses == null ? List.of() : List.copyOf(liveClasses);
        recordedVideos = recordedVideos == null ? List.of() : List.copyOf(recordedVideos);
    }
}
