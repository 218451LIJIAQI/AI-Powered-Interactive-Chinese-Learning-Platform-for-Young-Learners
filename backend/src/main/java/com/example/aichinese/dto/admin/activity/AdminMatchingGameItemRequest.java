package com.example.aichinese.dto.admin.activity;

import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record AdminMatchingGameItemRequest(
        @NotBlank(message = "Left text is required.")
        @Size(max = 500, message = "Left text must not exceed 500 characters.")
        String leftText,

        @NotBlank(message = "Right text is required.")
        @Size(max = 500, message = "Right text must not exceed 500 characters.")
        String rightText,

        @Pattern(
                regexp = AdminMatchingGameItemRequest.HTTP_URL_PATTERN,
                message = "Left image URL must start with http:// or https://.")
        @Size(max = 255, message = "Left image URL must not exceed 255 characters.")
        String leftImageUrl,

        @Pattern(
                regexp = AdminMatchingGameItemRequest.HTTP_URL_PATTERN,
                message = "Right image URL must start with http:// or https://.")
        @Size(max = 255, message = "Right image URL must not exceed 255 characters.")
        String rightImageUrl,

        @Size(max = 255, message = "Pinyin must not exceed 255 characters.")
        String pinyin,

        @PositiveOrZero(message = "Sort order must be non-negative.")
        @Max(value = 10_000, message = "Sort order must not exceed 10000.")
        Integer sortOrder) {

    public AdminMatchingGameItemRequest {
        leftText = strip(leftText);
        rightText = strip(rightText);
        leftImageUrl = stripToNull(leftImageUrl);
        rightImageUrl = stripToNull(rightImageUrl);
        pinyin = stripToNull(pinyin);
    }

    static final String HTTP_URL_PATTERN = "^(https?://.+)?$";
}
