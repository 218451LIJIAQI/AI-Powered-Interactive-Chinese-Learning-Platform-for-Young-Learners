package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ReviewViews;
import com.example.aichinese.dto.review.RejectionReasonRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.AdminClassContentService;
import com.example.aichinese.service.classes.ClassReviewService;

import jakarta.validation.Valid;

/** Admin review queue of teacher classes and the Teacher classes list (archive and restore). */
@RestController
@RequestMapping("/api/v1/admin")
public class AdminClassController {

    private final ClassReviewService reviews;
    private final AdminClassContentService content;

    public AdminClassController(ClassReviewService reviews, AdminClassContentService content) {
        this.reviews = reviews;
        this.content = content;
    }

    // ----- review queue -----

    @GetMapping("/class-reviews")
    public ApiResponse<ReviewViews.Queue> queue(@RequestParam(value = "type", required = false) String type) {
        return ApiResponse.success("Review queue retrieved successfully.", reviews.queue(type));
    }

    @GetMapping("/class-reviews/classes/{classId}")
    public ApiResponse<ReviewViews.Detail> classReview(@PathVariable Long classId) {
        return ApiResponse.success("Review retrieved successfully.", reviews.classDetail(classId));
    }

    @PostMapping("/class-reviews/classes/{classId}/approve")
    public ApiResponse<ReviewViews.Queue> approveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Approved.", reviews.approveClass(currentUser, classId));
    }

    @PostMapping("/class-reviews/classes/{classId}/request-changes")
    public ApiResponse<ReviewViews.Queue> requestClassChanges(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId,
            @Valid @RequestBody RejectionReasonRequest request) {
        return ApiResponse.success("Changes requested.", reviews.requestClassChanges(currentUser, classId, request.reason()));
    }

    @GetMapping("/class-reviews/lessons/{lessonId}")
    public ApiResponse<ReviewViews.Detail> lessonReview(@PathVariable Long lessonId) {
        return ApiResponse.success("Review retrieved successfully.", reviews.lessonDetail(lessonId));
    }

    @PostMapping("/class-reviews/lessons/{lessonId}/approve")
    public ApiResponse<ReviewViews.Queue> approveLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Approved.", reviews.approveLesson(currentUser, lessonId));
    }

    @PostMapping("/class-reviews/lessons/{lessonId}/request-changes")
    public ApiResponse<ReviewViews.Queue> requestLessonChanges(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody RejectionReasonRequest request) {
        return ApiResponse.success("Changes requested.", reviews.requestLessonChanges(currentUser, lessonId, request.reason()));
    }

    // ----- teacher classes -----

    @GetMapping("/teacher-classes")
    public ApiResponse<PageResponse<ReviewViews.ContentRow>> teacherClasses(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "teacherId", required = false) Long teacherId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "10") int size) {
        return ApiResponse.success("Teacher classes retrieved successfully.",
                content.list(keyword, type, status, teacherId, page, size));
    }

    @GetMapping("/teacher-classes/teachers")
    public ApiResponse<List<ReviewViews.TeacherOption>> teachers() {
        return ApiResponse.success("Teachers retrieved successfully.", content.teachers());
    }

    @GetMapping("/teacher-classes/classes/{classId}")
    public ApiResponse<ReviewViews.ContentDetail> classDetail(@PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", content.classDetail(classId));
    }

    @PostMapping("/teacher-classes/classes/{classId}/archive")
    public ApiResponse<ReviewViews.ContentDetail> archiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class archived.", content.archiveClass(currentUser, classId));
    }

    @PostMapping("/teacher-classes/classes/{classId}/restore")
    public ApiResponse<ReviewViews.ContentDetail> restoreClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class restored.", content.restoreClass(currentUser, classId));
    }

    @GetMapping("/teacher-classes/lessons/{lessonId}")
    public ApiResponse<ReviewViews.ContentDetail> lessonDetail(@PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", content.lessonDetail(lessonId));
    }

    @PostMapping("/teacher-classes/lessons/{lessonId}/archive")
    public ApiResponse<ReviewViews.ContentDetail> archiveLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson archived.", content.archiveLesson(currentUser, lessonId));
    }

    @PostMapping("/teacher-classes/lessons/{lessonId}/restore")
    public ApiResponse<ReviewViews.ContentDetail> restoreLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson restored.", content.restoreLesson(currentUser, lessonId));
    }
}
