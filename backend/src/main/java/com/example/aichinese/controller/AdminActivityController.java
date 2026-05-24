package com.example.aichinese.controller;

import java.util.Optional;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.activity.AdminActivityStatusUpdateRequest;
import com.example.aichinese.dto.admin.activity.AdminActivitySummaryResponse;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminFlashcardResponse;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseResponse;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameResponse;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminTracingExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminTracingExerciseResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AdminActivityService;
import com.example.aichinese.service.AdminActivityService.ActivityFilter;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

/**
 * Admin CRUD endpoints for the six platform-managed learning activity
 * types. Mounted under {@code /api/v1/admin/activities}; secured by
 * {@code hasRole("ADMIN")} via the global security configuration.
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/activities")
public class AdminActivityController {

    private final AdminActivityService adminActivityService;

    public AdminActivityController(AdminActivityService adminActivityService) {
        this.adminActivityService = adminActivityService;
    }

    // ===================================================================
    // Cross-type unified feed
    // ===================================================================

    @GetMapping
    public ApiResponse<PageResponse<AdminActivitySummaryResponse>> listAll(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @Size(max = 60, message = "Type must not exceed 60 characters.")
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, type);
        return ApiResponse.success(
                "Activities retrieved successfully.",
                adminActivityService.listAllActivities(filter, page, size));
    }

    // ===================================================================
    // Flashcards
    // ===================================================================

    @GetMapping("/flashcards")
    public ApiResponse<PageResponse<AdminFlashcardResponse>> listFlashcards(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Flashcards retrieved successfully.",
                adminActivityService.listFlashcards(filter, page, size, sort));
    }

    @GetMapping("/flashcards/{id}")
    public ApiResponse<AdminFlashcardResponse> getFlashcard(@PathVariable Long id) {
        return ApiResponse.success(
                "Flashcard retrieved successfully.",
                adminActivityService.getFlashcard(id));
    }

    @PostMapping("/flashcards")
    public ApiResponse<AdminFlashcardResponse> createFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminFlashcardRequest request) {
        return ApiResponse.success(
                "Flashcard created successfully.",
                adminActivityService.createFlashcard(currentUser, request));
    }

    @PutMapping("/flashcards/{id}")
    public ApiResponse<AdminFlashcardResponse> updateFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminFlashcardRequest request) {
        return ApiResponse.success(
                "Flashcard updated successfully.",
                adminActivityService.updateFlashcard(currentUser, id, request));
    }

    @PatchMapping("/flashcards/{id}/status")
    public ApiResponse<AdminFlashcardResponse> updateFlashcardStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Flashcard status updated successfully.",
                adminActivityService.updateFlashcardStatus(currentUser, id, request));
    }

    @DeleteMapping("/flashcards/{id}")
    public ApiResponse<Void> deleteFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteFlashcard(currentUser, id);
        return ApiResponse.success("Flashcard deleted successfully.", null);
    }

    // ===================================================================
    // Matching games
    // ===================================================================

    @GetMapping("/matching-games")
    public ApiResponse<PageResponse<AdminMatchingGameResponse>> listMatchingGames(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Matching games retrieved successfully.",
                adminActivityService.listMatchingGames(filter, page, size, sort));
    }

    @GetMapping("/matching-games/{id}")
    public ApiResponse<AdminMatchingGameResponse> getMatchingGame(@PathVariable Long id) {
        return ApiResponse.success(
                "Matching game retrieved successfully.",
                adminActivityService.getMatchingGame(id));
    }

    @PostMapping("/matching-games")
    public ApiResponse<AdminMatchingGameResponse> createMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminMatchingGameRequest request) {
        return ApiResponse.success(
                "Matching game created successfully.",
                adminActivityService.createMatchingGame(currentUser, request));
    }

    @PutMapping("/matching-games/{id}")
    public ApiResponse<AdminMatchingGameResponse> updateMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminMatchingGameRequest request) {
        return ApiResponse.success(
                "Matching game updated successfully.",
                adminActivityService.updateMatchingGame(currentUser, id, request));
    }

    @PatchMapping("/matching-games/{id}/status")
    public ApiResponse<AdminMatchingGameResponse> updateMatchingGameStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Matching game status updated successfully.",
                adminActivityService.updateMatchingGameStatus(currentUser, id, request));
    }

    @DeleteMapping("/matching-games/{id}")
    public ApiResponse<Void> deleteMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteMatchingGame(currentUser, id);
        return ApiResponse.success("Matching game deleted successfully.", null);
    }

    // ===================================================================
    // Listening exercises
    // ===================================================================

    @GetMapping("/listening-exercises")
    public ApiResponse<PageResponse<AdminListeningExerciseResponse>> listListeningExercises(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Listening exercises retrieved successfully.",
                adminActivityService.listListeningExercises(filter, page, size, sort));
    }

    @GetMapping("/listening-exercises/{id}")
    public ApiResponse<AdminListeningExerciseResponse> getListeningExercise(@PathVariable Long id) {
        return ApiResponse.success(
                "Listening exercise retrieved successfully.",
                adminActivityService.getListeningExercise(id));
    }

    @PostMapping("/listening-exercises")
    public ApiResponse<AdminListeningExerciseResponse> createListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminListeningExerciseRequest request) {
        return ApiResponse.success(
                "Listening exercise created successfully.",
                adminActivityService.createListeningExercise(currentUser, request));
    }

    @PutMapping("/listening-exercises/{id}")
    public ApiResponse<AdminListeningExerciseResponse> updateListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminListeningExerciseRequest request) {
        return ApiResponse.success(
                "Listening exercise updated successfully.",
                adminActivityService.updateListeningExercise(currentUser, id, request));
    }

    @PatchMapping("/listening-exercises/{id}/status")
    public ApiResponse<AdminListeningExerciseResponse> updateListeningExerciseStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Listening exercise status updated successfully.",
                adminActivityService.updateListeningExerciseStatus(currentUser, id, request));
    }

    @DeleteMapping("/listening-exercises/{id}")
    public ApiResponse<Void> deleteListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteListeningExercise(currentUser, id);
        return ApiResponse.success("Listening exercise deleted successfully.", null);
    }

    // ===================================================================
    // Tracing exercises
    // ===================================================================

    @GetMapping("/tracing-exercises")
    public ApiResponse<PageResponse<AdminTracingExerciseResponse>> listTracingExercises(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Tracing exercises retrieved successfully.",
                adminActivityService.listTracingExercises(filter, page, size, sort));
    }

    @GetMapping("/tracing-exercises/{id}")
    public ApiResponse<AdminTracingExerciseResponse> getTracingExercise(@PathVariable Long id) {
        return ApiResponse.success(
                "Tracing exercise retrieved successfully.",
                adminActivityService.getTracingExercise(id));
    }

    @PostMapping("/tracing-exercises")
    public ApiResponse<AdminTracingExerciseResponse> createTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminTracingExerciseRequest request) {
        return ApiResponse.success(
                "Tracing exercise created successfully.",
                adminActivityService.createTracingExercise(currentUser, request));
    }

    @PutMapping("/tracing-exercises/{id}")
    public ApiResponse<AdminTracingExerciseResponse> updateTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminTracingExerciseRequest request) {
        return ApiResponse.success(
                "Tracing exercise updated successfully.",
                adminActivityService.updateTracingExercise(currentUser, id, request));
    }

    @PatchMapping("/tracing-exercises/{id}/status")
    public ApiResponse<AdminTracingExerciseResponse> updateTracingExerciseStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Tracing exercise status updated successfully.",
                adminActivityService.updateTracingExerciseStatus(currentUser, id, request));
    }

    @DeleteMapping("/tracing-exercises/{id}")
    public ApiResponse<Void> deleteTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteTracingExercise(currentUser, id);
        return ApiResponse.success("Tracing exercise deleted successfully.", null);
    }

    // ===================================================================
    // Drag-drop activities
    // ===================================================================

    @GetMapping("/drag-drop-activities")
    public ApiResponse<PageResponse<AdminDragDropActivityResponse>> listDragDropActivities(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Drag-drop activities retrieved successfully.",
                adminActivityService.listDragDropActivities(filter, page, size, sort));
    }

    @GetMapping("/drag-drop-activities/{id}")
    public ApiResponse<AdminDragDropActivityResponse> getDragDropActivity(@PathVariable Long id) {
        return ApiResponse.success(
                "Drag-drop activity retrieved successfully.",
                adminActivityService.getDragDropActivity(id));
    }

    @PostMapping("/drag-drop-activities")
    public ApiResponse<AdminDragDropActivityResponse> createDragDropActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminDragDropActivityRequest request) {
        return ApiResponse.success(
                "Drag-drop activity created successfully.",
                adminActivityService.createDragDropActivity(currentUser, request));
    }

    @PutMapping("/drag-drop-activities/{id}")
    public ApiResponse<AdminDragDropActivityResponse> updateDragDropActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminDragDropActivityRequest request) {
        return ApiResponse.success(
                "Drag-drop activity updated successfully.",
                adminActivityService.updateDragDropActivity(currentUser, id, request));
    }

    @PatchMapping("/drag-drop-activities/{id}/status")
    public ApiResponse<AdminDragDropActivityResponse> updateDragDropActivityStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Drag-drop activity status updated successfully.",
                adminActivityService.updateDragDropActivityStatus(currentUser, id, request));
    }

    @DeleteMapping("/drag-drop-activities/{id}")
    public ApiResponse<Void> deleteDragDropActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteDragDropActivity(currentUser, id);
        return ApiResponse.success("Drag-drop activity deleted successfully.", null);
    }

    // ===================================================================
    // Story activities
    // ===================================================================

    @GetMapping("/story-activities")
    public ApiResponse<PageResponse<AdminStoryActivityResponse>> listStoryActivities(
            @Size(max = 40, message = "Status must not exceed 40 characters.")
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "hskLevel", required = false) Integer hskLevel,
            @Size(max = 40, message = "Age band must not exceed 40 characters.")
            @RequestParam(value = "ageBand", required = false) String ageBand,
            @Size(max = 40, message = "Difficulty must not exceed 40 characters.")
            @RequestParam(value = "difficulty", required = false) String difficulty,
            @Size(max = 200, message = "Topic must not exceed 200 characters.")
            @RequestParam(value = "topic", required = false) String topic,
            @Size(max = 100, message = "Tag must not exceed 100 characters.")
            @RequestParam(value = "tag", required = false) String tag,
            @RequestParam(value = "createdByUserId", required = false) Long createdByUserId,
            @Size(max = 200, message = "Keyword must not exceed 200 characters.")
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            @Size(max = 40, message = "Sort must not exceed 40 characters.")
            @RequestParam(value = "sort", required = false) String sort) {
        ActivityFilter filter = buildFilter(status, hskLevel, ageBand, difficulty,
                topic, tag, createdByUserId, keyword, null);
        return ApiResponse.success(
                "Story activities retrieved successfully.",
                adminActivityService.listStoryActivities(filter, page, size, sort));
    }

    @GetMapping("/story-activities/{id}")
    public ApiResponse<AdminStoryActivityResponse> getStoryActivity(@PathVariable Long id) {
        return ApiResponse.success(
                "Story activity retrieved successfully.",
                adminActivityService.getStoryActivity(id));
    }

    @PostMapping("/story-activities")
    public ApiResponse<AdminStoryActivityResponse> createStoryActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody AdminStoryActivityRequest request) {
        return ApiResponse.success(
                "Story activity created successfully.",
                adminActivityService.createStoryActivity(currentUser, request));
    }

    @PutMapping("/story-activities/{id}")
    public ApiResponse<AdminStoryActivityResponse> updateStoryActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminStoryActivityRequest request) {
        return ApiResponse.success(
                "Story activity updated successfully.",
                adminActivityService.updateStoryActivity(currentUser, id, request));
    }

    @PatchMapping("/story-activities/{id}/status")
    public ApiResponse<AdminStoryActivityResponse> updateStoryActivityStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody AdminActivityStatusUpdateRequest request) {
        return ApiResponse.success(
                "Story activity status updated successfully.",
                adminActivityService.updateStoryActivityStatus(currentUser, id, request));
    }

    @DeleteMapping("/story-activities/{id}")
    public ApiResponse<Void> deleteStoryActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        adminActivityService.deleteStoryActivity(currentUser, id);
        return ApiResponse.success("Story activity deleted successfully.", null);
    }

    // ===================================================================
    // Shared filter assembly
    // ===================================================================

    private ActivityFilter buildFilter(
            String status,
            Integer hskLevel,
            String ageBand,
            String difficulty,
            String topic,
            String tag,
            Long createdByUserId,
            String keyword,
            String type) {
        return ActivityFilter.of(
                Optional.ofNullable(status),
                Optional.ofNullable(hskLevel),
                Optional.ofNullable(ageBand),
                Optional.ofNullable(difficulty),
                Optional.ofNullable(topic),
                Optional.ofNullable(tag),
                Optional.ofNullable(createdByUserId),
                Optional.ofNullable(keyword),
                Optional.ofNullable(type));
    }
}
