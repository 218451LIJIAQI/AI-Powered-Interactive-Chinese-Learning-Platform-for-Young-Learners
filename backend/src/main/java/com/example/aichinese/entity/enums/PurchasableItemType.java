package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public enum PurchasableItemType {

    PACKAGE,

    COURSE,

    MODULE,

    PREMIUM_AI,

    LESSON;

    public static final Set<PurchasableItemType> CONTENT_ITEMS =
            Collections.unmodifiableSet(EnumSet.of(PACKAGE, COURSE, MODULE, LESSON));

    public static final Set<PurchasableItemType> SUBSCRIPTION_ITEMS =
            Collections.unmodifiableSet(EnumSet.of(PREMIUM_AI));

    static {
        EnumSet<PurchasableItemType> covered = EnumSet.copyOf(CONTENT_ITEMS);
        covered.addAll(SUBSCRIPTION_ITEMS);
        if (!covered.equals(EnumSet.allOf(PurchasableItemType.class))) {
            EnumSet<PurchasableItemType> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "PurchasableItemType category sets do not cover all constants; uncovered: " + missing);
        }
        EnumSet<PurchasableItemType> overlap = EnumSet.copyOf(CONTENT_ITEMS);
        overlap.retainAll(SUBSCRIPTION_ITEMS);
        if (!overlap.isEmpty()) {
            throw new AssertionError(
                    "PurchasableItemType CONTENT_ITEMS and SUBSCRIPTION_ITEMS must be "
                            + "disjoint; overlapping: " + overlap);
        }
    }

    private static final Map<String, PurchasableItemType> LOOKUP =
            EnumLookups.indexByName(PurchasableItemType.class);

    public static Optional<PurchasableItemType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    public static PurchasableItemType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Purchase item type is required.",
                () -> "Invalid purchase item type. Allowed values are PACKAGE, COURSE, "
                        + "MODULE, PREMIUM_AI, or LESSON.");
    }

    public boolean isContent() {
        return CONTENT_ITEMS.contains(this);
    }

    public boolean isSubscription() {
        return SUBSCRIPTION_ITEMS.contains(this);
    }
}
