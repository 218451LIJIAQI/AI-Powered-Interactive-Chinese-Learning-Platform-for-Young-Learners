package com.example.aichinese.service;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.util.TextUtils;

@Service
public class CommerceSettingsService {

    private static final Logger log = LoggerFactory.getLogger(CommerceSettingsService.class);

    public static final String DEFAULT_CURRENCY_KEY = "default_currency";
    public static final String PLATFORM_COMMISSION_RATE_KEY = "platform_commission_rate";

    private static final String FALLBACK_CURRENCY = "MYR";
    private static final Pattern ISO_4217_PATTERN = Pattern.compile("[A-Z]{3}");
    private static final BigDecimal DEFAULT_COMMISSION_RATE = new BigDecimal("0.20");
    private static final BigDecimal MIN_COMMISSION_RATE = BigDecimal.ZERO;
    private static final BigDecimal MAX_COMMISSION_RATE = BigDecimal.ONE;

    private final SystemSettingRepository systemSettingRepository;
    private final CommerceMoneyService commerceMoneyService;

    public CommerceSettingsService(
            SystemSettingRepository systemSettingRepository,
            CommerceMoneyService commerceMoneyService) {
        this.systemSettingRepository = Objects.requireNonNull(systemSettingRepository, "systemSettingRepository");
        this.commerceMoneyService = Objects.requireNonNull(commerceMoneyService, "commerceMoneyService");
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.DEFAULT_CURRENCY)
    public String getDefaultCurrency() {
        return systemSettingRepository.findBySettingKey(DEFAULT_CURRENCY_KEY)
                .map(SystemSetting::getSettingValue)
                .map(TextUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(this::requireIsoCurrencyCode)
                .orElseGet(() -> {
                    log.debug("No '{}' setting found; defaulting to {}", DEFAULT_CURRENCY_KEY, FALLBACK_CURRENCY);
                    return FALLBACK_CURRENCY;
                });
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.COMMISSION_RATE)
    public BigDecimal getPlatformCommissionRate() {
        return systemSettingRepository.findBySettingKey(PLATFORM_COMMISSION_RATE_KEY)
                .map(SystemSetting::getSettingValue)
                .map(TextUtils::trimToNull)
                .filter(Objects::nonNull)
                .map(this::requireCommissionRate)
                .orElseGet(() -> {
                    log.debug("No '{}' setting found; defaulting to {}",
                            PLATFORM_COMMISSION_RATE_KEY, DEFAULT_COMMISSION_RATE);
                    return commerceMoneyService.normalizeRate(DEFAULT_COMMISSION_RATE);
                });
    }

    public BigDecimal normalizeCommissionRate(BigDecimal value) {
        BigDecimal rate = commerceMoneyService.normalizeRate(value);
        if (rate.compareTo(MIN_COMMISSION_RATE) < 0 || rate.compareTo(MAX_COMMISSION_RATE) > 0) {
            throw new IllegalArgumentException("Platform commission rate must be between 0 and 1.");
        }
        return rate;
    }

    private String requireIsoCurrencyCode(String value) {
        String normalized = value.toUpperCase(Locale.ROOT);
        if (!ISO_4217_PATTERN.matcher(normalized).matches()) {
            throw new IllegalStateException("Default currency must be a three-letter ISO 4217 code.");
        }
        return normalized;
    }

    private BigDecimal requireCommissionRate(String value) {
        try {
            return normalizeCommissionRate(new BigDecimal(value));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Platform commission rate must be numeric.", exception);
        }
    }
}
