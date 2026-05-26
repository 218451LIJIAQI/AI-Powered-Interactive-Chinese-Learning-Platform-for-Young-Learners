package com.example.aichinese.dto.content;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.AccessType;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * Body for {@code PUT /api/v1/teachers/lessons/{id}} — the teacher
 * edits an existing lesson.
 *
 * <p>Mirrors {@link CreateLessonRequest} field-for-field. The
 * service rejects edits when the lesson is in a non-editable state
 * (see
 * {@link com.example.aichinese.service.TeacherContentAccessService})
 * and requires the edit to come from the lesson's owning teacher.</p>
 *
 * <p>{@link #moduleId} can be changed to move the lesson to a
 * different module (or set to {@code null} to detach the lesson).
 * Quizzes and recorded videos travel with the lesson.</p>
 *
 * <p>For archive use the dedicated
 * {@code PATCH /api/v1/teachers/lessons/{id}/archive} endpoint
 * (no body).</p>
 *
 * <p>See {@link CreateLessonRequest} and {@link CreateModuleRequest}
 * for the full discussion of fields and shared validation rules.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param title                    editorial title; required, max
 *                                 {@value ContentDtoConstants#TITLE_MAX_LENGTH}
 *                                 characters
 * @param description              short editorial description
 *                                 (lesson {@code summary} column,
 *                                 max
 *                                 {@value ContentDtoConstants#SHORT_DESCRIPTION_MAX_LENGTH}
 *                                 chars); nullable
 * @param moduleId                 optional parent module FK; when
 *                                 set, the service validates the
 *                                 module belongs to the same
 *                                 teacher; pass {@code null} to
 *                                 detach the lesson
 * @param levelId                  required FK into
 *                                 {@link LearningLevelResponse};
 *                                 must be positive
 * @param categoryId               required FK into
 *                                 {@link LearningCategoryResponse};
 *                                 must be positive
 * @param learningType             delivery model; required
 * @param accessType               commercial access tier
 *                                 ({@code FREE} / {@code PAID});
 *                                 required
 * @param price                    listed price; matches
 *                                 {@code DECIMAL(10,2)}; must be
 *                                 {@code >= 0}; may be {@code null}
 *                                 for free lessons
 * @param estimatedDurationMinutes UX duration hint (minutes);
 *                                 nullable; non-negative when set
 * @param basicContent             authored rich-text body; max
 *                                 {@value ContentDtoConstants#LESSON_CONTENT_MAX_LENGTH}
 *                                 chars; nullable
 * @param coverImageUrl            optional cover image reference;
 *                                 max
 *                                 {@value ContentDtoConstants#COVER_IMAGE_URL_MAX_LENGTH}
 *                                 chars
 */
@JsonPropertyOrder({
        "title",
        "description",
        "moduleId",
        "levelId",
        "categoryId",
        "learningType",
        "accessType",
        "price",
        "estimatedDurationMinutes",
        "basicContent",
        "coverImageUrl"
})
public record UpdateLessonRequest(
        @NotBlank(message = "Title is required.")
        @Size(max = ContentDtoConstants.TITLE_MAX_LENGTH, message = "Title must not exceed 200 characters.")
        String title,
        @Size(max = ContentDtoConstants.SHORT_DESCRIPTION_MAX_LENGTH,
                message = "Description must not exceed 1000 characters.")
        String description,
        @Positive(message = "Module id must be positive.")
        Long moduleId,
        @NotNull(message = "Level is required.")
        @Positive(message = "Level id must be positive.")
        Long levelId,
        @NotNull(message = "Category is required.")
        @Positive(message = "Category id must be positive.")
        Long categoryId,
        @NotNull(message = "Learning type is required.")
        LearningType learningType,
        @NotNull(message = "Access type is required.")
        AccessType accessType,
        @DecimalMin(value = "0.0", inclusive = true, message = "Price must be greater than or equal to 0.")
        @Digits(integer = ContentDtoConstants.PRICE_INTEGER_DIGITS,
                fraction = ContentDtoConstants.PRICE_FRACTION_DIGITS,
                message = "Price must fit within 8 integer digits and 2 decimal places.")
        BigDecimal price,
        @PositiveOrZero(message = "Estimated duration must be greater than or equal to 0.")
        Integer estimatedDurationMinutes,
        @Size(max = ContentDtoConstants.LESSON_CONTENT_MAX_LENGTH,
                message = "Lesson content must not exceed 10000 characters.")
        String basicContent,
        @Size(max = ContentDtoConstants.COVER_IMAGE_URL_MAX_LENGTH,
                message = "Cover image reference must not exceed 255 characters.")
        String coverImageUrl) {

    public UpdateLessonRequest {
        title = strip(title);
        description = stripToNull(description);
        basicContent = stripToNull(basicContent);
        coverImageUrl = stripToNull(coverImageUrl);
    }
}
