package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.example.aichinese.entity.enums.ManagedContentType;

@JsonPropertyOrder({ "type", "contentId", "title", "displayOrder" })
public record PublicCourseItemSummaryResponse(
        ManagedContentType type,
        Long contentId,
        String title,
        Integer displayOrder) {

    public PublicCourseItemSummaryResponse {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(contentId, "contentId must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
