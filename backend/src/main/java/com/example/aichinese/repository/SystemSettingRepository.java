package com.example.aichinese.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.aichinese.entity.SystemSetting;

/**
 * Persistence operations for {@link SystemSetting} \u2014 the key/value
 * store of runtime-tunable platform settings (e.g. enrolment open/close
 * flags, daily-AI rate-limit overrides). Settings are looked up by
 * their string key on every gated request, so the unique-index-backed
 * {@link #findBySettingKey(String)} stays the hot read path.
 */
public interface SystemSettingRepository extends JpaRepository<SystemSetting, Long> {

    /**
     * Looks up a setting by its canonical string key. Backed by the
     * unique index on the {@code setting_key} column so the lookup
     * stays O(log&nbsp;n) even as settings accumulate.
     */
    Optional<SystemSetting> findBySettingKey(String settingKey);
}
