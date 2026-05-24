package com.example.aichinese.dto.content;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code POST /api/v1/teachers/modules} — an approved
 * teacher creates a new {@link com.example.aichinese.entity.LearningModule}
 * (a grouping of lessons, e.g. "Greetings &amp; Introductions").
 *
 * <p>The module is published immediately on save (see
 * {@link com.example.aichinese.service.TeacherModuleService#createModule}):
 * teacher-authored modules bypass the legacy admin-review flow and
 * go straight to {@link com.example.aichinese.entity.enums.ContentStatus#PUBLISHED}.
 * Only
 * {@link com.example.aichinese.entity.enums.TeacherStatus#APPROVED}
 * teachers can call this endpoint (gated by
 * {@code TeacherContentAccessService.requireApprovedTeacher}); the
 * post-publication moderation surface remains available to admins
 * via {@link com.example.aichinese.service.ContentModerationService}
 * for warn / hide / archive actions if a published module needs
 * intervention.</p>
 *
 * <h2>Shared content metadata vocabulary</h2>
 * <p>Every owned-content create/update request in this package
 * ({@link CreateModuleRequest}, {@link CreateLessonRequest},
 * {@link CreateCoursePackageRequest} and their update counterparts)
 * shares the same metadata fields so the front-end can bind one
 * authoring form to all three artefact types:</p>
 * <ul>
 *   <li>{@link #levelId} — required FK into
 *       {@link LearningLevelResponse} (HSK/level taxonomy).</li>
 *   <li>{@link #categoryId} — required FK into
 *       {@link LearningCategoryResponse} (topic taxonomy).</li>
 *   <li>{@link #accessType} —
 *       {@link com.example.aichinese.entity.enums.AccessType}
 *       ({@code FREE} / {@code PAID}).</li>
 *   <li>{@link #price} — uses
 *       {@code @Digits(integer = 8, fraction = 2)} to match the
 *       {@code DECIMAL(10,2)} schema column. Required to be
 *       non-negative; {@code 0} (or {@code null}) for free content.</li>
 *   <li>{@link #coverImageUrl} — optional reference (URL or relative
 *       path) into the asset store; capped at
 *       {@value ContentDtoConstants#COVER_IMAGE_URL_MAX_LENGTH}
 *       chars.</li>
 * </ul>
 *
 * <p>Note: modules do <em>not</em> carry a
 * {@link com.example.aichinese.entity.enums.LearningType}; that
 * field is on lessons only. A module is a content-type-agnostic
 * container — its constituent lessons each pick their own learning
 * type.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param title         editorial title shown on cards; required, max
 *                      {@value ContentDtoConstants#TITLE_MAX_LENGTH}
 *                      characters
 * @param description   long-form description (max
 *                      {@value ContentDtoConstants#LONG_DESCRIPTION_MAX_LENGTH}
 *                      chars); nullable
 * @param levelId       required FK into
 *                      {@link LearningLevelResponse}; must be
 *                      positive
 * @param categoryId    required FK into
 *                      {@link LearningCategoryResponse}; must be
 *                      positive
 * @param accessType    commercial access tier ({@code FREE} /
 *                      {@code PAID}); required
 * @param price         listed price; matches {@code DECIMAL(10,2)};
 *                      must be {@code >= 0}; may be {@code null} for
 *                      free modules
 * @param coverImageUrl optional cover image reference; max
 *                      {@value ContentDtoConstants#COVER_IMAGE_URL_MAX_LENGTH}
 *                      chars
 */
@JsonPropertyOrder({
        "title",
        "description",
        "levelId",
        "categoryId",
        "accessType",
        "price",
        "coverImageUrl"
})
public record CreateModuleRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = ContentDtoConstants.TITLE_MAX_LENGTH, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = ContentDtoConstants.LONG_DESCRIPTION_MAX_LENGTH,
                message = "Description must not exceed 2000 characters.")
        String description,
        @NotNull(message = "Level is required.")
        @Positive(message = "Level id must be positive.")
        Long levelId,
        @NotNull(message = "Category is required.")
        @Positive(message = "Category id must be positive.")
        Long categoryId,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        @Digits(integer = ContentDtoConstants.PRICE_INTEGER_DIGITS,
                fraction = ContentDtoConstants.PRICE_FRACTION_DIGITS,
                message = "Price must fit within 8 integer digits and 2 decimal places.")
        BigDecimal price,
        @Size(max = ContentDtoConstants.COVER_IMAGE_URL_MAX_LENGTH,
                message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {
}
