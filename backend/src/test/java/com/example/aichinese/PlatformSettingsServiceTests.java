package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.system.AdminPlatformSettingsResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.PlatformSettingsService;

/**
 * Unit coverage for {@code PlatformSettingsService} - pins the
 * default values applied when system-setting rows are missing
 * (platform name, AI disclaimer, notification toggles, content
 * filtering switches) and the admin / public response shape split.
 */
class PlatformSettingsServiceTests {

    private static final String DEFAULT_PLATFORM_NAME =
            "AI-Powered Interactive Chinese Learning Platform for Young Learners";
    private static final String DEFAULT_AI_DISCLAIMER =
            "AI-generated content may not always be accurate. Please review before use.";
    private static final String UPDATED_AI_DISCLAIMER = "Teacher guidance is still important.";
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String PASSWORD_HASH = "password-hash";
    private static final int UPDATED_SETTINGS_COUNT = 7;

    @Test
    void getAdminPlatformSettingsFallsBackWhenSettingRowsAreMissing() {
        SystemSettingRepository repository = repositoryWithMissingSettings();
        PlatformSettingsService service = service(repository);

        AdminPlatformSettingsResponse response = service.getAdminPlatformSettings();

        assertThat(response.platformName()).isEqualTo(DEFAULT_PLATFORM_NAME);
        assertThat(response.aiDisclaimerText()).isEqualTo(DEFAULT_AI_DISCLAIMER);
        assertThat(response.emailNotifications()).isTrue();
        assertThat(response.inAppNotifications()).isTrue();
        assertThat(response.dailySummary()).isFalse();
        assertThat(response.strictContentFiltering()).isTrue();
        assertThat(response.sensitiveWordFilter()).isTrue();
        assertThat(response.aiSafetyCheck()).isTrue();
    }

    @Test
    void updateAdminPlatformSettingsCreatesMissingSettingRows() {
        SystemSettingRepository repository = repositoryWithMissingSettings();
        when(repository.save(any(SystemSetting.class)))
                .thenAnswer(invocation -> invocation.getArgument(0, SystemSetting.class));
        PlatformSettingsService service = service(repository);

        AdminPlatformSettingsResponse response = service.updateAdminPlatformSettings(
                currentUser(),
                new AdminPlatformSettingsUpdateRequest(
                        UPDATED_AI_DISCLAIMER,
                        false,
                        true,
                        true,
                        false,
                        true,
                        false));

        assertThat(response.platformName()).isEqualTo(DEFAULT_PLATFORM_NAME);
        assertThat(response.aiDisclaimerText()).isEqualTo(UPDATED_AI_DISCLAIMER);
        assertThat(response.emailNotifications()).isFalse();
        assertThat(response.inAppNotifications()).isTrue();
        assertThat(response.dailySummary()).isTrue();
        assertThat(response.strictContentFiltering()).isFalse();
        assertThat(response.sensitiveWordFilter()).isTrue();
        assertThat(response.aiSafetyCheck()).isFalse();
        verify(repository, times(UPDATED_SETTINGS_COUNT)).save(any(SystemSetting.class));
    }

    private SystemSettingRepository repositoryWithMissingSettings() {
        SystemSettingRepository repository = mock(SystemSettingRepository.class);
        when(repository.findBySettingKey(anyString())).thenReturn(Optional.empty());
        return repository;
    }

    private PlatformSettingsService service(SystemSettingRepository repository) {
        return new PlatformSettingsService(repository, mock(AuditLogService.class));
    }

    private SecurityUserDetails currentUser() {
        User user = new User();
        user.setEmail(ADMIN_EMAIL);
        user.setPasswordHash(PASSWORD_HASH);
        user.setActive(true);
        return new SecurityUserDetails(user);
    }
}
