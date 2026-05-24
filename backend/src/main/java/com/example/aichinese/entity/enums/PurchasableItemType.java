package com.example.aichinese.entity.enums;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

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
            Set.of(PACKAGE, COURSE, MODULE, LESSON);

    /** Items that grant a subscription / quota uplift rather than content. */
    public static final Set<PurchasableItemType> SUBSCRIPTION_ITEMS = Set.of(PREMIUM_AI);

    /** O(1) case-insensitive lookup table built once at class-load time. */
    private static final Map<String, PurchasableItemType> LOOKUP = Arrays.stream(values())
            .collect(Collectors.toUnmodifiableMap(
                    value -> value.name().toUpperCase(Locale.ROOT),
                    Function.identity()));

    /**
     * Tolerant case- and whitespace-insensitive lookup.
     *
     * @param value raw textual representation; may be {@code null} or blank
     * @return matching constant, or {@link Optional#empty()} when the input
     *         is {@code null}, blank, or not a recognised item type
     */
    public static Optional<PurchasableItemType> tryFrom(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return Optional.empty();
        }
        return Optional.ofNullable(LOOKUP.get(trimmed.toUpperCase(Locale.ROOT)));
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
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Purchase item type is required.");
        }
        return tryFrom(value).orElseThrow(() -> new IllegalArgumentException(
                "Invalid purchase item type. Allowed values are PACKAGE, COURSE, "
                        + "MODULE, PREMIUM_AI, or LESSON."));
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
