package com.example.aichinese.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

import org.springframework.stereotype.Service;

/**
 * Centralised currency arithmetic for every commerce path
 * (cart, order, payment, payout, refund).
 *
 * <p>Two scales are used:</p>
 * <ul>
 *   <li>{@link #MONEY_SCALE} (2 decimal places) — for amounts. This
 *       matches the underlying {@code DECIMAL(N,2)} DB columns; any
 *       precision beyond two decimals is silently lost on persist, so
 *       normalising here ensures the in-memory value always matches
 *       what eventually lands in the database.</li>
 *   <li>{@link #RATE_SCALE} (4 decimal places) — for percentages /
 *       commission rates ({@code 0.2000}) where finer precision is
 *       useful before multiplication.</li>
 * </ul>
 *
 * <p>All operations use {@link RoundingMode#HALF_UP} so the rounding
 * behaviour is identical across orders, payments, refunds, and
 * payouts — preventing one-cent reconciliation drift between
 * commerce surfaces.</p>
 */
@Service
public class CommerceMoneyService {

    public static final int MONEY_SCALE = 2;
    public static final int RATE_SCALE = 4;
    public static final RoundingMode MONEY_ROUNDING = RoundingMode.HALF_UP;

    public BigDecimal normalize(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(MONEY_SCALE, MONEY_ROUNDING);
        }

        return value.setScale(MONEY_SCALE, MONEY_ROUNDING);
    }

    public BigDecimal multiply(BigDecimal left, BigDecimal right) {
        return normalize(normalize(left).multiply(normalize(right)));
    }

    public BigDecimal subtract(BigDecimal left, BigDecimal right) {
        return normalize(normalize(left).subtract(normalize(right)));
    }

    public BigDecimal percentage(BigDecimal amount, BigDecimal rateDecimal) {
        return normalize(normalize(amount).multiply(normalizeRate(rateDecimal)));
    }

    public BigDecimal normalizeRate(BigDecimal value) {
        if (value == null) {
            return BigDecimal.ZERO.setScale(RATE_SCALE, MONEY_ROUNDING);
        }

        return value.setScale(RATE_SCALE, MONEY_ROUNDING);
    }
}
