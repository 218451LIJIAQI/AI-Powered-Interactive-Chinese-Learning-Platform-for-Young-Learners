package com.example.aichinese.dto.content;

import com.example.aichinese.entity.enums.PackageItemType;

public record PackageItemResponse(
        Long id,
        Long packageId,
        PackageItemType itemType,
        Long itemId,
        Integer displayOrder,
        String titleSnapshot) {
}
