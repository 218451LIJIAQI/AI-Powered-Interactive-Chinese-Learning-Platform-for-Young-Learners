package com.example.aichinese.dto.learning;

import java.util.List;

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
 *       {@code StudentLearningProgressService.lessonCompletionPercent}.</li>
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
 *       and enrolment booleans (see {@link LiveClassResponse}).</li>
 *   <li>{@link #recordedVideos} — published, non-archived recorded
 *       videos for this lesson, in {@code sortOrder} ascending.</li>
 * </ul>
 *
 * <h2>{@link #learningType}</h2>
 * <p>Enum-string from
 * {@link com.example.aichinese.entity.enums.LearningType}
 * ("VIDEO", "INTERACTIVE", "TEXT", etc.) so the player UI can pick
 * the right rendering pipeline.</p>
 */
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
}
