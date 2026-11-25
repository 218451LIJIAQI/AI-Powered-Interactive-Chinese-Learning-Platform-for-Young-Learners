package com.example.aichinese.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.content.LearningCategoryResponse;
import com.example.aichinese.dto.support.SupportRequestCreatedResponse;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.dto.system.PublicPlatformSettingsResponse;
import com.example.aichinese.security.ClientIpResolver;
import com.example.aichinese.security.PublicAuthRequestLimiter;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.support.SupportService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicController {

    private final ClassCatalogueService classCatalogue;
    private final PlatformSettingsService platformSettingsService;
    private final SupportService supportService;
    private final PublicAuthRequestLimiter publicAuthRequestLimiter;
    private final ClientIpResolver clientIpResolver;

    @GetMapping("/classes")
    public ApiResponse<PageResponse<ClassViews.ClassCard>> listClasses(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.success("Classes retrieved successfully.", classCatalogue.publicClasses(page, size));
    }

    @GetMapping("/classes/{classId:\\d+}")
    public ApiResponse<ClassViews.ClassPreview> previewClass(@PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", classCatalogue.preview(classId));
    }

    @GetMapping("/categories")
    public ApiResponse<List<LearningCategoryResponse>> getPublicCategories() {
        return ApiResponse.success(
                "Learning categories retrieved successfully.",
                classCatalogue.topics());
    }

    @GetMapping("/platform-settings")
    public ApiResponse<PublicPlatformSettingsResponse> getPublicPlatformSettings() {
        return ApiResponse.success(
                "Platform settings retrieved successfully.",
                platformSettingsService.getPublicPlatformSettings());
    }

    /** "Send us a message" on the public Help page: a visitor's support request, answered by e-mail. Rate-limited. */
    @PostMapping("/support")
    public ApiResponse<SupportRequestCreatedResponse> sendSupportMessage(
            @Valid @RequestBody VisitorSupportRequest request,
            HttpServletRequest httpRequest) {
        publicAuthRequestLimiter.checkSupportMessageAllowed(request.email(), clientIpResolver.resolve(httpRequest));
        return ApiResponse.success("Message sent.", supportService.createForVisitor(request));
    }
}
