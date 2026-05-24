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
 * Unit coverage for {@code CommerceSettingsService} — pins the
 * three-letter ISO-4217 currency validation, the {@code [0, 1]}
 * commission-rate range guard, and the {@code DECIMAL(N, 4)}
 * normalisation rules used by every commerce surface.
 */
class CommerceSettingsServiceTests {

    @Test
    void defaultCurrencyIsUppercaseIso4217Code() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        SystemSetting setting = setting(CommerceSettingsService.DEFAULT_CURRENCY_KEY, " myr ");
        when(repository.findBySettingKey(CommerceSettingsService.DEFAULT_CURRENCY_KEY)).thenReturn(Optional.of(setting));

        assertThat(service(repository).getDefaultCurrency()).isEqualTo("MYR");
    }

    @Test
    void defaultCurrencyRejectsInvalidCodesInsteadOfPersistingAmbiguousValues() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        SystemSetting setting = setting(CommerceSettingsService.DEFAULT_CURRENCY_KEY, "RINGGIT");
        when(repository.findBySettingKey(CommerceSettingsService.DEFAULT_CURRENCY_KEY)).thenReturn(Optional.of(setting));

        assertThatThrownBy(() -> service(repository).getDefaultCurrency())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("three-letter ISO 4217");
    }

    @Test
    void platformCommissionRateUsesFourDecimalRateScale() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        SystemSetting setting = setting(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY, "0.15555");
        when(repository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)).thenReturn(Optional.of(setting));

        assertThat(service(repository).getPlatformCommissionRate()).isEqualByComparingTo("0.1556");
    }

    @Test
    void platformCommissionRateRejectsOutOfRangeValues() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        SystemSetting setting = setting(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY, "1.01");
        when(repository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)).thenReturn(Optional.of(setting));

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

    private CommerceSettingsService service(SystemSettingRepository repository) {
        return new CommerceSettingsService(repository, new CommerceMoneyService());
    }

    private SystemSetting setting(String key, String value) {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(key);
        setting.setSettingValue(value);
        return setting;
    }
}
