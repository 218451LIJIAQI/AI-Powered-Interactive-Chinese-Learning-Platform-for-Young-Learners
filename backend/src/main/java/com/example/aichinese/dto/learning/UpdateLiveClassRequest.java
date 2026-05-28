package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode whitespace from
 * every string field so Bean Validation sees the canonical form
 * (a paste with stray leading/trailing whitespace cannot inflate
 * the length past the cap or sneak past the http(s) regex) and so
 * every downstream consumer sees the same stripped value. Optional
 * fields ({@link #description}, {@link #status}) collapse to
 * {@code null} when blank so the downstream "is this set?" check
 * is a single null-check.</p>
 *
 * @param title           short human-readable session title (1–200
 *                        chars after stripping); required
 * @param description     optional long-form description (max 1000
 *                        chars after stripping); blank collapses to
 *                        {@code null}
 * @param lessonId        optional lesson id; mutually exclusive
 *                        with {@link #packageId}
 * @param packageId       optional package id; mutually exclusive
 *                        with {@link #lessonId}
 * @param startTime       new wall-clock instant the session begins
 *                        (Asia/Singapore zone); required
 * @param endTime         new wall-clock instant the session ends;
 *                        required and validated server-side to be
 *                        strictly after {@link #startTime}
 * @param meetingLink     full {@code http(s)://} URL (max 500 chars,
 *                        no embedded whitespace); required
 * @param meetingPlatform free-text platform name (max 80 chars);
 *                        required
 * @param status          optional new
 *                        {@link com.example.aichinese.entity.enums.LiveClassStatus}
 *                        enum-name; blank collapses to {@code null}
 *                        (preserves current status)
 * @param maxParticipants optional soft cap on enrolments; must be
 *                        positive when present
 */
@JsonPropertyOrder({
        "title",
        "description",
        "lessonId",
        "packageId",
        "startTime",
        "endTime",
        "timeZone",
        "meetingLink",
        "meetingPlatform",
        "status",
        "maxParticipants"
})
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

        @Size(max = 64, message = "Time zone must be 64 characters or fewer.")
        String timeZone,

        @NotBlank(message = "Meeting link is required.")
        @Pattern(regexp = "^https?://\\S+$", message = "Meeting link must start with http:// or https:// and contain no whitespace.")
        @Size(max = 500, message = "Meeting link must be 500 characters or fewer.")
        String meetingLink,

        @NotBlank(message = "Meeting platform is required.")
        @Size(max = 80, message = "Meeting platform must be 80 characters or fewer.")
        String meetingPlatform,

        @Size(max = 30, message = "Status must be 30 characters or fewer.")
        String status,

        @Min(value = 1, message = "Maximum participants must be positive.")
        Integer maxParticipants) {

    /**
     * Defensive canonical constructor: strips every string field so
     * Bean Validation sees the canonical form (preventing length-cap
     * bypass via leading/trailing whitespace) and collapses optional
     * blank-only strings to {@code null} so downstream "is this set?"
     * checks are a single null-check.
     */
    public UpdateLiveClassRequest {
        title = strip(title);
        description = stripToNull(description);
        timeZone = stripToNull(timeZone);
        meetingLink = strip(meetingLink);
        meetingPlatform = strip(meetingPlatform);
        status = stripToNull(status);
    }
}
