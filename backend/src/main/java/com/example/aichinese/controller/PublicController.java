package com.example.aichinese.controller;

import java.util.List;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.content.LearningLevelResponse;
import com.example.aichinese.dto.content.PublicCourseDetailResponse;
import com.example.aichinese.dto.content.PublicCourseSummaryResponse;
import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.service.LearningCatalogService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.PublicContentService;
import com.example.aichinese.service.PublicTeacherProfileService;

import jakarta.validation.constraints.Size;

/**
 * Unauthenticated catalogue + reference-data endpoints used by the
 * public-facing marketing surface: course browser, course detail,
 * teacher profile pages, learning levels / categories dropdowns, and
 * platform settings used for branding / disclaimer text.
 *
 * <h2>Input validation</h2>
 * <p>Every free-text query parameter ({@code keyword}, {@code accessType},
 * {@code learningType}, {@code sort}, course {@code id}) has a
 * {@code @Size} cap so a malicious caller cannot blow up the
 * underlying {@code LIKE} or filter operation with a multi-megabyte
 * string. The service layer applies further whitelisting.</p>
 *
 * <h2>Visibility</h2>
 * <p>Read endpoints always filter on the published / approved status
 * of the underlying entities at the service layer; this controller
 * itself owns no authorisation logic because the surface is
 * intentionally public.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final PublicTeacherProfileService publicTeacherProfileService;
    private final LearningCatalogService learningCatalogService;
    private final PublicContentService publicContentService;
    private final PlatformSettingsService platformSettingsService;

    public PublicController(
            PublicTeacherProfileService publicTeacherProfileService,
            LearningCatalogService learningCatalogService,
            PublicContentService publicContentService,
            PlatformSettingsService platformSettingsService) {
        this.publicTeacherProfileService = publicTeacherProfileService;
        this.learningCatalogService = learningCatalogService;
        this.publicContentService = publicContentService;
        this.platformSettingsService = platformSettingsService;
    }

    @GetMapping("/teachers/{id}")
    public ApiResponse<PublicTeacherProfileResponse> getApprovedTeacherProfile(@PathVariable Long id) {
        return ApiResponse.success(
                "Approved teacher profile retrieved successfully.",
                publicTeacherProfileService.getApprovedTeacherProfile(id));
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

    @GetMapping("/courses")
    public ApiResponse<PageResponse<PublicCourseSummaryResponse>> getPublicCourses(
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "levelId", required = false) Long levelId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @Size(max = 40, message = "Access type must not exceed 40 characters.")
            @RequestParam(value = "accessType", required = false) String accessType,
            @Size(max = 40, message = "Learning type must not exceed 40 characters.")
            @RequestParam(value = "learningType", required = false) String learningType,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Published courses retrieved successfully.",
                publicContentService.discoverCourses(keyword, levelId, categoryId, accessType, learningType, sort, page, size));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<PublicCourseDetailResponse> getPublicCourseDetail(
            @Size(max = 80, message = "Course id must not exceed 80 characters.")
            @PathVariable String id) {
        return ApiResponse.success(
                "Published course detail retrieved successfully.",
                publicContentService.getCourseDetail(id));
    }

}
