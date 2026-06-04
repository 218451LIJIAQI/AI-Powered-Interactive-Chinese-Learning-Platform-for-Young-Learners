package com.example.aichinese.dto.content;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import com.example.aichinese.entity.enums.PackageItemType;

@JsonPropertyOrder({
        "id",
        "packageId",
        "itemType",
        "itemId",
        "displayOrder",
        "titleSnapshot"
})
public record PackageItemResponse(
        Long id,
        Long packageId,
        PackageItemType itemType,
        Long itemId,
        Integer displayOrder,
        String titleSnapshot) {

    public PackageItemResponse {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(itemType, "itemType must not be null");
        Objects.requireNonNull(itemId, "itemId must not be null");
        Objects.requireNonNull(displayOrder, "displayOrder must not be null");
    }
}
