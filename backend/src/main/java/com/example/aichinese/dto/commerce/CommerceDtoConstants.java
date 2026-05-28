package com.example.aichinese.dto.commerce;

/**
 * Centralised size / precision / pattern limits shared by the
 * commerce DTOs in this package.
 *
 * <p>The limits are mirrored from the persistence layer
 * ({@link com.example.aichinese.entity.Cart},
 * {@link com.example.aichinese.entity.Order},
 * {@link com.example.aichinese.entity.PaymentRecord},
 * {@link com.example.aichinese.entity.RefundRequest},
 * {@link com.example.aichinese.entity.PayoutRequest},
 * {@link com.example.aichinese.entity.TeacherEarning}) and the
 * {@code DECIMAL(10,2)} money / {@code VARCHAR(3)} ISO-4217 currency
 * schema columns. Centralising them here keeps the request DTOs that
 * share the same vocabulary in lock-step with the schema, so a
 * schema widening only needs one source-code edit.</p>
 *
 * <h2>Why a class with private constructor and not an {@code enum}
 * or {@code interface}</h2>
 * <p>Bean Validation annotations
 * ({@link jakarta.validation.constraints.Size},
 * {@link jakarta.validation.constraints.Digits},
 * {@link jakarta.validation.constraints.Pattern}) require their
 * member expressions to be compile-time constants of the exact
 * primitive / {@link String} type expected by the annotation.
 * {@code public static final} fields on a final class with a private
 * constructor give us exactly that contract without the boxing tax
 * of a wrapper type and without the "constant interface"
 * anti-pattern.</p>
 *
 * <p>Layout mirrors {@link com.example.aichinese.dto.content.ContentDtoConstants}
 * for cross-package consistency.</p>
 */
public final class CommerceDtoConstants {

    /**
     * Maximum length for the case-insensitive purchase-item-type
     * discriminator (e.g. {@code "LESSON"}, {@code "PACKAGE"},
     * {@code "PREMIUM_AI"}). Provides head-room above the longest
     * {@link com.example.aichinese.entity.enums.PurchasableItemType}
     * constant ({@code PREMIUM_AI}, 10 chars) so future additions
     * fit without a schema migration.
     */
    public static final int ITEM_TYPE_MAX_LENGTH = 30;

    /**
     * Maximum length for the optional feature-key tag attached to a
     * subscription-style purchase (e.g. {@code "AI_CHAT_PRO"}). Sized
     * generously above the longest current
     * {@link com.example.aichinese.entity.enums.AiFeatureKey} constant
     * to allow new feature tiers without a request-DTO change.
     */
    public static final int FEATURE_KEY_MAX_LENGTH = 100;

    /**
     * Maximum length of the student-supplied free-text rationale on
     * a refund request. Mirrors the
     * {@code refund_requests.reason VARCHAR(1000)} schema column.
     */
    public static final int REFUND_REASON_MAX_LENGTH = 1000;

    /**
     * Maximum length of the admin-supplied rejection note used by
     * both refund and payout decision flows. Mirrors the
     * {@code refund_requests.decision_reason} /
     * {@code payout_requests.decision_reason} {@code VARCHAR(1000)}
     * schema columns.
     */
    public static final int DECISION_REASON_MAX_LENGTH = 1000;

    /**
     * Maximum length of the optional teacher-supplied note when
     * filing a payout. Mirrors the
     * {@code payout_requests.requested_note VARCHAR(1000)} schema
     * column.
     */
    public static final int PAYOUT_NOTE_MAX_LENGTH = 1000;

    /**
     * Maximum number of digits accepted to the left of the decimal
     * separator for a money amount. Combined with
     * {@link #MONEY_FRACTION_DIGITS} the pair encodes the
     * {@code DECIMAL(10,2)} schema columns shared by orders,
     * payments, refunds, payouts, and earnings: 10 total digits split
     * into 8 integer digits and 2 fraction digits.
     */
    public static final int MONEY_INTEGER_DIGITS = 8;

    /**
     * Maximum number of digits accepted to the right of the decimal
     * separator for a money amount. See {@link #MONEY_INTEGER_DIGITS}
     * for the paired contract.
     */
    public static final int MONEY_FRACTION_DIGITS = 2;

    /**
     * Lower bound (inclusive) for the platform commission rate.
     * Stored as a {@link String} because Bean Validation's
     * {@link jakarta.validation.constraints.DecimalMin} annotation
     * accepts only string literals.
     */
    public static final String COMMISSION_RATE_MIN = "0.00";

    /**
     * Upper bound (inclusive) for the platform commission rate
     * ({@code 1.00} == 100 % platform take). Stored as a
     * {@link String} for the same reason as
     * {@link #COMMISSION_RATE_MIN}.
     */
    public static final String COMMISSION_RATE_MAX = "1.00";

    /**
     * Maximum number of digits accepted to the left of the decimal
     * separator for a commission rate. Combined with
     * {@link #COMMISSION_RATE_FRACTION_DIGITS} this caps the precision
     * accepted from the wire to avoid pathological inputs (e.g.
     * {@code 0.123456789012345}). One integer digit suffices because
     * the value is constrained to {@code [0.00, 1.00]}.
     */
    public static final int COMMISSION_RATE_INTEGER_DIGITS = 1;

    /**
     * Maximum number of digits accepted to the right of the decimal
     * separator for a commission rate. Four decimals are sufficient
     * to express 0.01 % granularity (e.g. {@code 0.1234} = 12.34 %).
     */
    public static final int COMMISSION_RATE_FRACTION_DIGITS = 4;

    /**
     * Regular expression that matches a valid ISO 4217 currency code.
     * Mirrors the schema-side {@code regexp '^[A-Z]{3}$'} CHECK
     * constraint so DTO validation rejects malformed currencies at
     * the API boundary rather than letting the database raise a
     * sqlstate.
     */
    public static final String CURRENCY_CODE_PATTERN = "^[A-Z]{3}$";

    private CommerceDtoConstants() {
        // Utility class — no instances.
        throw new UnsupportedOperationException("CommerceDtoConstants is a utility class.");
    }
}
