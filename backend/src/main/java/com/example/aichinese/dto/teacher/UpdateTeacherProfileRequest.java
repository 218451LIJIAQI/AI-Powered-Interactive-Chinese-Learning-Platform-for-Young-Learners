package com.example.aichinese.dto.teacher;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/me/profile} — the
 * self-service "edit my profile" endpoint that any authenticated
 * teacher can use to update the four user-editable fields on their
 * {@code TeacherProfile}.
 *
 * <p>Note that this is intentionally <em>not</em> the full set of
 * teacher profile fields:
 * {@link com.example.aichinese.entity.enums.TeacherStatus},
 * {@code rejectionReason}, verification documents and audit timestamps
 * are managed by the verification pipeline (admin reviews + the
 * dedicated multipart upload endpoint) and are deliberately omitted
 * from this DTO so a teacher cannot self-approve.</p>
 *
 * <h2>Field-level constraints</h2>
 * <ul>
 *   <li>{@link #fullName} — required. The record field is named
 *       "full name" for historical reasons, but the service writes it
 *       to {@code users.display_name} (the {@code users} table has no
 *       {@code full_name} column). Cap of 120 chars matches the
 *       {@code users.display_name} schema column; {@code @NotBlank}
 *       prevents the teacher from blanking out their identity.</li>
 *   <li>{@link #bio} — optional long-form description (2000 chars).
 *       Rendered on the public teacher profile page; cap matches the
 *       {@code teacher_profiles.bio} {@code TEXT} column.</li>
 *   <li>{@link #shortIntroduction} — optional one-paragraph hook
 *       (1000 chars). Used as the teaser line on directory cards
 *       where the full {@link #bio} would be too long.</li>
 *   <li>{@link #teachingCategories} — free-form tag list with each
 *       entry capped at 60 chars. Persisted as a normalised
 *       {@code teaching_categories} join table; an empty/{@code null}
 *       list clears the existing entries.</li>
 * </ul>
 */
public record UpdateTeacherProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be 120 characters or fewer.")
        String fullName,
        @Size(max = 2000, message = "Bio must be 2000 characters or fewer.")
        String bio,
        @Size(max = 1000, message = "Short introduction must be 1000 characters or fewer.")
        String shortIntroduction,
        List<@Size(max = 60, message = "Each teaching category must be 60 characters or fewer.") String> teachingCategories) {
}
