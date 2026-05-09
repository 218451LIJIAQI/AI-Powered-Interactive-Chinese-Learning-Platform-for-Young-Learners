package com.example.aichinese.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.CreateCoursePackageRequest;
import com.example.aichinese.dto.content.CreateLessonActivityRequest;
import com.example.aichinese.dto.content.CreateLessonRequest;
import com.example.aichinese.dto.content.CreateModuleRequest;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.LessonActivityResponse;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.content.PackageItemRequest;
import com.example.aichinese.dto.content.UpdateCoursePackageRequest;
import com.example.aichinese.dto.content.UpdateLessonActivityRequest;
import com.example.aichinese.dto.content.UpdateLessonRequest;
import com.example.aichinese.dto.content.UpdateModuleRequest;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.dto.learning.CreateFlashcardRequest;
import com.example.aichinese.dto.learning.CreateListeningExerciseRequest;
import com.example.aichinese.dto.learning.CreateMatchingGameRequest;
import com.example.aichinese.dto.learning.CreateQuizQuestionRequest;
import com.example.aichinese.dto.learning.CreateQuizRequest;
import com.example.aichinese.dto.learning.CreateTracingExerciseRequest;
import com.example.aichinese.dto.learning.FlashcardOptionResponse;
import com.example.aichinese.dto.learning.ListeningExerciseResponse;
import com.example.aichinese.dto.learning.MatchingGameResponse;
import com.example.aichinese.dto.learning.TeacherQuizAttemptSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.dto.learning.TracingExerciseResponse;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationStatusResponse;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.LessonActivityService;
import com.example.aichinese.service.TeacherLearningActivityService;
import com.example.aichinese.service.TeacherLessonService;
import com.example.aichinese.service.TeacherManagementService;
import com.example.aichinese.service.TeacherModuleService;
import com.example.aichinese.service.TeacherPackageService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final DashboardService dashboardService;
    private final TeacherManagementService teacherManagementService;
    private final TeacherModuleService teacherModuleService;
    private final TeacherLessonService teacherLessonService;
    private final LessonActivityService lessonActivityService;
    private final TeacherPackageService teacherPackageService;
    private final TeacherLearningActivityService teacherLearningActivityService;

    public TeacherController(
            DashboardService dashboardService,
            TeacherManagementService teacherManagementService,
            TeacherModuleService teacherModuleService,
            TeacherLessonService teacherLessonService,
            LessonActivityService lessonActivityService,
            TeacherPackageService teacherPackageService,
            TeacherLearningActivityService teacherLearningActivityService) {
        this.dashboardService = dashboardService;
        this.teacherManagementService = teacherManagementService;
        this.teacherModuleService = teacherModuleService;
        this.teacherLessonService = teacherLessonService;
        this.lessonActivityService = lessonActivityService;
        this.teacherPackageService = teacherPackageService;
        this.teacherLearningActivityService = teacherLearningActivityService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<TeacherDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher dashboard retrieved successfully.",
                dashboardService.getTeacherDashboard(currentUser));
    }

    @GetMapping("/me")
    public ApiResponse<TeacherProfileResponse> getCurrentTeacherProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher profile retrieved successfully.",
                teacherManagementService.getCurrentTeacherProfile(currentUser));
    }

    @PutMapping("/me/profile")
    public ApiResponse<TeacherProfileResponse> updateCurrentTeacherProfile(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody UpdateTeacherProfileRequest request) {
        return ApiResponse.success(
                "Teacher profile updated successfully.",
                teacherManagementService.updateCurrentTeacherProfile(currentUser, request));
    }

    @GetMapping("/verification-status")
    public ApiResponse<TeacherVerificationStatusResponse> getVerificationStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher verification status retrieved successfully.",
                teacherManagementService.getTeacherVerificationStatus(currentUser));
    }

    @PostMapping(value = "/verification-documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TeacherVerificationStatusResponse> uploadVerificationDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "documentType", required = false) String documentType,
            @RequestParam(value = "description", required = false) String description) {
        return ApiResponse.success(
                "Verification document uploaded successfully.",
                teacherManagementService.uploadTeacherVerificationDocument(currentUser, file, documentType, description));
    }

    @PostMapping("/resubmit-verification")
    public ApiResponse<TeacherVerificationStatusResponse> resubmitVerification(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher verification resubmitted successfully.",
                teacherManagementService.resubmitVerification(currentUser));
    }

    @GetMapping("/verification-documents/{id}")
    public ResponseEntity<Resource> getTeacherOwnedDocument(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        Resource resource = teacherManagementService.getTeacherOwnedDocument(currentUser, id);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource)
                        .orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/modules")
    public ApiResponse<java.util.List<LearningModuleResponse>> getTeacherModules(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher modules retrieved successfully.",
                teacherModuleService.getTeacherModules(currentUser));
    }

    @PostMapping("/modules")
    public ApiResponse<LearningModuleResponse> createModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateModuleRequest request) {
        return ApiResponse.success(
                "Module created successfully.",
                teacherModuleService.createModule(currentUser, request));
    }

    @GetMapping("/modules/{id}")
    public ApiResponse<LearningModuleResponse> getTeacherModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher module retrieved successfully.",
                teacherModuleService.getTeacherModule(currentUser, id));
    }

    @PutMapping("/modules/{id}")
    public ApiResponse<LearningModuleResponse> updateModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateModuleRequest request) {
        return ApiResponse.success(
                "Module updated successfully.",
                teacherModuleService.updateModule(currentUser, id, request));
    }

    @PatchMapping("/modules/{id}/archive")
    public ApiResponse<LearningModuleResponse> archiveModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Module archived successfully.",
                teacherModuleService.archiveModule(currentUser, id));
    }

    @PostMapping("/modules/{id}/submit")
    public ApiResponse<LearningModuleResponse> submitModule(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Module submitted successfully.",
                teacherModuleService.submitModule(currentUser, id));
    }

    @GetMapping("/lessons")
    public ApiResponse<java.util.List<LessonResponse>> getTeacherLessons(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher lessons retrieved successfully.",
                teacherLessonService.getTeacherLessons(currentUser));
    }

    @PostMapping("/lessons")
    public ApiResponse<LessonResponse> createLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateLessonRequest request) {
        return ApiResponse.success(
                "Lesson created successfully.",
                teacherLessonService.createLesson(currentUser, request));
    }

    @GetMapping("/lessons/{id}")
    public ApiResponse<LessonResponse> getTeacherLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher lesson retrieved successfully.",
                teacherLessonService.getTeacherLesson(currentUser, id));
    }

    @PutMapping("/lessons/{id}")
    public ApiResponse<LessonResponse> updateLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLessonRequest request) {
        return ApiResponse.success(
                "Lesson updated successfully.",
                teacherLessonService.updateLesson(currentUser, id, request));
    }

    @PatchMapping("/lessons/{id}/archive")
    public ApiResponse<LessonResponse> archiveLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Lesson archived successfully.",
                teacherLessonService.archiveLesson(currentUser, id));
    }

    @PostMapping("/lessons/{id}/submit")
    public ApiResponse<LessonResponse> submitLesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Lesson submitted successfully.",
                teacherLessonService.submitLesson(currentUser, id));
    }

    @GetMapping("/lessons/{lessonId}/activities")
    public ApiResponse<java.util.List<LessonActivityResponse>> getLessonActivities(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Lesson activities retrieved successfully.",
                lessonActivityService.getLessonActivities(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/activities")
    public ApiResponse<LessonActivityResponse> createLessonActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateLessonActivityRequest request) {
        return ApiResponse.success(
                "Lesson activity created successfully.",
                lessonActivityService.createLessonActivity(currentUser, lessonId, request));
    }

    @PutMapping("/lesson-activities/{activityId}")
    public ApiResponse<LessonActivityResponse> updateLessonActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long activityId,
            @Valid @RequestBody UpdateLessonActivityRequest request) {
        return ApiResponse.success(
                "Lesson activity updated successfully.",
                lessonActivityService.updateLessonActivity(currentUser, activityId, request));
    }

    @PatchMapping("/lesson-activities/{activityId}/archive")
    public ApiResponse<LessonActivityResponse> archiveLessonActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long activityId) {
        return ApiResponse.success(
                "Lesson activity archived successfully.",
                lessonActivityService.archiveLessonActivity(currentUser, activityId));
    }

    @GetMapping("/packages")
    public ApiResponse<java.util.List<CoursePackageResponse>> getTeacherPackages(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher packages retrieved successfully.",
                teacherPackageService.getTeacherPackages(currentUser));
    }

    @PostMapping("/packages")
    public ApiResponse<CoursePackageResponse> createPackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateCoursePackageRequest request) {
        return ApiResponse.success(
                "Package created successfully.",
                teacherPackageService.createPackage(currentUser, request));
    }

    @GetMapping("/packages/{id}")
    public ApiResponse<CoursePackageResponse> getTeacherPackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher package retrieved successfully.",
                teacherPackageService.getTeacherPackage(currentUser, id));
    }

    @PutMapping("/packages/{id}")
    public ApiResponse<CoursePackageResponse> updatePackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateCoursePackageRequest request) {
        return ApiResponse.success(
                "Package updated successfully.",
                teacherPackageService.updatePackage(currentUser, id, request));
    }

    @PatchMapping("/packages/{id}/archive")
    public ApiResponse<CoursePackageResponse> archivePackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Package archived successfully.",
                teacherPackageService.archivePackage(currentUser, id));
    }

    @PostMapping("/packages/{id}/submit")
    public ApiResponse<CoursePackageResponse> submitPackage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Package submitted successfully.",
                teacherPackageService.submitPackage(currentUser, id));
    }

    @PostMapping("/packages/{id}/items")
    public ApiResponse<CoursePackageResponse> addPackageItem(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody PackageItemRequest request) {
        return ApiResponse.success(
                "Package item added successfully.",
                teacherPackageService.addPackageItem(currentUser, id, request));
    }

    @DeleteMapping("/packages/{packageId}/items/{itemId}")
    public ApiResponse<CoursePackageResponse> removePackageItem(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long packageId,
            @PathVariable Long itemId) {
        return ApiResponse.success(
                "Package item removed successfully.",
                teacherPackageService.removePackageItem(currentUser, packageId, itemId));
    }

    @GetMapping("/lessons/{lessonId}/flashcards")
    public ApiResponse<java.util.List<FlashcardOptionResponse>> getLessonFlashcards(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Flashcards retrieved successfully.",
                teacherLearningActivityService.getFlashcards(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/flashcards")
    public ApiResponse<FlashcardOptionResponse> createFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateFlashcardRequest request) {
        return ApiResponse.success(
                "Flashcard created successfully.",
                teacherLearningActivityService.createFlashcard(currentUser, lessonId, request));
    }

    @PutMapping("/flashcards/{flashcardId}")
    public ApiResponse<FlashcardOptionResponse> updateFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long flashcardId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateFlashcardRequest request) {
        return ApiResponse.success(
                "Flashcard updated successfully.",
                teacherLearningActivityService.updateFlashcard(currentUser, flashcardId, request));
    }

    @DeleteMapping("/flashcards/{flashcardId}")
    public ApiResponse<Void> deleteFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long flashcardId) {
        teacherLearningActivityService.deleteFlashcard(currentUser, flashcardId);
        return ApiResponse.success("Flashcard archived successfully.", null);
    }

    @GetMapping("/lessons/{lessonId}/quizzes")
    public ApiResponse<java.util.List<TeacherQuizResponse>> getLessonQuizzes(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Quizzes retrieved successfully.",
                teacherLearningActivityService.getQuizzes(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/quizzes")
    public ApiResponse<TeacherQuizResponse> createQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateQuizRequest request) {
        return ApiResponse.success(
                "Quiz created successfully.",
                teacherLearningActivityService.createQuiz(currentUser, lessonId, request));
    }

    @PutMapping("/quizzes/{quizId}")
    public ApiResponse<TeacherQuizResponse> updateQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateQuizRequest request) {
        return ApiResponse.success(
                "Quiz updated successfully.",
                teacherLearningActivityService.updateQuiz(currentUser, quizId, request));
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ApiResponse<Void> deleteQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        teacherLearningActivityService.deleteQuiz(currentUser, quizId);
        return ApiResponse.success("Quiz archived successfully.", null);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ApiResponse<TeacherQuizResponse> createQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @Valid @RequestBody CreateQuizQuestionRequest request) {
        return ApiResponse.success(
                "Quiz question created successfully.",
                teacherLearningActivityService.addQuizQuestion(currentUser, quizId, request));
    }

    @PutMapping("/quiz-questions/{questionId}")
    public ApiResponse<TeacherQuizResponse> updateQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long questionId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateQuizQuestionRequest request) {
        return ApiResponse.success(
                "Quiz question updated successfully.",
                teacherLearningActivityService.updateQuizQuestion(currentUser, questionId, request));
    }

    @DeleteMapping("/quiz-questions/{questionId}")
    public ApiResponse<TeacherQuizResponse> deleteQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long questionId) {
        return ApiResponse.success(
                "Quiz question deleted successfully.",
                teacherLearningActivityService.deleteQuizQuestion(currentUser, questionId));
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    public ApiResponse<java.util.List<TeacherQuizAttemptSummaryResponse>> getQuizAttempts(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success(
                "Quiz attempts retrieved successfully.",
                teacherLearningActivityService.getQuizAttempts(currentUser, quizId));
    }

    @GetMapping("/lessons/{lessonId}/matching-games")
    public ApiResponse<java.util.List<MatchingGameResponse>> getMatchingGames(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Matching games retrieved successfully.",
                teacherLearningActivityService.getMatchingGames(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/matching-games")
    public ApiResponse<MatchingGameResponse> createMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateMatchingGameRequest request) {
        return ApiResponse.success(
                "Matching game created successfully.",
                teacherLearningActivityService.createMatchingGame(currentUser, lessonId, request));
    }

    @PutMapping("/matching-games/{gameId}")
    public ApiResponse<MatchingGameResponse> updateMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long gameId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateMatchingGameRequest request) {
        return ApiResponse.success(
                "Matching game updated successfully.",
                teacherLearningActivityService.updateMatchingGame(currentUser, gameId, request));
    }

    @DeleteMapping("/matching-games/{gameId}")
    public ApiResponse<Void> deleteMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long gameId) {
        teacherLearningActivityService.deleteMatchingGame(currentUser, gameId);
        return ApiResponse.success("Matching game archived successfully.", null);
    }

    @GetMapping("/lessons/{lessonId}/listening-exercises")
    public ApiResponse<java.util.List<ListeningExerciseResponse>> getListeningExercises(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Listening exercises retrieved successfully.",
                teacherLearningActivityService.getListeningExercises(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/listening-exercises")
    public ApiResponse<ListeningExerciseResponse> createListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateListeningExerciseRequest request) {
        return ApiResponse.success(
                "Listening exercise created successfully.",
                teacherLearningActivityService.createListeningExercise(currentUser, lessonId, request));
    }

    @PutMapping("/listening-exercises/{exerciseId}")
    public ApiResponse<ListeningExerciseResponse> updateListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateListeningExerciseRequest request) {
        return ApiResponse.success(
                "Listening exercise updated successfully.",
                teacherLearningActivityService.updateListeningExercise(currentUser, exerciseId, request));
    }

    @DeleteMapping("/listening-exercises/{exerciseId}")
    public ApiResponse<Void> deleteListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId) {
        teacherLearningActivityService.deleteListeningExercise(currentUser, exerciseId);
        return ApiResponse.success("Listening exercise archived successfully.", null);
    }

    @GetMapping("/lessons/{lessonId}/tracing-exercises")
    public ApiResponse<java.util.List<TracingExerciseResponse>> getTracingExercises(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Tracing exercises retrieved successfully.",
                teacherLearningActivityService.getTracingExercises(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/tracing-exercises")
    public ApiResponse<TracingExerciseResponse> createTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateTracingExerciseRequest request) {
        return ApiResponse.success(
                "Tracing exercise created successfully.",
                teacherLearningActivityService.createTracingExercise(currentUser, lessonId, request));
    }

    @PutMapping("/tracing-exercises/{exerciseId}")
    public ApiResponse<TracingExerciseResponse> updateTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateTracingExerciseRequest request) {
        return ApiResponse.success(
                "Tracing exercise updated successfully.",
                teacherLearningActivityService.updateTracingExercise(currentUser, exerciseId, request));
    }

    @DeleteMapping("/tracing-exercises/{exerciseId}")
    public ApiResponse<Void> deleteTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId) {
        teacherLearningActivityService.deleteTracingExercise(currentUser, exerciseId);
        return ApiResponse.success("Tracing exercise archived successfully.", null);
    }
}
