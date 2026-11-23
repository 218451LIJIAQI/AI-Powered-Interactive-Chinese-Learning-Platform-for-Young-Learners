package com.example.aichinese.controller;

import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ApiResponse;
import com.example.aichinese.dto.classes.AdminClassViews.LessonEditor;
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.QuizQuestionRequest;
import com.example.aichinese.dto.classes.QuizRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.LessonEditorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import lombok.RequiredArgsConstructor;

/**
 * The admin lesson editor of a class lesson. Every change answers with the whole editor, so the page always shows what
 * was saved (including the ids of the working copy the first change of a published lesson creates).
 */
@Validated
@RestController
@RequestMapping("/api/v1/admin/lessons/{lessonId}")
@RequiredArgsConstructor
public class AdminLessonController {

    private final LessonEditorService editor;

    @GetMapping
    public ApiResponse<LessonEditor> lesson(@PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", editor.editor(lessonId));
    }

    @PutMapping("/content")
    public ApiResponse<LessonEditor> saveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody ClassRequests.LessonContent request) {
        return ApiResponse.success("Lesson saved.", editor.saveContent(currentUser, lessonId, request));
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LessonEditor> saveVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            @RequestParam("title") String title,
            @PositiveOrZero(message = "Duration must not be negative.")
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success("Video saved.", editor.saveVideo(currentUser, lessonId, title, durationSeconds, file));
    }

    @DeleteMapping("/video")
    public ApiResponse<LessonEditor> removeVideo(@PathVariable Long lessonId) {
        return ApiResponse.success("Video removed.", editor.removeVideo(lessonId));
    }

    @PostMapping("/activities")
    public ApiResponse<LessonEditor> addActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody ClassRequests.Activity request) {
        return ApiResponse.success("Activity added.", editor.addActivity(currentUser, lessonId, request));
    }

    @PutMapping("/activities/{type}/{activityId}")
    public ApiResponse<LessonEditor> updateActivity(
            @PathVariable Long lessonId,
            @PathVariable String type,
            @PathVariable Long activityId,
            @Valid @RequestBody ClassRequests.Activity request) {
        return ApiResponse.success("Activity saved.", editor.updateActivity(lessonId, type, activityId, request));
    }

    @PostMapping("/quizzes")
    public ApiResponse<LessonEditor> addQuiz(
            @PathVariable Long lessonId,
            @Valid @RequestBody QuizRequest request) {
        return ApiResponse.success("Quiz added.", editor.addQuiz(lessonId, request));
    }

    /** The quiz's settings: "Allow SparkBot hints" ({@code hintsAllowed}). */
    @PutMapping("/quizzes/{quizId}")
    public ApiResponse<LessonEditor> updateQuiz(
            @PathVariable Long lessonId,
            @PathVariable Long quizId,
            @Valid @RequestBody ClassRequests.QuizSettings request) {
        return ApiResponse.success("Quiz saved.", editor.updateQuiz(lessonId, quizId, request));
    }

    /** Adds a question (a lesson quiz has at most 5); {@code position} puts it at that place (the Undo of a deletion). */
    @PostMapping("/quizzes/{quizId}/questions")
    public ApiResponse<LessonEditor> addQuestion(
            @PathVariable Long lessonId,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizQuestionRequest request) {
        return ApiResponse.success("Question added.", editor.addQuestion(lessonId, quizId, request));
    }

    @PutMapping("/questions/{questionId}")
    public ApiResponse<LessonEditor> updateQuestion(
            @PathVariable Long lessonId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuizQuestionRequest request) {
        return ApiResponse.success("Question saved.", editor.updateQuestion(lessonId, questionId, request));
    }

    /** Keeps a question taken from the AI quiz draft as it is: it is no longer an unchecked AI draft. */
    @PostMapping("/questions/{questionId}/keep")
    public ApiResponse<LessonEditor> keepQuestion(
            @PathVariable Long lessonId,
            @PathVariable Long questionId) {
        return ApiResponse.success("Question kept.", editor.keepQuestion(lessonId, questionId));
    }

    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<LessonEditor> deleteQuestion(
            @PathVariable Long lessonId,
            @PathVariable Long questionId) {
        return ApiResponse.success("Question removed.", editor.deleteQuestion(lessonId, questionId));
    }

    /** Publishes a draft lesson, or the unpublished changes of a published one. */
    @PostMapping("/publish")
    public ApiResponse<LessonEditor> publish(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson published.", editor.publish(currentUser, lessonId));
    }

    @DeleteMapping("/revision")
    public ApiResponse<LessonEditor> discardRevision(@PathVariable Long lessonId) {
        return ApiResponse.success("Changes discarded.", editor.discardRevision(lessonId));
    }

    @PostMapping("/archive")
    public ApiResponse<LessonEditor> archive(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson archived.", editor.archive(currentUser, lessonId));
    }

    @PostMapping("/restore")
    public ApiResponse<LessonEditor> restore(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson restored.", editor.restore(currentUser, lessonId));
    }
}
