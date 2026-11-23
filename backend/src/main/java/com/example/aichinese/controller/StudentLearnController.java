package com.example.aichinese.controller;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.GuidebookResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.LessonCompleteRequest;
import com.example.aichinese.dto.learn.LessonCompleteResponse;
import com.example.aichinese.dto.learn.LessonPlayResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.LearnPathService;

import lombok.RequiredArgsConstructor;

/** The student's Learn path and lesson player. */
@RestController
@RequestMapping("/api/v1/students/learn")
@RequiredArgsConstructor
public class StudentLearnController {

    private final LearnPathService learnPathService;

    @GetMapping("/path")
    public ApiResponse<LearnPathResponse> path(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Learning path retrieved successfully.", learnPathService.path(currentUser));
    }

    @GetMapping("/units/{unitId}/guidebook")
    public ApiResponse<GuidebookResponse> guidebook(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long unitId) {
        return ApiResponse.success("Guidebook retrieved successfully.", learnPathService.guidebook(currentUser, unitId));
    }

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonPlayResponse> lesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", learnPathService.lesson(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/check")
    public ApiResponse<AnswerCheckResponse> check(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody QuestionAnswer answer) {
        return ApiResponse.success("Answer checked.", learnPathService.check(currentUser, lessonId, answer));
    }

    @PostMapping("/lessons/{lessonId}/hint")
    public ApiResponse<HintDto.Step> hint(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody HintDto.QuestionRequest request) {
        return ApiResponse.success("Hint retrieved.", learnPathService.hint(currentUser, lessonId, request));
    }

    @PostMapping("/lessons/{lessonId}/complete")
    public ApiResponse<LessonCompleteResponse> complete(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody LessonCompleteRequest request) {
        return ApiResponse.success("Lesson completed.", learnPathService.complete(currentUser, lessonId, request));
    }
}
