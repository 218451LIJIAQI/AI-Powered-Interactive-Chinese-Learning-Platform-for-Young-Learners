package com.example.aichinese.dto.learning;

import java.time.LocalDateTime;

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
 * <p>A live class may be linked to <em>either</em> a lesson, a
 * course package, or neither (a stand-alone live class), but not
 * both. The service ({@code LiveClassService}) raises a 400 when
 * both ids are populated. This linkage drives whether the live
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
 *   <li>{@link #meetingLink} must be {@code http(s)://} — see the
 *       regex. The service redacts the link from student responses
 *       until the lobby window opens (see
 *       {@link LiveClassResponse#meetingLinkVisible()}).</li>
 *   <li>{@link #meetingPlatform} is parsed via
 *       {@code LiveLearningAccessService.parseMeetingPlatform} into
 *       {@code MeetingPlatform} enum; unknown values produce a 400.</li>
 *   <li>{@link #maxParticipants} is the soft cap used by the
 *       enrolment endpoint — clients exceeding it get a 400.</li>
 * </ul>
 */
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
