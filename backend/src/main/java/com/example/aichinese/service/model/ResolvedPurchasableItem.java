package com.example.aichinese.service.model;

import java.math.BigDecimal;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.PurchasableItemType;

public record ResolvedPurchasableItem(
        PurchasableItemType itemType,
        Long itemId,
        String title,
        BigDecimal unitPrice,
        String currencyCode,
        TeacherProfile teacher,
        boolean freeItem,
        String relatedFeatureKey) {
}
