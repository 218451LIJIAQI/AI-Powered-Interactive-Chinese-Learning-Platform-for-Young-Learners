package com.example.aichinese.dto.teacher;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

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
 *       entry capped at 60 chars and the list itself capped at 12
 *       entries (matches the silent server-side cap inside
 *       {@code TeacherManagementMapper.joinCategories} — surfacing it
 *       as a Bean Validation error replaces silent truncation with a
 *       clear 400-level rejection). Persisted as a normalised
 *       {@code teaching_categories} join table; an empty/{@code null}
 *       list clears the existing entries.</li>
 * </ul>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * <h2>Normalisation</h2>
 * <p>The canonical constructor eagerly {@link String#trim() trims}
 * {@link #fullName}, {@link #bio} and {@link #shortIntroduction} so
 * Bean Validation operates on the canonical form (a whitespace-only
 * {@code fullName} fails {@code @NotBlank} after trimming, the way
 * the user expects) and the service layer's
 * {@code .trim()} / {@code TextUtils.trimToNull} calls are
 * idempotent. {@link #teachingCategories} is handed off to the
 * mapper unchanged because {@code joinCategories} owns the
 * trim/dedup/cap pipeline for that field. {@code null} is preserved
 * for any field so the {@link NotBlank @NotBlank} validators render
 * their friendly user-facing messages rather than a low-level
 * {@link NullPointerException}.</p>
 */
@JsonPropertyOrder({ "fullName", "bio", "shortIntroduction", "teachingCategories" })
public record UpdateTeacherProfileRequest(
        @NotBlank(message = "Full name is required.")
        @Size(max = 120, message = "Full name must be 120 characters or fewer.")
        String fullName,

        @Size(max = 2000, message = "Bio must be 2000 characters or fewer.")
        String bio,

        @Size(max = 1000, message = "Short introduction must be 1000 characters or fewer.")
        String shortIntroduction,

        @Size(max = 12, message = "Teaching categories must not exceed 12 entries.")
        List<@Size(max = 60, message = "Each teaching category must be 60 characters or fewer.") String> teachingCategories) {

    /**
     * Defensive canonical constructor: trims the three free-text
     * fields so Bean Validation operates on the canonical form and the
     * service layer's normalisation calls are idempotent. {@code null}
     * is preserved so {@link NotBlank @NotBlank} can render the
     * friendly "is required" message rather than a low-level
     * {@link NullPointerException}.
     * {@link #teachingCategories} is intentionally <em>not</em>
     * defensively copied or normalised here — the
     * {@code TeacherManagementMapper.joinCategories} pipeline is the
     * canonical owner of trim, dedup and storage cap for this field.
     */
    public UpdateTeacherProfileRequest {
        if (fullName != null) {
            fullName = fullName.trim();
        }
        if (bio != null) {
            bio = bio.trim();
        }
        if (shortIntroduction != null) {
            shortIntroduction = shortIntroduction.trim();
        }
    }
}
