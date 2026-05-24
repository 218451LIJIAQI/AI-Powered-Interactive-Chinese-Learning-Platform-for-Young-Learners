package com.example.aichinese.dto.commerce;

import java.math.BigDecimal;

/**
 * Current commission setting returned by:
 * <ul>
 *   <li>{@code GET /api/v1/admin/system-settings/commission}</li>
 *   <li>{@code PUT /api/v1/admin/system-settings/commission}
 *       (response side, echoes the freshly-saved row)</li>
 * </ul>
 *
 * <p>{@link #commissionRate} is the same {@code [0.0, 1.0]}
 * fraction documented on {@link CommissionSettingRequest}.
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
 */
public record CommissionSettingResponse(
        BigDecimal commissionRate,
        String settingKey,
        String description) {
}
