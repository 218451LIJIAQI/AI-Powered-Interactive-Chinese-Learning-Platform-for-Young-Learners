package com.example.aichinese.dto.learning;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/live-classes} — the teacher
 * schedules a new live class.
 *
 * <h2>Initial status invariant</h2>
 * <p>The service forces newly-created live classes into
 * {@link com.example.aichinese.entity.enums.LiveClassStatus#SCHEDULED}
 * regardless of what the optional {@link #status} field carries
 * (see {@code LiveClassService.validateInitialLiveClassStatus} which
 * raises a 400 with "Live classes must be created in SCHEDULED
 * status." for any other value). The field is accepted to keep the
 * create-vs-update DTO shapes symmetrical.</p>
 *
 * <h2>{@link #lessonId} / {@link #packageId} ownership</h2>
 * <p>A live class must be linked to a lesson, a course package, or
 * both. The service ({@code LiveClassService}) raises a 400 when
 * neither id is populated. This linkage drives whether the live
 * class appears under
 * {@link StudentLessonPlayerResponse#liveClasses()} (lesson-linked)
 * or under the student's "My subscribed packages" view
 * (package-linked).</p>
 *
 * <h2>Other invariants</h2>
 * <ul>
 *   <li>{@link #startTime} must be in the {@link Future}; the
 *       database stores Asia/Singapore-anchored timestamps.</li>
 *   <li>{@link #endTime} is captured but server-side validation in
 *       {@code LiveLearningAccessService.validateLiveClassTiming}
 *       enforces {@code endTime > startTime}.</li>
 *   <li>{@link #meetingLink} must be {@code http(s)://} with no
 *       embedded whitespace — see the regex. The service redacts the
 *       link from student responses until the lobby window opens
 *       (see {@link LiveClassResponse#meetingLinkVisible()}).</li>
 *   <li>{@link #meetingPlatform} is parsed via
 *       {@code LiveLearningAccessService.parseMeetingPlatform} into
 *       {@code MeetingPlatform} enum; unknown values produce a 400.</li>
 *   <li>{@link #maxParticipants} is the soft cap used by the
 *       enrolment endpoint — clients exceeding it get a 400.</li>
 * </ul>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly strips surrounding Unicode whitespace from
 * every string field so Bean Validation evaluates the canonical
 * form (a paste with stray leading/trailing whitespace cannot
 * inflate the length past the cap or sneak past the http(s) regex)
 * and so every downstream consumer — the persisted entity, the
 * audit log, and the meeting-platform parser — sees the same
 * stripped value rather than relying on each consumer to normalise
 * independently. Optional fields ({@link #description},
 * {@link #status}) collapse to {@code null} when blank so the
 * downstream "is this set?" check is a single null-check rather
 * than {@code null || isEmpty()}.</p>
 *
 * @param title           short human-readable session title shown on
 *                        the calendar (1–200 chars after stripping);
 *                        required
 * @param description     optional long-form description shown on the
 *                        live-class detail page (max 1000 chars after
 *                        stripping); {@code null} or blank collapses to
 *                        {@code null}
 * @param lessonId        optional id of the
 *                        {@link com.example.aichinese.entity.Lesson}
 *                        this session anchors to; mutually exclusive
 *                        with {@link #packageId}
 * @param packageId       optional id of the
 *                        {@link com.example.aichinese.entity.CoursePackage}
 *                        this session anchors to; mutually exclusive
 *                        with {@link #lessonId}
 * @param startTime       wall-clock instant the session begins
 *                        (Asia/Singapore zone); required and must be
 *                        in the future
 * @param endTime         wall-clock instant the session ends
 *                        (Asia/Singapore zone); required and must be
 *                        strictly after {@link #startTime}
 *                        (validated server-side)
 * @param meetingLink     full {@code http(s)://} URL students follow
 *                        to join the session (max 500 chars);
 *                        required
 * @param meetingPlatform free-text platform name parsed into
 *                        {@code MeetingPlatform} enum (max 80 chars);
 *                        required
 * @param status          optional initial status hint (ignored — the
 *                        service forces {@code SCHEDULED}); kept for
 *                        DTO symmetry with
 *                        {@link UpdateLiveClassRequest}; {@code null}
 *                        or blank collapses to {@code null}
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
public record CreateLiveClassRequest(
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
        @Future(message = "Start time must be in the future.")
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
     * checks are a single null-check. {@code null} inputs are
     * preserved so {@link NotBlank @NotBlank} on required fields
     * still fires correctly.
     */
    public CreateLiveClassRequest {
        title = strip(title);
        description = stripToNull(description);
        timeZone = stripToNull(timeZone);
        meetingLink = strip(meetingLink);
        meetingPlatform = strip(meetingPlatform);
        status = stripToNull(status);
    }
}
