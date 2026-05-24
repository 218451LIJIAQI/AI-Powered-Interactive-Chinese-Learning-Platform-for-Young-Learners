package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

/**
 * Body for {@code PUT /api/v1/admin/system-settings/commission}
 * — the admin updates the platform-wide commission rate that
 * determines how much of each sale the platform retains versus
 * how much credits the teacher's earnings.
 *
 * <p>Setting takes effect prospectively only — already-credited
 * earnings are NOT recomputed when the rate changes. Each
 * {@link com.example.aichinese.entity.TeacherEarning} row stores the
 * {@code commissionAmount} and {@code netAmount} computed at the
 * moment the order was paid, so historical earnings are stable
 * even across rate changes.</p>
 *
 * <h2>{@link #commissionRate}</h2>
 * <p>Decimal fraction in the closed range
 * {@code [{@value CommerceDtoConstants#COMMISSION_RATE_MIN},
 * {@value CommerceDtoConstants#COMMISSION_RATE_MAX}]} (e.g.
 * {@code 0.20} = 20% platform commission; teacher keeps 80%).</p>
 * <ul>
 *   <li>{@code 0.00} — platform takes nothing; entire sale goes to
 *       the teacher (e.g. promotional period).</li>
 *   <li>{@code 1.00} — platform takes everything; teacher gets
 *       zero (only legitimate for free / promotional content
 *       priced at 0 anyway).</li>
 * </ul>
 *
 * <p>The {@link Digits} cap of
 * {@value CommerceDtoConstants#COMMISSION_RATE_INTEGER_DIGITS}
 * integer / {@value CommerceDtoConstants#COMMISSION_RATE_FRACTION_DIGITS}
 * fraction digits prevents pathological precision inputs (e.g.
 * {@code 0.123456789012345}) at the API boundary, before the
 * service-layer normaliser ever runs.</p>
 *
 * <p>The setting is persisted as a key-value row on
 * {@link com.example.aichinese.entity.SystemSetting} keyed by
 * {@code "commission.rate"} (mirrored on
 * {@link CommissionSettingResponse#settingKey()}).</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param commissionRate decimal fraction in
 *                       {@code [0.00, 1.00]}; required and bounded
 *                       in precision
 */
@JsonPropertyOrder({ "commissionRate" })
public record CommissionSettingRequest(
        @NotNull(message = "Commission rate is required.")
        @DecimalMin(value = CommerceDtoConstants.COMMISSION_RATE_MIN, inclusive = true,
                message = "Commission rate must be at least {value}.")
        @DecimalMax(value = CommerceDtoConstants.COMMISSION_RATE_MAX, inclusive = true,
                message = "Commission rate must not exceed {value}.")
        @Digits(integer = CommerceDtoConstants.COMMISSION_RATE_INTEGER_DIGITS,
                fraction = CommerceDtoConstants.COMMISSION_RATE_FRACTION_DIGITS,
                message = "Commission rate must fit within {integer} integer digit(s) and {fraction} decimal places.")
        BigDecimal commissionRate) {
}
