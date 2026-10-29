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
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteResponse;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.PracticeSetResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.PracticeService;

/** Practice sets by type ({@code flashcards}, {@code quizzes}, {@code listening}, {@code tracing}, {@code drag-drop}, ...). */
@RestController
@RequestMapping("/api/v1/students/practice/{type}/sets")
public class StudentPracticeController {

    private final PracticeService practiceService;
    private final StudentLearningAccessService accessService;

    public StudentPracticeController(PracticeService practiceService, StudentLearningAccessService accessService) {
        this.practiceService = practiceService;
        this.accessService = accessService;
    }

    @GetMapping
    public ApiResponse<PracticeSetsResponse> sets(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type) {
        return ApiResponse.success("Practice sets retrieved successfully.",
                practiceService.sets(accessService.requireStudent(currentUser), LearnParams.practiceType(type)));
    }

    @GetMapping("/{setId}")
    public ApiResponse<PracticeSetResponse> set(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set retrieved successfully.",
                practiceService.set(accessService.requireStudent(currentUser), LearnParams.practiceType(type), setId));
    }

    @PostMapping("/{setId}/check")
    public ApiResponse<AnswerCheckResponse> check(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody PracticeItemAnswer answer) {
        return ApiResponse.success("Answer checked.", practiceService.check(
                accessService.requireStudent(currentUser), LearnParams.practiceType(type), setId, answer));
    }

    @PostMapping("/{setId}/complete")
    public ApiResponse<PracticeCompleteResponse> complete(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody PracticeCompleteRequest request) {
        return ApiResponse.success("Practice set finished.", practiceService.complete(
                accessService.requireStudent(currentUser), LearnParams.practiceType(type), setId, request));
    }
}
