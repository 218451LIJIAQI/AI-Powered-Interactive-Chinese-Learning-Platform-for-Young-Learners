package com.example.aichinese.entity;

import com.example.aichinese.entity.base.BaseEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
