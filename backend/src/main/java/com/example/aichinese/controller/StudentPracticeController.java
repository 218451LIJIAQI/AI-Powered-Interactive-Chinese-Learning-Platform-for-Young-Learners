package com.example.aichinese.controller;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteResponse;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.PracticeSetResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.PracticeService;

import lombok.RequiredArgsConstructor;

/**
 * Practice sets by type ({@code flashcards}, {@code listening}, {@code tracing}, {@code drag-drop},
 * {@code matching}, {@code typing}): a set, instant checks, hint steps, typing candidates and finished runs.
 */
@RestController
@RequestMapping("/api/v1/students/practice/{type}/sets")
@RequiredArgsConstructor
public class StudentPracticeController {

    private final PracticeService practiceService;

    @GetMapping
    public ApiResponse<PracticeSetsResponse> sets(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type) {
        return ApiResponse.success("Practice sets retrieved successfully.",
                practiceService.sets(currentUser, LearnParams.practiceType(type)));
    }

    @GetMapping("/{setId}")
    public ApiResponse<PracticeSetResponse> set(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId) {
        return ApiResponse.success("Practice set retrieved successfully.",
                practiceService.set(currentUser, LearnParams.practiceType(type), setId));
    }

    @PostMapping("/{setId}/check")
    public ApiResponse<AnswerCheckResponse> check(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody PracticeItemAnswer answer) {
        return ApiResponse.success("Answer checked.",
                practiceService.check(currentUser, LearnParams.practiceType(type), setId, answer));
    }

    @PostMapping("/{setId}/hint")
    public ApiResponse<HintDto.Step> hint(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody HintDto.Request request) {
        return ApiResponse.success("Hint retrieved.",
                practiceService.hint(currentUser, LearnParams.practiceType(type), setId, request));
    }

    /** Typing only: the words the typed pinyin spells. */
    @PostMapping("/{setId}/candidates")
    public ApiResponse<HintDto.Candidates> candidates(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody HintDto.CandidatesRequest request) {
        if (LearnParams.practiceType(type) != PracticeType.TYPING) {
            throw new ResponseStatusException(NOT_FOUND, "Only typing sets offer words to choose from.");
        }
        return ApiResponse.success("Words retrieved.", practiceService.candidates(currentUser, setId, request));
    }

    @PostMapping("/{setId}/complete")
    public ApiResponse<PracticeCompleteResponse> complete(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long setId,
            @Valid @RequestBody PracticeCompleteRequest request) {
        return ApiResponse.success("Practice set finished.",
                practiceService.complete(currentUser, LearnParams.practiceType(type), setId, request));
    }
}
