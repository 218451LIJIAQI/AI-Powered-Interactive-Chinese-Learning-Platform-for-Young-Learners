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

/**
 * Reads the editable commerce-tier system settings: default
 * currency (ISO 4217) and platform commission rate.
 *
 * <p>Values live in {@link SystemSetting} and are admin-editable via
 * {@link CommissionService} / the system-settings UI. Defaults
 * ({@code MYR} currency, 20% commission) are applied when the row is
 * missing so a fresh deployment always has a coherent commerce
 * baseline. Validation refuses anything outside [0,1] for commission
 * and anything that does not match the strict three-letter ISO 4217
 * pattern for currency — preventing the rest of the commerce stack
 * from inheriting malformed numeric input.</p>
 */
@Service
public class CommerceSettingsService {

    private static final Logger log = LoggerFactory.getLogger(CommerceSettingsService.class);

    public static final String DEFAULT_CURRENCY_KEY = "default_currency";
    public static final String PLATFORM_COMMISSION_RATE_KEY = "platform_commission_rate";

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
                .filter(value -> !value.isBlank())
                .map(this::requireIsoCurrencyCode)
                .orElseGet(() -> {
                    log.debug("No '{}' setting found; defaulting to MYR", DEFAULT_CURRENCY_KEY);
                    return "MYR";
                });
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = CacheConfig.COMMISSION_RATE)
    public BigDecimal getPlatformCommissionRate() {
        return systemSettingRepository.findBySettingKey(PLATFORM_COMMISSION_RATE_KEY)
                .map(SystemSetting::getSettingValue)
                .filter(value -> !value.isBlank())
                .map(this::requireCommissionRate)
                .orElseGet(() -> {
                    log.debug("No '{}' setting found; defaulting to {}",
                            PLATFORM_COMMISSION_RATE_KEY, DEFAULT_COMMISSION_RATE);
                    return commerceMoneyService.normalizeRate(DEFAULT_COMMISSION_RATE);
                });
    }

    /**
     * Validates and normalises a commission rate to
     * {@link CommerceMoneyService#RATE_SCALE} decimal places.
     *
     * @throws IllegalArgumentException if the rate is outside [0, 1]
     */
    public BigDecimal normalizeCommissionRate(BigDecimal value) {
        BigDecimal rate = commerceMoneyService.normalizeRate(value);
        if (rate.compareTo(MIN_COMMISSION_RATE) < 0 || rate.compareTo(MAX_COMMISSION_RATE) > 0) {
            throw new IllegalArgumentException("Platform commission rate must be between 0 and 1.");
        }
        return rate;
    }

    private String requireIsoCurrencyCode(String value) {
        String normalized = value.trim().toUpperCase(Locale.ROOT);
        if (!ISO_4217_PATTERN.matcher(normalized).matches()) {
            throw new IllegalStateException("Default currency must be a three-letter ISO 4217 code.");
        }
        return normalized;
    }

    private BigDecimal requireCommissionRate(String value) {
        try {
            return normalizeCommissionRate(new BigDecimal(value.trim()));
        } catch (NumberFormatException exception) {
            throw new IllegalStateException("Platform commission rate must be numeric.", exception);
        }
    }
}
