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

class CommissionServiceTests {

    @Test
    void getCommissionSettingFallsBackWhenSettingRowIsMissing() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)).thenReturn(Optional.empty());
        CommissionService service = service(repository);

        CommissionSettingResponse response = service.getCommissionSetting();

        assertThat(response.commissionRate()).isEqualByComparingTo("0.20");
        assertThat(response.settingKey()).isEqualTo(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY);
        assertThat(response.description()).isEqualTo("Editable platform commission rate represented as a decimal.");
    }

    @Test
    void updateCommissionSettingCreatesMissingSettingRow() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)).thenReturn(Optional.empty());
        when(repository.save(any(SystemSetting.class))).thenAnswer(invocation -> invocation.getArgument(0));
        CommissionService service = service(repository);

        CommissionSettingResponse response = service.updateCommissionSetting(currentUser(), new CommissionSettingRequest(new BigDecimal("0.15")));

        assertThat(response.commissionRate()).isEqualByComparingTo("0.15");
        assertThat(response.settingKey()).isEqualTo(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY);
        verify(repository).save(any(SystemSetting.class));
    }

    private CommissionService service(SystemSettingRepository repository) {
        CommerceMoneyService moneyService = new CommerceMoneyService();
        CommerceSettingsService settingsService = new CommerceSettingsService(repository, moneyService);
        return new CommissionService(repository, settingsService, mock(AuditLogService.class));
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setEmail("admin@example.com");
        user.setPasswordHash("password-hash");
        user.setActive(true);
        return new SecurityUserDetails(user);
    }
}
