package com.example.aichinese.controller;

import java.util.List;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.ClassViews;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.dto.classes.LiveSessionViews;
import com.example.aichinese.dto.learning.QuizSubmissionAnswerRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassCatalogueService;
import com.example.aichinese.service.classes.ClassLiveSessionService;
import com.example.aichinese.service.classes.ClassQuizService;
import com.example.aichinese.service.classes.StudentClassActivityService;
import com.example.aichinese.service.classes.StudentClassService;

import jakarta.validation.Valid;

/** The student's classes: My classes, Find classes, class and lesson pages, activities, lesson quizzes, live sessions. */
@RestController
@RequestMapping("/api/v1/students/classes")
public class StudentClassController {

    private final StudentClassService classes;
    private final StudentClassActivityService activities;
    private final ClassQuizService quizzes;
    private final ClassLiveSessionService liveSessions;

    public StudentClassController(
            StudentClassService classes,
            StudentClassActivityService activities,
            ClassQuizService quizzes,
            ClassLiveSessionService liveSessions) {
        this.classes = classes;
        this.activities = activities;
        this.quizzes = quizzes;
        this.liveSessions = liveSessions;
    }

    @GetMapping
    public ApiResponse<ClassViews.MyClasses> myClasses(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Your classes retrieved successfully.", classes.myClasses(currentUser));
    }

    @GetMapping("/catalogue")
    public ApiResponse<PageResponse<ClassViews.ClassCard>> findClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "topicId", required = false) Long topicId,
            @RequestParam(value = "levelId", required = false) Long levelId,
            @RequestParam(value = "learningType", required = false) String learningType,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.success("Classes retrieved successfully.", classes.findClasses(currentUser,
                new ClassCatalogueService.Query(keyword, topicId, levelId, learningType, sort), page, size));
    }

    @GetMapping("/{classId:\\d+}")
    public ApiResponse<ClassViews.StudentClass> classPage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", classes.classPage(currentUser, classId));
    }

    @PostMapping("/{classId:\\d+}/join")
    public ApiResponse<ClassViews.StudentClass> join(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("You joined the class.", classes.join(currentUser, classId));
    }

    @GetMapping("/{classId:\\d+}/quiz-results")
    public ApiResponse<ClassViews.QuizResults> quizResults(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Quiz results retrieved successfully.", classes.quizResults(currentUser, classId));
    }

    @GetMapping("/{classId:\\d+}/feedback")
    public ApiResponse<List<ClassViews.FeedbackItem>> feedback(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Feedback retrieved successfully.", classes.feedback(currentUser, classId));
    }

    // ----- lessons -----

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonViews.StudentLesson> lesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", classes.lesson(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/video/watched")
    public ApiResponse<LessonViews.StudentLesson> markVideoWatched(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Video marked as watched.", classes.markVideoWatched(currentUser, lessonId));
    }

    @GetMapping("/lessons/{lessonId}/video/{videoId}/stream")
    public ResponseEntity<Resource> streamVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(classes.streamVideo(currentUser, lessonId, videoId));
    }

    // ----- activities -----

    @GetMapping("/activities/{type}/{activityId}")
    public ApiResponse<LessonViews.ActivityPlay> activity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long activityId) {
        return ApiResponse.success("Activity retrieved successfully.", activities.play(currentUser, type, activityId));
    }

    @PostMapping("/activities/{type}/{activityId}/complete")
    public ApiResponse<LessonViews.ActivityOutcome> completeActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable String type,
            @PathVariable Long activityId) {
        return ApiResponse.success("Activity completed.", activities.complete(currentUser, type, activityId));
    }

    @PostMapping("/activities/matching/{activityId}/check")
    public ApiResponse<LessonViews.ActivityOutcome> checkMatching(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long activityId,
            @Valid @RequestBody ClassRequests.MatchingAnswer request) {
        return ApiResponse.success("Pairs checked.", activities.checkMatching(currentUser, activityId, request));
    }

    @PostMapping("/activities/matching/{activityId}/hint")
    public ApiResponse<LessonViews.MatchHint> matchingHint(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long activityId,
            @Valid @RequestBody ClassRequests.MatchingHint request) {
        return ApiResponse.success("Hint retrieved successfully.", activities.matchingHint(currentUser, activityId, request.wordId()));
    }

    // ----- lesson quizzes -----

    @GetMapping("/quizzes/{quizId}")
    public ApiResponse<LessonViews.QuizIntro> quiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success("Quiz retrieved successfully.", quizzes.intro(currentUser, quizId));
    }

    @PostMapping("/quizzes/{quizId}/attempts")
    public ApiResponse<LessonViews.QuizSession> startQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        return ApiResponse.success("Quiz started.", quizzes.start(currentUser, quizId));
    }

    @PutMapping("/quiz-attempts/{attemptId}/answers")
    public ApiResponse<LessonViews.SavedAnswer> saveAnswer(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId,
            @Valid @RequestBody QuizSubmissionAnswerRequest request) {
        return ApiResponse.success("Answer saved.", quizzes.saveAnswer(currentUser, attemptId, request));
    }

    @PostMapping("/quiz-attempts/{attemptId}/hint")
    public ApiResponse<LessonViews.QuizHint> hint(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId,
            @Valid @RequestBody ClassRequests.HintRequest request) {
        return ApiResponse.success("Hint retrieved successfully.", quizzes.hint(currentUser, attemptId, request.questionId()));
    }

    @PostMapping("/quiz-attempts/{attemptId}/submit")
    public ApiResponse<LessonViews.QuizResult> submitQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success("Quiz submitted.", quizzes.submit(currentUser, attemptId));
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ApiResponse<LessonViews.QuizResult> quizResult(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success("Quiz result retrieved successfully.", quizzes.result(currentUser, attemptId));
    }

    // ----- live sessions -----

    @GetMapping("/live-sessions")
    public ApiResponse<LiveSessionViews.StudentSessions> liveSessions(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Live sessions retrieved successfully.", liveSessions.studentSessions(currentUser));
    }

    @PostMapping("/live-sessions/{sessionId}/book")
    public ApiResponse<LiveSessionViews.StudentSession> book(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success("Seat booked.", liveSessions.book(currentUser, sessionId));
    }

    @PostMapping("/live-sessions/{sessionId}/cancel-booking")
    public ApiResponse<LiveSessionViews.StudentSession> cancelBooking(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success("Booking cancelled.", liveSessions.cancelBooking(currentUser, sessionId));
    }

    @PostMapping("/live-sessions/{sessionId}/join")
    public ApiResponse<LiveSessionViews.JoinLink> joinSession(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long sessionId) {
        return ApiResponse.success("Joining the live session.", liveSessions.join(currentUser, sessionId));
    }
}
