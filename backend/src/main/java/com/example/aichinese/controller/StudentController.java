package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
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
import com.example.aichinese.dto.learning.JoinLiveClassResponse;
import com.example.aichinese.dto.learning.ListeningExercisePlayResponse;
import com.example.aichinese.dto.learning.ListeningExerciseSubmissionResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.MatchingGamePlayResponse;
import com.example.aichinese.dto.learning.MatchingGameSubmissionResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentScheduleResponse;
import com.example.aichinese.dto.learning.StudentLessonActivitySummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.SubmitListeningExerciseRequest;
import com.example.aichinese.dto.learning.SubmitMatchingGameRequest;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.TracingExerciseCompletionResponse;
import com.example.aichinese.dto.learning.TracingExercisePlayResponse;
import com.example.aichinese.dto.learning.VideoProgressRequest;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.LiveClassService;
import com.example.aichinese.service.RecordedLessonService;
import com.example.aichinese.service.ScheduleService;
import com.example.aichinese.service.StudentLearningService;
import com.example.aichinese.service.VideoProgressService;

import jakarta.validation.Valid;

@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final DashboardService dashboardService;
    private final StudentLearningService studentLearningService;
    private final LiveClassService liveClassService;
    private final RecordedLessonService recordedLessonService;
    private final VideoProgressService videoProgressService;
    private final ScheduleService scheduleService;

    public StudentController(
            DashboardService dashboardService,
            StudentLearningService studentLearningService,
            LiveClassService liveClassService,
            RecordedLessonService recordedLessonService,
            VideoProgressService videoProgressService,
            ScheduleService scheduleService) {
        this.dashboardService = dashboardService;
        this.studentLearningService = studentLearningService;
        this.liveClassService = liveClassService;
        this.recordedLessonService = recordedLessonService;
        this.videoProgressService = videoProgressService;
        this.scheduleService = scheduleService;
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

    @GetMapping("/schedule")
    public ApiResponse<StudentScheduleResponse> getStudentSchedule(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Student schedule retrieved successfully.",
                scheduleService.getStudentSchedule(currentUser));
    }

    @GetMapping("/live-classes")
    public ApiResponse<List<LiveClassResponse>> getStudentLiveClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Live classes retrieved successfully.",
                liveClassService.getStudentLiveClasses(currentUser));
    }

    @GetMapping("/live-classes/{id}")
    public ApiResponse<LiveClassResponse> getStudentLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Live class retrieved successfully.",
                liveClassService.getStudentLiveClass(currentUser, id));
    }

    @PostMapping("/live-classes/{id}/enrol")
    public ApiResponse<LiveClassResponse> enrolLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Live class enrolment completed successfully.",
                liveClassService.enrol(currentUser, id));
    }

    @PostMapping("/live-classes/{id}/cancel-enrolment")
    public ApiResponse<LiveClassResponse> cancelLiveClassEnrolment(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Live class enrolment cancelled successfully.",
                liveClassService.cancelEnrolment(currentUser, id));
    }

    @GetMapping("/live-classes/{id}/join")
    public ApiResponse<JoinLiveClassResponse> joinLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Live class join link retrieved successfully.",
                liveClassService.join(currentUser, id));
    }

    @GetMapping("/lessons/{lessonId}/recorded-videos")
    public ApiResponse<List<RecordedLessonVideoResponse>> getRecordedVideos(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Recorded lesson videos retrieved successfully.",
                recordedLessonService.getStudentRecordedVideos(currentUser, lessonId));
    }

    @GetMapping("/lessons/{lessonId}/recorded-videos/{videoId}")
    public ApiResponse<RecordedLessonVideoResponse> getRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId) {
        return ApiResponse.success(
                "Recorded lesson video retrieved successfully.",
                recordedLessonService.getStudentRecordedVideo(currentUser, lessonId, videoId));
    }

    @GetMapping("/recorded-videos/{videoId}/stream")
    public ResponseEntity<Resource> streamRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long videoId) {
        Resource resource = recordedLessonService.loadRecordedVideoResource(currentUser, videoId);
        return ResponseEntity.ok()
                .contentType(MediaTypeFactory.getMediaType(resource).orElse(MediaType.APPLICATION_OCTET_STREAM))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }

    @GetMapping("/video-progress/{videoId}")
    public ApiResponse<VideoProgressResponse> getVideoProgress(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long videoId) {
        return ApiResponse.success(
                "Video progress retrieved successfully.",
                videoProgressService.getProgress(currentUser, videoId));
    }

    @PostMapping("/video-progress/{videoId}")
    public ApiResponse<VideoProgressResponse> createVideoProgress(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long videoId,
            @Valid @RequestBody VideoProgressRequest request) {
        return ApiResponse.success(
                "Video progress saved successfully.",
                videoProgressService.upsertProgress(currentUser, videoId, request));
    }

    @PatchMapping("/video-progress/{videoId}")
    public ApiResponse<VideoProgressResponse> updateVideoProgress(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long videoId,
            @Valid @RequestBody VideoProgressRequest request) {
        return ApiResponse.success(
                "Video progress updated successfully.",
                videoProgressService.upsertProgress(currentUser, videoId, request));
    }

    @PostMapping("/video-progress/{videoId}/complete")
    public ApiResponse<VideoProgressResponse> completeVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long videoId) {
        return ApiResponse.success(
                "Video marked as completed successfully.",
                videoProgressService.completeVideo(currentUser, videoId));
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
