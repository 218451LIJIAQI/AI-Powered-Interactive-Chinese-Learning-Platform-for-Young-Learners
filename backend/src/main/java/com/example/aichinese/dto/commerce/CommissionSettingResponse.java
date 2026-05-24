package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Current commission setting returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/system-settings/commission}</li>
 *   <li>{@code PUT /api/v1/admin/system-settings/commission}
 *       (response side, echoes the freshly-saved row)</li>
 * </ul>
 *
 * <p>{@link #commissionRate} is the same
 * {@code [{@value CommerceDtoConstants#COMMISSION_RATE_MIN},
 * {@value CommerceDtoConstants#COMMISSION_RATE_MAX}]} fraction
 * documented on {@link CommissionSettingRequest}.
 * {@link #settingKey} is the persisted
 * {@link com.example.aichinese.entity.SystemSetting#getSettingKey()}
 * (currently {@code "commission.rate"}) — surfaced so the admin UI
 * can deep-link to the system-settings detail screen for that
 * specific key.</p>
 *
 * <p>{@link #description} is the optional admin-facing blurb
 * explaining what the setting controls (defaults to a sane
 * platform-managed string when first seeded). The setting is
 * platform-wide; there is no per-teacher commission override
 * today.</p>
 *
 * <h2>Wire format</h2>
 * <p>The component order is locked via {@link JsonPropertyOrder} so
 * upstream Jackson upgrades cannot accidentally reshuffle the wire
 * format and break consumers that pin on field positions.</p>
 *
 * @param commissionRate currently-active platform commission
 *                       fraction in
 *                       {@code [0.00, 1.00]}; never {@code null}
 * @param settingKey     stable identifier of the underlying
 *                       {@link com.example.aichinese.entity.SystemSetting}
 *                       row (today {@code "commission.rate"})
 * @param description    admin-facing human-readable description;
 *                       nullable when never seeded
 */
@JsonPropertyOrder({ "commissionRate", "settingKey", "description" })
public record CommissionSettingResponse(
        BigDecimal commissionRate,
        String settingKey,
        String description) {
}
