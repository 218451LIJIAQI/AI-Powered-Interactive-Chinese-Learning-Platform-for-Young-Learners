package com.example.aichinese.service.model;

import java.math.BigDecimal;

import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.enums.PurchasableItemType;

/**
 * Snapshot returned by
 * {@link com.example.aichinese.service.PurchasableItemService} after
 * validating a cart-line / direct-purchase reference into something
 * the commerce path can safely persist.
 *
 * <p>Carrying a record instead of the live entity decouples the
 * commerce pipeline (cart, order, payment, entitlement) from the
 * authoring entities — the unit price captured at resolution time
 * locks the amount the learner sees before payment so a price update
 * mid-checkout cannot retroactively re-bill them. {@code teacher}
 * is the resolved {@link TeacherProfile} owner (used by
 * {@link com.example.aichinese.service.TeacherEarningService} to write
 * earnings) and {@code freeItem=true} short-circuits payment for
 * promotional / free titles.</p>
 */
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
