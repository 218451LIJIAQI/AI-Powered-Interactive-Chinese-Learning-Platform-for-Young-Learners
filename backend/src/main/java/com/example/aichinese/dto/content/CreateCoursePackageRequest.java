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
import jakarta.validation.constraints.Size;

@JsonPropertyOrder({
        "title",
        "description",
        "levelId",
        "categoryId",
        "learningType",
        "accessType",
        "price",
        "coverImageUrl"
})
public record CreateCoursePackageRequest(
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
        @NotNull(message = "Learning type is required.")
        LearningType learningType,
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

    public CreateCoursePackageRequest {
        title = strip(title);
        description = stripToNull(description);
        coverImageUrl = stripToNull(coverImageUrl);
    }
}
