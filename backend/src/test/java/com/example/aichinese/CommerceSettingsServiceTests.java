package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;

/**
 * Unit coverage for {@code CommerceSettingsService}: pins the
 * three-letter ISO-4217 currency validation, the {@code [0, 1]}
 * commission-rate range guard, and the {@code DECIMAL(N, 4)}
 * normalisation rules used by every commerce surface.
 */
class CommerceSettingsServiceTests {

    @Test
    void defaultCurrencyIsUppercaseIso4217Code() {
        SystemSettingRepository repository = repositoryWithSetting(
                CommerceSettingsService.DEFAULT_CURRENCY_KEY,
                " myr ");

        assertThat(service(repository).getDefaultCurrency()).isEqualTo("MYR");
    }

    @Test
    void defaultCurrencyRejectsInvalidCodesInsteadOfPersistingAmbiguousValues() {
        SystemSettingRepository repository = repositoryWithSetting(
                CommerceSettingsService.DEFAULT_CURRENCY_KEY,
                "RINGGIT");

        assertThatThrownBy(() -> service(repository).getDefaultCurrency())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("three-letter ISO 4217");
    }

    @Test
    void platformCommissionRateUsesFourDecimalRateScale() {
        SystemSettingRepository repository = repositoryWithSetting(
                CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY,
                "0.15555");

        assertThat(service(repository).getPlatformCommissionRate()).isEqualByComparingTo("0.1556");
    }

    @Test
    void platformCommissionRateRejectsOutOfRangeValues() {
        SystemSettingRepository repository = repositoryWithSetting(
                CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY,
                "1.01");

        assertThatThrownBy(() -> service(repository).getPlatformCommissionRate())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("between 0 and 1");
    }

    @Test
    void percentageKeepsRatePrecisionBeforeMoneyRounding() {
        CommerceMoneyService moneyService = new CommerceMoneyService();

        assertThat(moneyService.percentage(new BigDecimal("100.00"), new BigDecimal("0.1555")))
                .isEqualByComparingTo("15.55");
    }

    private static CommerceSettingsService service(SystemSettingRepository repository) {
        return new CommerceSettingsService(repository, new CommerceMoneyService());
    }

    private static SystemSettingRepository repositoryWithSetting(String key, String value) {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(key)).thenReturn(Optional.of(setting(key, value)));
        return repository;
    }

    private static SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
