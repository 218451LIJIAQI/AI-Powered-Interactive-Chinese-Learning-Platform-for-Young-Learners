package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
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
 *
 * <p>Uniqueness of {@link #settingKey} is enforced by a single named
 * constraint ({@code idx_system_settings_setting_key}) declared via
 * {@link UniqueConstraint} on the {@link Table @Table} annotation,
 * matching the Flyway baseline. {@link #settingKey} is also mapped
 * {@code updatable = false}: every consumer locates a setting via
 * {@code PlatformSettingsService.findBySettingKey(key)} where the key
 * is a compile-time constant; renaming a row's key in place would
 * silently strand all existing lookups. New settings are inserted
 * fresh; the editable trio ({@link #settingValue}, {@link #description},
 * {@link #editable}) is what gets tuned at runtime.</p>
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(
        name = "system_settings",
        uniqueConstraints = {
                @UniqueConstraint(name = "idx_system_settings_setting_key", columnNames = "setting_key")
        }
)
public class SystemSetting extends BaseEntity {

    @Column(name = "setting_key", nullable = false, updatable = false, length = 100)
    private String settingKey;

    @Column(name = "setting_value", nullable = false, length = 2000)
    private String settingValue;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "editable", nullable = false)
    private boolean editable = true;
}
