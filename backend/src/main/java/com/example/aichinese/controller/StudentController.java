package com.example.aichinese.controller;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.dashboard.StudentDashboardResponse;
import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.CompleteTracingExerciseRequest;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.FlashcardOptionResponse;
import com.example.aichinese.dto.learning.FlashcardPracticeResponse;
import com.example.aichinese.dto.learning.ListeningExercisePlayResponse;
import com.example.aichinese.dto.learning.ListeningExerciseSubmissionResponse;
import com.example.aichinese.dto.learning.MatchingGamePlayResponse;
import com.example.aichinese.dto.learning.MatchingGameSubmissionResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonActivitySummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.SubmitListeningExerciseRequest;
import com.example.aichinese.dto.learning.SubmitMatchingGameRequest;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.TracingExerciseCompletionResponse;
import com.example.aichinese.dto.learning.TracingExercisePlayResponse;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.StudentLearningService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final DashboardService dashboardService;
    private final StudentLearningService studentLearningService;

    public StudentController(DashboardService dashboardService, StudentLearningService studentLearningService) {
        this.dashboardService = dashboardService;
        this.studentLearningService = studentLearningService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Student dashboard retrieved successfully.",
                dashboardService.getStudentDashboard(currentUser));
    }

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<StudentLessonPlayerResponse> getLessonPlayer(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Lesson player retrieved successfully.",
                studentLearningService.getLessonPlayer(currentUser, lessonId));
    }

    @GetMapping("/lessons/{lessonId}/activities")
    public ApiResponse<List<StudentLessonActivitySummaryResponse>> getLessonActivities(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Lesson activities retrieved successfully.",
                studentLearningService.getLessonActivities(currentUser, lessonId));
    }

    @GetMapping("/lessons/{lessonId}/flashcards")
    public ApiResponse<List<FlashcardOptionResponse>> getFlashcards(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Flashcards retrieved successfully.",
                studentLearningService.getFlashcards(currentUser, lessonId));
    }

    @PostMapping("/flashcards/{flashcardId}/practice")
    public ApiResponse<FlashcardPracticeResponse> practiceFlashcard(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long flashcardId) {
        return ApiResponse.success(
                "Flashcard practice recorded successfully.",
                studentLearningService.practiceFlashcard(currentUser, flashcardId));
    }

    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<StudentQuizResponse> getQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success(
                "Quiz retrieved successfully.",
                studentLearningService.getQuiz(currentUser, quizId));
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ApiResponse<QuizAttemptResultResponse> submitQuizAttempt(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @Valid @RequestBody SubmitQuizAttemptRequest request) {
        return ApiResponse.success(
                "Quiz submitted successfully.",
                studentLearningService.submitQuizAttempt(currentUser, quizId, request));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ApiResponse<QuizAttemptResultResponse> getQuizAttempt(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success(
                "Quiz attempt retrieved successfully.",
                studentLearningService.getQuizAttempt(currentUser, attemptId));
    }

    @GetMapping("/wrong-answers")
    public ApiResponse<List<WrongAnswerResponse>> getWrongAnswers(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Wrong answers retrieved successfully.",
                studentLearningService.getWrongAnswers(currentUser));
    }

    @GetMapping("/quiz-attempts/{attemptId}/wrong-answers")
    public ApiResponse<List<WrongAnswerResponse>> getAttemptWrongAnswers(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success(
                "Attempt wrong answers retrieved successfully.",
                studentLearningService.getWrongAnswersForAttempt(currentUser, attemptId));
    }

    @GetMapping("/matching-games/{gameId}")
    public ApiResponse<MatchingGamePlayResponse> getMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long gameId) {
        return ApiResponse.success(
                "Matching game retrieved successfully.",
                studentLearningService.getMatchingGame(currentUser, gameId));
    }

    @PostMapping("/matching-games/{gameId}/submit")
    public ApiResponse<MatchingGameSubmissionResponse> submitMatchingGame(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long gameId,
            @Valid @RequestBody SubmitMatchingGameRequest request) {
        return ApiResponse.success(
                "Matching game submitted successfully.",
                studentLearningService.submitMatchingGame(currentUser, gameId, request));
    }

    @GetMapping("/listening-exercises/{exerciseId}")
    public ApiResponse<ListeningExercisePlayResponse> getListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId) {
        return ApiResponse.success(
                "Listening exercise retrieved successfully.",
                studentLearningService.getListeningExercise(currentUser, exerciseId));
    }

    @PostMapping("/listening-exercises/{exerciseId}/submit")
    public ApiResponse<ListeningExerciseSubmissionResponse> submitListeningExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId,
            @Valid @RequestBody SubmitListeningExerciseRequest request) {
        return ApiResponse.success(
                "Listening exercise submitted successfully.",
                studentLearningService.submitListeningExercise(currentUser, exerciseId, request));
    }

    @GetMapping("/tracing-exercises/{exerciseId}")
    public ApiResponse<TracingExercisePlayResponse> getTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId) {
        return ApiResponse.success(
                "Tracing exercise retrieved successfully.",
                studentLearningService.getTracingExercise(currentUser, exerciseId));
    }

    @PostMapping("/tracing-exercises/{exerciseId}/complete")
    public ApiResponse<TracingExerciseCompletionResponse> completeTracingExercise(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long exerciseId,
            @Valid @RequestBody(required = false) CompleteTracingExerciseRequest request) {
        return ApiResponse.success(
                "Tracing exercise completion recorded successfully.",
                studentLearningService.completeTracingExercise(
                        currentUser,
                        exerciseId,
                        request == null ? new CompleteTracingExerciseRequest(null) : request));
    }

    @GetMapping("/progress")
    public ApiResponse<StudentProgressSummaryResponse> getProgressSummary(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Progress summary retrieved successfully.",
                studentLearningService.getProgressSummary(currentUser));
    }

    @GetMapping("/progress/summary")
    public ApiResponse<StudentProgressSummaryResponse> getProgressSummaryAlias(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Progress summary retrieved successfully.",
                studentLearningService.getProgressSummary(currentUser));
    }

    @GetMapping("/rewards")
    public ApiResponse<RewardSummaryResponse> getRewards(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Rewards retrieved successfully.",
                studentLearningService.getRewards(currentUser));
    }

    @GetMapping("/rewards/badges")
    public ApiResponse<List<BadgeSummaryResponse>> getBadges(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Badges retrieved successfully.",
                studentLearningService.getBadges(currentUser));
    }

    @GetMapping("/rewards/points-history")
    public ApiResponse<List<PointsTransactionResponse>> getPointsHistory(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Points history retrieved successfully.",
                studentLearningService.getPointsHistory(currentUser));
    }

    @GetMapping("/rewards/streak")
    public ApiResponse<DailyStreakResponse> getStreak(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Learning streak retrieved successfully.",
                studentLearningService.getStreak(currentUser));
    }
}
