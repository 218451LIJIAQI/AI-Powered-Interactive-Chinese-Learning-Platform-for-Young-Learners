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

    private final SystemSettingRepository systemSettingRepository;
    private final CommerceSettingsService commerceSettingsService;
    private final CommerceAuditService commerceAuditService;

    public CommissionService(
            SystemSettingRepository systemSettingRepository,
            CommerceSettingsService commerceSettingsService,
            CommerceAuditService commerceAuditService) {
        this.systemSettingRepository = systemSettingRepository;
        this.commerceSettingsService = commerceSettingsService;
        this.commerceAuditService = commerceAuditService;
    }

    @Transactional(readOnly = true)
    public CommissionSettingResponse getCommissionSetting() {
        SystemSetting setting = systemSettingRepository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)
                .orElseThrow();
        return new CommissionSettingResponse(
                commerceSettingsService.getPlatformCommissionRate(),
                setting.getSettingKey(),
                setting.getDescription());
    }

    @Transactional
    public CommissionSettingResponse updateCommissionSetting(SecurityUserDetails currentUser, CommissionSettingRequest request) {
        SystemSetting setting = systemSettingRepository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)
                .orElseThrow();
        setting.setSettingValue(request.commissionRate().toPlainString());
        systemSettingRepository.save(setting);
        commerceAuditService.record(
                currentUser.getUser(),
                "COMMISSION_RATE_CHANGED",
                "SYSTEM_SETTING",
                setting.getId(),
                "Platform commission rate updated to " + request.commissionRate().toPlainString());
        return new CommissionSettingResponse(request.commissionRate(), setting.getSettingKey(), setting.getDescription());
    }
}
