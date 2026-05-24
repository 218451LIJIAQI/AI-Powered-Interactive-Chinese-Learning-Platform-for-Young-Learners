package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.analytics.StudentAnalyticsResponse;
import com.example.aichinese.dto.dashboard.StudentDashboardResponse;
import com.example.aichinese.dto.learning.BadgeSummaryResponse;
import com.example.aichinese.dto.learning.DailyStreakResponse;
import com.example.aichinese.dto.learning.JoinLiveClassResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.PointsTransactionResponse;
import com.example.aichinese.dto.learning.QuizAttemptResultResponse;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.RewardSummaryResponse;
import com.example.aichinese.dto.learning.StudentLessonPlayerResponse;
import com.example.aichinese.dto.learning.StudentProgressSummaryResponse;
import com.example.aichinese.dto.learning.StudentQuizResponse;
import com.example.aichinese.dto.learning.StudentQuizSummaryResponse;
import com.example.aichinese.dto.learning.SubmitQuizAttemptRequest;
import com.example.aichinese.dto.learning.VideoProgressRequest;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.dto.learning.WrongAnswerResponse;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.LiveClassService;
import com.example.aichinese.service.RecordedLessonService;
import com.example.aichinese.service.AnalyticsService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.StudentLearningService;
import com.example.aichinese.service.VideoProgressService;

import jakarta.validation.Valid;

/**
 * Student persona REST surface — dashboard, lesson + quiz player,
 * progress / rewards / streaks, AI report-this-response, recorded
 * video playback, and live-class enrolment.
 *
 * <h2>Authorization</h2>
 * <p>Class-level {@code hasRole("STUDENT")} is enforced in
 * {@link com.example.aichinese.config.SecurityConfig}. Every
 * endpoint additionally re-checks ownership / entitlement through
 * {@link com.example.aichinese.service.StudentLearningAccessService}
 * (lesson / quiz access) and
 * {@link com.example.aichinese.service.LiveLearningAccessService}
 * (live class / recorded video access) so a forged path id cannot
 * cross-tenant into another student's content.</p>
 *
 * <h2>Input validation</h2>
 * <p>Free-text query parameters (search keyword, sort) are
 * {@code @Size}-capped at the controller boundary; JSON bodies use
 * {@code @Valid} so per-field constraints declared on the request
 * DTOs fire before the service is reached.</p>
 */
@Validated
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final StudentLearningService studentLearningService;
    private final LiveClassService liveClassService;
    private final RecordedLessonService recordedLessonService;
    private final VideoProgressService videoProgressService;

    public StudentController(
            DashboardService dashboardService,
            AnalyticsService analyticsService,
            StudentLearningService studentLearningService,
            LiveClassService liveClassService,
            RecordedLessonService recordedLessonService,
            VideoProgressService videoProgressService) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
        this.studentLearningService = studentLearningService;
        this.liveClassService = liveClassService;
        this.recordedLessonService = recordedLessonService;
        this.videoProgressService = videoProgressService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<StudentDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Student dashboard retrieved successfully.",
                dashboardService.getStudentDashboard(currentUser));
    }

    @GetMapping("/analytics")
    public ApiResponse<StudentAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Student analytics retrieved successfully.",
                analyticsService.getStudentAnalytics(currentUser));
    }

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<StudentLessonPlayerResponse> getLessonPlayer(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success(
                "Lesson player retrieved successfully.",
                studentLearningService.getLessonPlayer(currentUser, lessonId));
    }

    @GetMapping("/live-classes")
    public ApiResponse<PageResponse<LiveClassResponse>> getStudentLiveClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Live classes retrieved successfully.",
                liveClassService.getStudentLiveClasses(currentUser, page, size));
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
    public ApiResponse<PageResponse<RecordedLessonVideoResponse>> getRecordedVideos(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Recorded lesson videos retrieved successfully.",
                recordedLessonService.getStudentRecordedVideos(currentUser, lessonId, page, size));
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
        StoredFileDownload download = recordedLessonService.loadStudentRecordedVideoDownload(currentUser, videoId);
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(download);
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

    @GetMapping("/quizzes")
    public ApiResponse<PageResponse<StudentQuizSummaryResponse>> listQuizzes(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "lessonId", required = false) Long lessonId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Quizzes retrieved successfully.",
                studentLearningService.listQuizzes(currentUser, lessonId, page, size));
    }

    @GetMapping("/quizzes/{quizId:\\d+}")
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
    public ApiResponse<PageResponse<WrongAnswerResponse>> getWrongAnswers(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Wrong answers retrieved successfully.",
                studentLearningService.getWrongAnswers(currentUser, page, size));
    }

    @GetMapping("/quiz-attempts/{attemptId}/wrong-answers")
    public ApiResponse<List<WrongAnswerResponse>> getAttemptWrongAnswers(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success(
                "Attempt wrong answers retrieved successfully.",
                studentLearningService.getWrongAnswersForAttempt(currentUser, attemptId));
    }

    @GetMapping("/progress")
    public ApiResponse<StudentProgressSummaryResponse> getProgressSummary(
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
    public ApiResponse<PageResponse<BadgeSummaryResponse>> getBadges(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Badges retrieved successfully.",
                studentLearningService.getBadges(currentUser, page, size));
    }

    @GetMapping("/rewards/points-history")
    public ApiResponse<PageResponse<PointsTransactionResponse>> getPointsHistory(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Points history retrieved successfully.",
                studentLearningService.getPointsHistory(currentUser, page, size));
    }

    @GetMapping("/rewards/streak")
    public ApiResponse<DailyStreakResponse> getStreak(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Learning streak retrieved successfully.",
                studentLearningService.getStreak(currentUser));
    }
}
