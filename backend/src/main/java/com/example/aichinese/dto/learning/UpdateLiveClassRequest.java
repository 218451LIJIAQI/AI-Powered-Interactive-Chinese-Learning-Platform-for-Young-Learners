package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/live-classes/{id}} — the
 * teacher edits an existing live class.
 *
 * <p>Mirrors {@link CreateLiveClassRequest} field-for-field with two
 * subtle differences:</p>
 * <ul>
 *   <li>{@link #startTime} is <em>not</em> annotated {@code @Future}.
 *       Update can re-confirm a class that has already started (e.g.
 *       changing only the meeting link mid-session) without
 *       artificially failing validation.</li>
 *   <li>{@link #status} accepts any
 *       {@link com.example.aichinese.entity.enums.LiveClassStatus}
 *       transition that
 *       {@code LiveLearningAccessService.validateLiveClassStatusTransition}
 *       allows (e.g. {@code SCHEDULED → ONGOING},
 *       {@code SCHEDULED → CANCELLED}); illegal transitions raise
 *       400. Terminal states ({@code COMPLETED}, {@code CANCELLED})
 *       reject any further edits via
 *       {@code LiveClassService.ensureLiveClassEditable}.</li>
 * </ul>
 *
 * <p>For the dedicated reschedule and status-only flows see
 * {@link RescheduleLiveClassRequest} (auto-flips status to
 * {@code RESCHEDULED}) and {@link UpdateLiveClassStatusRequest}
 * (status-only PATCH).</p>
 */
public record UpdateLiveClassRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = 200, message = "Title must be 200 characters or fewer.")
        String title,

        @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
        String description,

        @Positive(message = "Lesson id must be positive.")
        Long lessonId,
        @Positive(message = "Package id must be positive.")
        Long packageId,

        @NotNull(message = "Start time is required.")
        LocalDateTime startTime,

        @NotNull(message = "End time is required.")
        LocalDateTime endTime,

        @NotBlank(message = "Meeting link is required.")
        @Pattern(regexp = "^https?://.+$", message = "Meeting link must start with http:// or https://.")
        @Size(max = 500, message = "Meeting link must be 500 characters or fewer.")
        String meetingLink,

        @NotBlank(message = "Meeting platform is required.")
        @Size(max = 80, message = "Meeting platform must be 80 characters or fewer.")
        String meetingPlatform,

        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status,

        @Min(value = 1, message = "Maximum participants must be positive.")
        Integer maxParticipants) {
}
