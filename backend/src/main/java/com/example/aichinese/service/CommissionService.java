package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.math.BigDecimal;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.CacheConfig;
import com.example.aichinese.dto.commerce.CommissionSettingRequest;
import com.example.aichinese.dto.commerce.CommissionSettingResponse;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class CommissionService {

    private static final Logger log = LoggerFactory.getLogger(CommissionService.class);

    private static final String DEFAULT_DESCRIPTION = "Editable platform commission rate represented as a decimal.";

    private final SystemSettingRepository systemSettingRepository;
    private final CommerceSettingsService commerceSettingsService;
    private final AuditLogService auditLogService;

    public CommissionService(
            SystemSettingRepository systemSettingRepository,
            CommerceSettingsService commerceSettingsService,
            AuditLogService auditLogService) {
        this.systemSettingRepository = Objects.requireNonNull(systemSettingRepository, "systemSettingRepository");
        this.commerceSettingsService = Objects.requireNonNull(commerceSettingsService, "commerceSettingsService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
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
    @CacheEvict(cacheNames = CacheConfig.COMMISSION_RATE, allEntries = true)
    public CommissionSettingResponse updateCommissionSetting(SecurityUserDetails currentUser, CommissionSettingRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Commission setting request is required.");
        }
        BigDecimal normalizedRate = commerceSettingsService.normalizeCommissionRate(request.commissionRate());
        SystemSetting setting = systemSettingRepository.findBySettingKey(CommerceSettingsService.PLATFORM_COMMISSION_RATE_KEY)
                .orElseGet(this::defaultCommissionSetting);

        String previousValue = setting.getSettingValue();
        setting.setSettingValue(normalizedRate.toPlainString());
        systemSettingRepository.save(setting);

        log.info("Commission rate changed from {} to {} by userId={}",
                previousValue, normalizedRate.toPlainString(), currentUser.getUserId());
        auditLogService.record(
                currentUser.getUser(),
                "COMMISSION_RATE_CHANGED",
                "SYSTEM_SETTING",
                setting.getId(),
                "Platform commission rate updated from " + previousValue + " to " + normalizedRate.toPlainString());
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
