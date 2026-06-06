package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.commerce.CommissionSettingRequest;
import com.example.aichinese.dto.commerce.CommissionSettingResponse;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.CommerceMoneyService;
import com.example.aichinese.service.CommerceSettingsService;
import com.example.aichinese.service.CommissionService;

/**
 * Unit coverage for {@code CommissionService}: pins the
 * fallback-to-default behaviour when the {@code platform_commission_rate}
 * system setting row is missing, plus the audit trail emitted on every
 * write.
 */
class CommissionServiceTests {

    private static final String COMMISSION_KEY = CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY;
    private static final BigDecimal DEFAULT_RATE = new BigDecimal("0.20");
    private static final BigDecimal UPDATED_RATE = new BigDecimal("0.15");

    @Test
    void getCommissionSettingFallsBackWhenSettingRowIsMissing() {
        SystemSettingRepository repository = repositoryWithoutCommissionSetting();
        CommissionService service = service(repository);

        CommissionSettingResponse response = service.getCommissionSetting();

        assertThat(response.commissionRate()).isEqualByComparingTo(DEFAULT_RATE);
        assertThat(response.settingKey()).isEqualTo(COMMISSION_KEY);
        assertThat(response.description()).isEqualTo("Editable platform commission rate represented as a decimal.");
    }

    @Test
    void updateCommissionSettingCreatesMissingSettingRow() {
        SystemSettingRepository repository = repositoryWithoutCommissionSetting();
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CommissionService service = service(repository);

        CommissionSettingResponse response = service.updateCommissionSetting(currentUser(), new CommissionSettingRequest(UPDATED_RATE));

        assertThat(response.commissionRate()).isEqualByComparingTo(UPDATED_RATE);
        assertThat(response.settingKey()).isEqualTo(COMMISSION_KEY);
        verify(repository).save(any(SystemSetting.class));
    }

    private static SystemSettingRepository repositoryWithoutCommissionSetting() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(COMMISSION_KEY)).thenReturn(Optional.empty());
        return repository;
    }

    private static CommissionService service(SystemSettingRepository repository) {
        CommerceMoneyService moneyService = new CommerceMoneyService();
        CommerceSettingsService settingsService = new CommerceSettingsService(repository, moneyService);
        return new CommissionService(repository, settingsService, mock(AuditLogService.class));
    }

    private static SecurityUserDetails currentUser() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }
}
