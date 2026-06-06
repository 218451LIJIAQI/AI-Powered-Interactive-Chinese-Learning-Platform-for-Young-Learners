package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.util.TextUtils;

@Service
public class ContentSafetySettingsReader {

    private final SystemSettingRepository systemSettingRepository;

    public ContentSafetySettingsReader(SystemSettingRepository systemSettingRepository) {
        this.systemSettingRepository = Objects.requireNonNull(systemSettingRepository, "systemSettingRepository");
    }

    @Cacheable(cacheNames = CacheConfig.SENSITIVE_WORD_FILTER_ENABLED)
    public boolean sensitiveWordFilterEnabled() {
        return systemSettingRepository.findBySettingKey(PlatformSettingsService.SENSITIVE_WORD_FILTER_KEY)
                .map(SystemSetting::getSettingValue)
                .map(TextUtils::trimToNull)
                .map(value -> !"false".equalsIgnoreCase(value))
                .orElse(true);
    }
}
