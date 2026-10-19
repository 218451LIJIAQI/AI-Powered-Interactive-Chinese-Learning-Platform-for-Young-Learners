package com.example.aichinese.controller;

import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsResponse;
import com.example.aichinese.dto.system.AdminPlatformSettingsUpdateRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.PlatformSettingsService;

import jakarta.validation.Valid;

/**
 * Admin-only platform settings (read-only platform name, AI disclaimer, sensitive-word filter and
 * AI safety check toggles).
 * Access is restricted to ADMIN by the {@code /api/v1/admin/**} rule in SecurityConfig.
 */
@RestController
@RequestMapping("/api/v1/admin/system-settings")
public class AdminSettingsController {

    private final PlatformSettingsService platformSettingsService;

    public AdminSettingsController(PlatformSettingsService platformSettingsService) {
        this.platformSettingsService = Objects.requireNonNull(platformSettingsService, "platformSettingsService");
    }

    @GetMapping("/platform")
    public ApiResponse<AdminPlatformSettingsResponse> getPlatformSettings() {
        return ApiResponse.success(
                "Platform settings retrieved successfully.",
                platformSettingsService.getAdminPlatformSettings());
    }

    @PutMapping("/platform")
    public ApiResponse<AdminPlatformSettingsResponse> updatePlatformSettings(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminPlatformSettingsUpdateRequest request) {
        return ApiResponse.success(
                "Platform settings updated successfully.",
                platformSettingsService.updateAdminPlatformSettings(currentUser, request));
    }
}
