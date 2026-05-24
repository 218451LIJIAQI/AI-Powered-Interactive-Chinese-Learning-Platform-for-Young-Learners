package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
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
 * <p>Decimal fraction in the closed range {@code [0.0, 1.0]} (e.g.
 * {@code 0.20} = 20% platform commission; teacher keeps 80%).</p>
 * <ul>
 *   <li>{@code 0.00} — platform takes nothing; entire sale goes to
 *       the teacher (e.g. promotional period).</li>
 *   <li>{@code 1.00} — platform takes everything; teacher gets
 *       zero (only legitimate for free / promotional content
 *       priced at 0 anyway).</li>
 * </ul>
 *
 * <p>The setting is persisted as a key-value row on
 * {@link com.example.aichinese.entity.SystemSetting} keyed by
 * {@code "commission.rate"} (mirrored on
 * {@link CommissionSettingResponse#settingKey()}).</p>
 */
public record CommissionSettingRequest(
        @NotNull(message = "Commission rate is required.")
        @DecimalMin(value = "0.0", inclusive = true, message = "Commission rate must not be negative.")
        @DecimalMax(value = "1.0", inclusive = true, message = "Commission rate must not exceed 1.0.")
        BigDecimal commissionRate) {
}
