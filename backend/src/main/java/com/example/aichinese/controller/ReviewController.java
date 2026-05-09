package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.communication.CreateReviewRequest;
import com.example.aichinese.dto.communication.ReviewResponse;
import com.example.aichinese.dto.communication.UpdateReviewRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.ReviewService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(ReviewService reviewService) {
        this.reviewService = reviewService;
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PostMapping
    public ApiResponse<ReviewResponse> createReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateReviewRequest request) {
        return ApiResponse.success(
                "Review created successfully.",
                reviewService.createReview(currentUser, request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/my")
    public ApiResponse<List<ReviewResponse>> getMyReviews(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Reviews retrieved successfully.",
                reviewService.getMyReviews(currentUser));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @PutMapping("/{id}")
    public ApiResponse<ReviewResponse> updateReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateReviewRequest request) {
        return ApiResponse.success(
                "Review updated successfully.",
                reviewService.updateMyReview(currentUser, id, request));
    }

    @PreAuthorize("hasRole('STUDENT')")
    @DeleteMapping("/{id}")
    public ApiResponse<ReviewResponse> hideReview(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Review hidden successfully.",
                reviewService.hideMyReview(currentUser, id));
    }
}
