package com.example.aichinese.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.commerce.CommissionSettingRequest;
import com.example.aichinese.dto.commerce.CommissionSettingResponse;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class CommissionService {

    private static final String DEFAULT_DESCRIPTION = "Editable platform commission rate represented as a decimal.";

    private final SystemSettingRepository systemSettingRepository;
    private final CommerceSettingsService commerceSettingsService;
    private final AuditLogService auditLogService;

    public CommissionService(
            SystemSettingRepository systemSettingRepository,
            CommerceSettingsService commerceSettingsService,
            AuditLogService auditLogService) {
        this.systemSettingRepository = systemSettingRepository;
        this.commerceSettingsService = commerceSettingsService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public CommissionSettingResponse getCommissionSetting() {
        SystemSetting setting = systemSettingRepository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)
                .orElseGet(this::defaultCommissionSetting);
        return new CommissionSettingResponse(
                commerceSettingsService.getPlatformCommissionRate(),
                setting.getSettingKey(),
                setting.getDescription());
    }

    @Transactional
    public CommissionSettingResponse updateCommissionSetting(SecurityUserDetails currentUser, CommissionSettingRequest request) {
        java.math.BigDecimal normalizedRate = commerceSettingsService.normalizeCommissionRate(request.commissionRate());
        SystemSetting setting = systemSettingRepository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)
                .orElseGet(this::defaultCommissionSetting);
        setting.setSettingValue(normalizedRate.toPlainString());
        systemSettingRepository.save(setting);
        auditLogService.record(
                currentUser.getUser(),
                "COMMISSION_RATE_CHANGED",
                "SYSTEM_SETTING",
                setting.getId(),
                "Platform commission rate updated to " + normalizedRate.toPlainString());
        return new CommissionSettingResponse(normalizedRate, setting.getSettingKey(), setting.getDescription());
    }

    private SystemSetting defaultCommissionSetting() {
        SystemSetting setting = new SystemSetting();
        setting.setSettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY);
        setting.setSettingValue(commerceSettingsService.getPlatformCommissionRate().toPlainString());
        setting.setDescription(DEFAULT_DESCRIPTION);
        setting.setEditable(true);
        return setting;
    }

}
