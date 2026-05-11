package com.example.aichinese.controller;

import java.util.List;

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
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.dto.publicview.PublicTeacherProfileResponse;
import com.example.aichinese.service.LearningCatalogService;
import com.example.aichinese.service.PublicContentService;
import com.example.aichinese.service.PublicTeacherProfileService;
import com.example.aichinese.service.ReviewService;

@RestController
@RequestMapping("/api/v1/public")
public class PublicController {

    private final PublicTeacherProfileService publicTeacherProfileService;
    private final LearningCatalogService learningCatalogService;
    private final PublicContentService publicContentService;
    private final ReviewService reviewService;

    public PublicController(
            PublicTeacherProfileService publicTeacherProfileService,
            LearningCatalogService learningCatalogService,
            PublicContentService publicContentService,
            ReviewService reviewService) {
        this.publicTeacherProfileService = publicTeacherProfileService;
        this.learningCatalogService = learningCatalogService;
        this.publicContentService = publicContentService;
        this.reviewService = reviewService;
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

    @GetMapping("/courses")
    public ApiResponse<PageResponse<PublicCourseSummaryResponse>> getPublicCourses(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "levelId", required = false) Long levelId,
            @RequestParam(value = "categoryId", required = false) Long categoryId,
            @RequestParam(value = "accessType", required = false) String accessType,
            @RequestParam(value = "learningType", required = false) String learningType,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success(
                "Published courses retrieved successfully.",
                publicContentService.discoverCourses(keyword, levelId, categoryId, accessType, learningType, sort, page, size));
    }

    @GetMapping("/courses/{id}")
    public ApiResponse<PublicCourseDetailResponse> getPublicCourseDetail(@PathVariable String id) {
        return ApiResponse.success(
                "Published course detail retrieved successfully.",
                publicContentService.getCourseDetail(id));
    }

    @GetMapping("/reviews")
    public ApiResponse<List<ReviewResponse>> getPublicReviews(
            @RequestParam("targetType") String targetType,
            @RequestParam("targetId") Long targetId) {
        return ApiResponse.success(
                "Public reviews retrieved successfully.",
                reviewService.getPublicVisibleReviews(targetType, targetId));
    }
}
