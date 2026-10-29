package com.example.aichinese.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.service.LearningCatalogService;
import com.example.aichinese.service.PlatformSettingsService;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final LearningCatalogService learningCatalogService;
    private final PlatformSettingsService platformSettingsService;

    public PublicController(
            LearningCatalogService learningCatalogService,
            PlatformSettingsService platformSettingsService) {
        this.learningCatalogService = learningCatalogService;
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping("/levels")
    public ApiResponse<List<LearningLevelResponse>> getPublicLevels() {
        return ApiResponse.success(
                "Learning levels retrieved successfully.",
                learningCatalogService.getPublicLevels());
    }

    @GetMapping("/categories")
    public ApiResponse<List<LearningCategoryResponse>> getPublicCategories() {
        return ApiResponse.success(
                "Learning categories retrieved successfully.",
                learningCatalogService.getPublicCategories());
    }

    @GetMapping("/platform-settings")
    public ApiResponse<PublicPlatformSettingsResponse> getPublicPlatformSettings() {
        return ApiResponse.success(
                "Platform settings retrieved successfully.",
                platformSettingsService.getPublicPlatformSettings());
    }
}
