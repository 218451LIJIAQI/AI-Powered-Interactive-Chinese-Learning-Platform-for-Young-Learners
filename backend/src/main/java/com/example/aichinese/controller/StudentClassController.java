package com.example.aichinese.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.example.aichinese.dto.classes.QuizSubmissionAnswerRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassQuizService;
import com.example.aichinese.service.classes.StudentClassActivityService;
import com.example.aichinese.service.classes.StudentClassService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;

/** The learner's classes: My classes, Find classes, joining and leaving, class and lesson pages, activities, lesson quizzes. */
@RestController
@RequestMapping("/api/v1/students/classes")
@RequiredArgsConstructor
public class StudentClassController {

    private final StudentClassService classes;
    private final StudentClassActivityService activities;
    private final ClassQuizService quizzes;

    @GetMapping
    public ApiResponse<ClassViews.MyClasses> myClasses(@AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Your classes retrieved successfully.", classes.myClasses(currentUser));
    }

    @GetMapping("/catalogue")
    public ApiResponse<PageResponse<ClassViews.ClassCard>> findClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size) {
        return ApiResponse.success("Classes retrieved successfully.", classes.findClasses(currentUser, page, size));
    }

    @GetMapping("/catalogue/membership")
    public ApiResponse<ClassViews.CatalogueMembership> catalogueMembership(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success("Classes retrieved successfully.", classes.catalogueMembership(currentUser));
    }

    @GetMapping("/{classId:\\d+}")
    public ApiResponse<ClassViews.StudentClass> classPage(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        return ApiResponse.success("Class retrieved successfully.", classes.classPage(currentUser, classId));
    }

    @PostMapping("/{classId:\\d+}/join")
    public ApiResponse<Void> join(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        classes.join(currentUser, classId);
        return ApiResponse.success("You joined the class.", null);
    }

    /** Leaves the class (also the Undo of joining it); the learner's progress is kept for joining again. */
    @PostMapping("/{classId:\\d+}/leave")
    public ApiResponse<Void> leave(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long classId) {
        classes.leave(currentUser, classId);
        return ApiResponse.success("You left the class.", null);
    }

    // Lessons

    @GetMapping("/lessons/{lessonId}")
    public ApiResponse<LessonViews.StudentLesson> lesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", classes.lesson(currentUser, lessonId));
    }

    @PostMapping("/lessons/{lessonId}/video/watched")
    public ApiResponse<Void> markVideoWatched(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        classes.markVideoWatched(currentUser, lessonId);
        return ApiResponse.success("Video marked as watched.", null);
    }

    @StoredFileResponse
    @GetMapping("/lessons/{lessonId}/video/{videoId}/stream")
    public ResponseEntity<Resource> streamVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(classes.streamVideo(currentUser, lessonId, videoId));
    }

    // Activities

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

    // Lesson quizzes

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

    @DeleteMapping("/quiz-attempts/{attemptId}/answers/{questionId}")
    public ApiResponse<Void> clearAnswer(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId,
            @PathVariable Long questionId) {
        quizzes.clearAnswer(currentUser, attemptId, questionId);
        return ApiResponse.success("Answer cleared.", null);
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

    /** Leaves a quiz midway: the open attempt and its saved answers are discarded (submitted attempts stay). */
    @DeleteMapping("/quiz-attempts/{attemptId}")
    public ApiResponse<Void> discardQuizAttempt(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        quizzes.discard(currentUser, attemptId);
        return ApiResponse.success("Quiz attempt discarded.", null);
    }

    @GetMapping("/quiz-attempts/{attemptId}")
    public ApiResponse<LessonViews.QuizResult> quizResult(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long attemptId) {
        return ApiResponse.success("Quiz result retrieved successfully.", quizzes.result(currentUser, attemptId));
    }
}
