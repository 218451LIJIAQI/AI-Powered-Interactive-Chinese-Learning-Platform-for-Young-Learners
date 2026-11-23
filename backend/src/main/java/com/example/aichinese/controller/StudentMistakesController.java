package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.MistakeListResponse;
import com.example.aichinese.dto.learn.MistakeReviewCompleteRequest;
import com.example.aichinese.dto.learn.MistakeReviewCompleteResponse;
import com.example.aichinese.dto.learn.MistakeReviewResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.MistakeService;

import lombok.RequiredArgsConstructor;

/** Mistakes from Learn and Practice, and the Mistakes review that clears them. */
@RestController
@RequestMapping("/api/v1/students/mistakes")
@RequiredArgsConstructor
public class StudentMistakesController {

    private final MistakeService mistakeService;

    @GetMapping
    public ApiResponse<MistakeListResponse> list(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "source", required = false) String source,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success("Mistakes retrieved successfully.",
                mistakeService.list(currentUser, LearnParams.optionalMistakeSource(source), size));
    }

    @GetMapping("/review")
    public ApiResponse<MistakeReviewResponse> review(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Review retrieved successfully.", mistakeService.review(currentUser));
    }

    @PostMapping("/review/check")
    public ApiResponse<AnswerCheckResponse> check(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody QuestionAnswer answer) {
        return ApiResponse.success("Answer checked.", mistakeService.check(currentUser, answer));
    }

    @PostMapping("/review/hint")
    public ApiResponse<HintDto.Step> hint(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody HintDto.QuestionRequest request) {
        return ApiResponse.success("Hint retrieved.", mistakeService.hint(currentUser, request));
    }

    @PostMapping("/review/complete")
    public ApiResponse<MistakeReviewCompleteResponse> complete(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody MistakeReviewCompleteRequest request) {
        return ApiResponse.success("Review finished.", mistakeService.completeReview(currentUser, request));
    }
}
