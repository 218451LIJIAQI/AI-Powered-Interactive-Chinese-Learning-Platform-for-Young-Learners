package com.example.aichinese.dto.teacher;

import java.time.LocalDateTime;
import java.util.List;

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
 * @param userId                  underlying {@code users} row id
 * @param fullName                {@code users.display_name} (despite
 *                                the field name); mirrors
 *                                {@link UpdateTeacherProfileRequest#fullName}
 * @param email                   primary contact email; not editable
 *                                via {@link UpdateTeacherProfileRequest}
 * @param teacherProfileId        underlying {@code teacher_profiles}
 *                                row id
 * @param bio                     long-form description; nullable
 * @param shortIntroduction       directory teaser line; nullable
 * @param teachingCategories      free-form tag list; never
 *                                {@code null} (empty when none)
 * @param teacherStatus           verification state machine; see
 *                                {@link TeacherStatus}
 * @param rejectionReason         admin-supplied free text from the
 *                                latest {@link TeacherStatus#REJECTED}
 *                                decision; {@code null} when not rejected
 * @param verificationDocuments   one row per document type, current
 *                                version only (see class Javadoc)
 * @param createdAt               profile-creation timestamp
 * @param updatedAt               last-modified timestamp
 */
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
}
