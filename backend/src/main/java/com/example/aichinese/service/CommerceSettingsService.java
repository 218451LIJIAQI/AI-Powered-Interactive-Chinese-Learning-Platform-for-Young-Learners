package com.example.aichinese.service;

import java.math.BigDecimal;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;

@Service
public class CommerceSettingsService {

    public static final String DEFAULT_CURRENCY_KEY = "default_currency";
    public static final String PLATFORM_COMMISSION_RATE_KEY = "platform_commission_rate";

    private final SystemSettingRepository systemSettingRepository;
    private final CommerceMoneyService commerceMoneyService;

    public CommerceSettingsService(
            SystemSettingRepository systemSettingRepository,
            CommerceMoneyService commerceMoneyService) {
        this.systemSettingRepository = systemSettingRepository;
        this.commerceMoneyService = commerceMoneyService;
    }

    @Transactional(readOnly = true)
    public String getDefaultCurrency() {
        return systemSettingRepository.findBySettingKey(DEFAULT_CURRENCY_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(value -> !value.isBlank())
                .map(value -> value.trim().toUpperCase(Locale.ROOT))
                .orElse("MYR");
    }

    @Transactional(readOnly = true)
    public BigDecimal getPlatformCommissionRate() {
        return systemSettingRepository.findBySettingKey(PLATFORM_COMMISSION_RATE_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(value -> !value.isBlank())
                .map(BigDecimal::new)
                .map(commerceMoneyService::normalize)
                .orElse(new BigDecimal("0.20"));
    }
}
