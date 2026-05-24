package com.example.aichinese.entity.enums;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Kind of item that may be added to a shopping cart and purchased.
 *
 * <p>Resolved at checkout by {@code PurchasableItemService.resolve}, which
 * dispatches per-type pricing, entitlement, and teacher-attribution
 * logic. The {@link #COURSE} value is retained for backward compatibility
 * with legacy carts that referenced courses before the platform unified
 * around {@link #MODULE} and {@link #PACKAGE}.</p>
 *
 * <p>This enum is parsed from cart / checkout DTOs sent by the client.
 * The constant names appear in JSON payloads, so they MUST NOT be
 * renamed without a coordinated frontend change.</p>
 */
public enum PurchasableItemType {

    /** A {@code CoursePackage} bundle. */
    PACKAGE,

    /** Legacy course identifier; kept for backward compatibility. */
    COURSE,

    /** A {@code LearningModule}. */
    MODULE,

    /** Subscription to the premium AI tier (higher quotas, advanced models). */
    PREMIUM_AI,

    /** A single {@code Lesson}. */
    LESSON;

    /** Items that grant access to a piece of learning content. */
    public static final Set<PurchasableItemType> CONTENT_ITEMS =
            Collections.unmodifiableSet(EnumSet.of(PACKAGE, COURSE, MODULE, LESSON));

    /** Items that grant a subscription / quota uplift rather than content. */
    public static final Set<PurchasableItemType> SUBSCRIPTION_ITEMS =
            Collections.unmodifiableSet(EnumSet.of(PREMIUM_AI));

    /*
     * Startup-time exhaustiveness guard: every constant must be either
     * a content item or a subscription item.  Catches forgotten
     * classification at class-load time rather than at runtime.
     */
    static {
        EnumSet<PurchasableItemType> covered = EnumSet.copyOf(CONTENT_ITEMS);
        covered.addAll(SUBSCRIPTION_ITEMS);
        if (!covered.equals(EnumSet.allOf(PurchasableItemType.class))) {
            EnumSet<PurchasableItemType> missing = EnumSet.complementOf(covered);
            throw new AssertionError(
                    "PurchasableItemType category sets do not cover all constants; uncovered: " + missing);
        }
    }

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PurchasableItemType> LOOKUP =
            EnumLookups.indexByName(PurchasableItemType.class);

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised item type
     */
    public static Optional<PurchasableItemType> tryFrom(String value) {
        return EnumLookups.tryFrom(LOOKUP, value);
    }

    /**
     * Strict counterpart of {@link #tryFrom(String)}.
     *
     * <p>Behaviour preserved from the original implementation: {@code null}
     * or blank input throws "{@code Purchase item type is required.}",
     * invalid input throws "{@code Invalid purchase item type. Allowed
     * values are PACKAGE, COURSE, MODULE, PREMIUM_AI, or LESSON.}".</p>
     *
     * @throws IllegalArgumentException when {@code value} is {@code null},
     *         blank, or not a recognised item type
     */
    public static PurchasableItemType fromValue(String value) {
        return EnumLookups.fromValue(
                LOOKUP,
                value,
                "Purchase item type is required.",
                () -> "Invalid purchase item type. Allowed values are PACKAGE, COURSE, "
                        + "MODULE, PREMIUM_AI, or LESSON.");
    }

    /** @return {@code true} when the purchase grants access to learning content. */
    public boolean isContent() {
        return CONTENT_ITEMS.contains(this);
    }

    /** @return {@code true} when the purchase grants a subscription / quota uplift. */
    public boolean isSubscription() {
        return SUBSCRIPTION_ITEMS.contains(this);
    }
}
