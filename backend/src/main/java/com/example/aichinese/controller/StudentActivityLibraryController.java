package com.example.aichinese.controller;

import java.util.Optional;
import java.util.Objects;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.student.activity.StudentActivityCompletionResponse;
import com.example.aichinese.dto.student.activity.StudentActivitySummaryResponse;
import com.example.aichinese.dto.student.activity.StudentDragDropActivityResponse;
import com.example.aichinese.dto.student.activity.StudentFlashcardResponse;
import com.example.aichinese.dto.student.activity.StudentListeningExerciseResponse;
import com.example.aichinese.dto.student.activity.StudentMatchingGameResponse;
import com.example.aichinese.dto.student.activity.StudentStoryActivityResponse;
import com.example.aichinese.dto.student.activity.StudentTracingExerciseResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentActivityLibraryService;
import com.example.aichinese.service.StudentActivityLibraryService.ActivityFilter;

import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/students/activities")
public class StudentActivityLibraryController {

    private final StudentActivityLibraryService studentActivityLibraryService;

    public StudentActivityLibraryController(StudentActivityLibraryService studentActivityLibraryService) {
        this.studentActivityLibraryService = Objects.requireNonNull(
                studentActivityLibraryService,
                "studentActivityLibraryService");
    }

    @GetMapping
    public ApiResponse<PageResponse<StudentActivitySummaryResponse>> listLibrary(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 60, message = "Type must not exceed 60 characters.")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ActivityFilter filter = buildFilter(hskLevel, ageBand, difficulty, topic, tag, keyword, type);
        return ApiResponse.success(
                "Activity library retrieved successfully.",
                studentActivityLibraryService.listLibrary(currentUser, filter, page, size));
    }

    @GetMapping("/flashcards/{id}")
    public ApiResponse<StudentFlashcardResponse> getFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Flashcard retrieved successfully.",
                studentActivityLibraryService.getFlashcard(currentUser, id));
    }

    @PostMapping("/flashcards/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Flashcard completion recorded.",
                studentActivityLibraryService.completeFlashcard(currentUser, id));
    }

    @GetMapping("/matching-games/{id}")
    public ApiResponse<StudentMatchingGameResponse> getMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Matching game retrieved successfully.",
                studentActivityLibraryService.getMatchingGame(currentUser, id));
    }

    @PostMapping("/matching-games/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Matching game completion recorded.",
                studentActivityLibraryService.completeMatchingGame(currentUser, id));
    }

    @GetMapping("/listening-exercises/{id}")
    public ApiResponse<StudentListeningExerciseResponse> getListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Listening exercise retrieved successfully.",
                studentActivityLibraryService.getListeningExercise(currentUser, id));
    }

    @PostMapping("/listening-exercises/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Listening exercise completion recorded.",
                studentActivityLibraryService.completeListeningExercise(currentUser, id));
    }

    @GetMapping("/tracing-exercises/{id}")
    public ApiResponse<StudentTracingExerciseResponse> getTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Tracing exercise retrieved successfully.",
                studentActivityLibraryService.getTracingExercise(currentUser, id));
    }

    @PostMapping("/tracing-exercises/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Tracing exercise completion recorded.",
                studentActivityLibraryService.completeTracingExercise(currentUser, id));
    }

    @GetMapping("/drag-drop-activities/{id}")
    public ApiResponse<StudentDragDropActivityResponse> getDragDropActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Drag-drop activity retrieved successfully.",
                studentActivityLibraryService.getDragDropActivity(currentUser, id));
    }

    @PostMapping("/drag-drop-activities/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeDragDropActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Drag-drop activity completion recorded.",
                studentActivityLibraryService.completeDragDropActivity(currentUser, id));
    }

    @GetMapping("/story-activities/{id}")
    public ApiResponse<StudentStoryActivityResponse> getStoryActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Story activity retrieved successfully.",
                studentActivityLibraryService.getStoryActivity(currentUser, id));
    }

    @PostMapping("/story-activities/{id}/complete")
    public ApiResponse<StudentActivityCompletionResponse> completeStoryActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Story activity completion recorded.",
                studentActivityLibraryService.completeStoryActivity(currentUser, id));
    }

    private static ActivityFilter buildFilter(
            Integer hskLevel,
            String ageBand,
            String difficulty,
            String topic,
            String tag,
            String keyword,
            String type) {
        return ActivityFilter.of(
                Optional.ofNullable(hskLevel),
                Optional.ofNullable(ageBand),
                Optional.ofNullable(difficulty),
                Optional.ofNullable(topic),
                Optional.ofNullable(tag),
                Optional.ofNullable(keyword),
                Optional.ofNullable(type));
    }
}
