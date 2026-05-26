package com.example.aichinese.dto.content;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

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
 * Body for {@code PUT /api/v1/teachers/modules/{id}} — the
 * teacher edits an existing module.
 *
 * <p>Mirrors {@link CreateModuleRequest} field-for-field. The
 * service rejects the edit when the module is in a non-editable
 * state (e.g.
 * {@link com.example.aichinese.entity.enums.ContentStatus#ARCHIVED})
 * — see
 * {@link com.example.aichinese.service.TeacherContentAccessService}
 * for the full editable-state predicate.</p>
 *
 * <p>For archiving (soft delete), use
 * {@code PATCH /api/v1/teachers/modules/{id}/archive} (no body) —
 * not this endpoint.</p>
 *
 * <p>See {@link CreateModuleRequest} for the full discussion of
 * shared content metadata vocabulary and validation rules.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param title         editorial title shown on cards; required,
 *                      max
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
 *                      must be {@code >= 0}; may be {@code null}
 *                      for free modules
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
public record UpdateModuleRequest(
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

    public UpdateModuleRequest {
        title = strip(title);
        description = stripToNull(description);
        coverImageUrl = stripToNull(coverImageUrl);
    }
}
