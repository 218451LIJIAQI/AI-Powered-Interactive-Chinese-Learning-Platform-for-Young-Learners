package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Runtime-tunable platform configuration value persisted in the
 * database (as opposed to compile-time application properties).
 *
 * <p>Used for things that an administrator should be able to adjust
 * without redeploying — commission rates, feature toggles, daily AI
 * quotas. {@link #settingKey} is unique and stable; the
 * {@code PlatformSettingsService} hides the type coercion, parsing
 * the {@link #settingValue} string as boolean, integer, decimal or
 * JSON depending on the consumer.</p>
 *
 * <p>{@link #editable} guards critical settings (e.g. admin email,
 * security keys) so the admin UI surfaces them as read-only even
 * though they remain technically writable through the database.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "system_settings", indexes = {
        @Index(name = "idx_system_settings_setting_key", columnList = "setting_key", unique = true)
})
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 2000)
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "editable", nullable = false)
    private boolean editable = true;
}
