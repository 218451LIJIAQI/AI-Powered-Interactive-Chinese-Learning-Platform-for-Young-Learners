package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.TeacherStatus;

/**
 * Full teacher profile payload returned by
 * {@code GET /api/v1/teachers/me} and the response side of
 * {@code PUT /api/v1/teachers/me/profile}.
 *
 * <p>Aggregates three concerns into a single screen-shape:</p>
 * <ul>
 *   <li><strong>Identity</strong> — {@link #userId},
 *       {@link #teacherProfileId}, {@link #fullName}, {@link #email}.
 *       The two id columns are surfaced so the front-end can
 *       cross-reference both the {@code users} row and the
 *       {@code teacher_profiles} row when navigating to admin tools
 *       or profile-scoped endpoints.</li>
 *   <li><strong>Self-edited content</strong> — {@link #bio},
 *       {@link #shortIntroduction}, {@link #teachingCategories} —
 *       these are the same fields the teacher can update via
 *       {@link UpdateTeacherProfileRequest}.</li>
 *   <li><strong>Verification state</strong> — {@link #teacherStatus},
 *       {@link #rejectionReason}, {@link #verificationDocuments} —
 *       managed by the admin review pipeline and read-only from the
 *       teacher's perspective.</li>
 * </ul>
 *
 * <p>For a leaner payload focused only on the verification state
 * (used by the dashboard "verification banner" widget), see
 * {@link TeacherVerificationStatusResponse} — that endpoint
 * additionally returns the latest review record and the
 * {@code resubmissionAllowed} flag.</p>
 *
 * <h2>{@link #verificationDocuments}</h2>
 * <p>Currently surfaces only the latest version of each document type
 * — the service queries
 * {@code findByTeacherIdAndCurrentVersionTrueOrderBySubmittedAtDesc}
 * so superseded uploads stay server-side for the audit trail and do
 * not appear here. Each entry therefore has
 * {@link TeacherVerificationDocumentSummaryResponse#currentVersion()}
 * {@code == true}.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions (snapshot
 * tests, log fingerprints, etc.).</p>
 *
 * <h2>Invariants</h2>
 * <p>The canonical constructor enforces non-null on the seven header
 * fields the profile screen renders unconditionally
 * ({@link #userId}, {@link #fullName}, {@link #email},
 * {@link #teacherProfileId}, {@link #teacherStatus},
 * {@link #createdAt}, {@link #updatedAt}) and defensively copies the
 * two embedded lists. {@link #bio}, {@link #shortIntroduction} and
 * {@link #rejectionReason} remain nullable per the contextual rules
 * documented above.</p>
 *
 * @param userId                  underlying {@code users} row id;
 *                                never {@code null}
 * @param fullName                {@code users.display_name} (despite
 *                                the field name); mirrors
 *                                {@link UpdateTeacherProfileRequest#fullName};
 *                                never {@code null}
 * @param email                   primary contact email; not editable
 *                                via {@link UpdateTeacherProfileRequest};
 *                                never {@code null}
 * @param teacherProfileId        underlying {@code teacher_profiles}
 *                                row id; never {@code null}
 * @param bio                     long-form description; nullable
 * @param shortIntroduction       directory teaser line; nullable
 * @param teachingCategories      free-form tag list; never
 *                                {@code null} (empty when none); the
 *                                wrapped list is unmodifiable
 * @param teacherStatus           verification state machine; see
 *                                {@link TeacherStatus}; never
 *                                {@code null}
 * @param rejectionReason         admin-supplied free text from the
 *                                latest {@link TeacherStatus#REJECTED}
 *                                decision; {@code null} when not
 *                                rejected
 * @param verificationDocuments   one row per document type, current
 *                                version only (see class Javadoc);
 *                                never {@code null}; the wrapped list
 *                                is unmodifiable
 * @param createdAt               profile-creation timestamp; never
 *                                {@code null}
 * @param updatedAt               last-modified timestamp; never
 *                                {@code null}
 */
@JsonPropertyOrder({
        "userId",
        "fullName",
        "email",
        "teacherProfileId",
        "bio",
        "shortIntroduction",
        "teachingCategories",
        "teacherStatus",
        "rejectionReason",
        "verificationDocuments",
        "createdAt",
        "updatedAt"
})
public record TeacherProfileResponse(
        Long userId,
        String fullName,
        String email,
        Long teacherProfileId,
        String bio,
        String shortIntroduction,
        List<String> teachingCategories,
        TeacherStatus teacherStatus,
        String rejectionReason,
        List<TeacherVerificationDocumentSummaryResponse> verificationDocuments,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    /**
     * Defensive canonical constructor: enforces non-null on the seven
     * header fields the profile screen renders unconditionally,
     * defensively copies the two embedded lists (with {@code null} →
     * {@link List#of()} normalisation) so external mutation cannot
     * leak into an already-serialised payload, and leaves the
     * contextual nullable fields ({@code bio},
     * {@code shortIntroduction}, {@code rejectionReason}) untouched.
     */
    public TeacherProfileResponse {
        Objects.requireNonNull(userId, "userId must not be null");
        Objects.requireNonNull(fullName, "fullName must not be null");
        Objects.requireNonNull(email, "email must not be null");
        Objects.requireNonNull(teacherProfileId, "teacherProfileId must not be null");
        Objects.requireNonNull(teacherStatus, "teacherStatus must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        Objects.requireNonNull(updatedAt, "updatedAt must not be null");
        teachingCategories = teachingCategories == null
                ? List.of()
                : List.copyOf(teachingCategories);
        verificationDocuments = verificationDocuments == null
                ? List.of()
                : List.copyOf(verificationDocuments);
    }
}
