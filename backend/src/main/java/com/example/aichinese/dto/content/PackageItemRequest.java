package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.PackageItemType;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record PackageItemRequest(
        @NotNull(message = "Item type is required.")
        PackageItemType itemType,
        @NotNull(message = "Item id is required.")
        Long itemId,
        @PositiveOrZero(message = "Display order must be greater than or equal to 0.")
        Integer displayOrder) {
}
