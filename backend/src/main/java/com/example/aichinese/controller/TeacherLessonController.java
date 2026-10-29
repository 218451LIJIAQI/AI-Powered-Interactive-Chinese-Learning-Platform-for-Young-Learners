package com.example.aichinese.controller;

import static com.example.aichinese.dto.DtoNormalization.OPTIONAL_HTTP_URL_PATTERN;

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
import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.TeacherClassViews.LessonEditor;
import com.example.aichinese.dto.learning.QuizQuestionRequest;
import com.example.aichinese.dto.learning.QuizRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.TeacherLessonEditorService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The lesson editor of a class lesson. Every change answers with the whole editor, so the page always shows what
 * was saved (including ids of the revision copy the first change of a published lesson creates).
 */
@Validated
@RestController
@RequestMapping("/api/v1/teachers/lessons/{lessonId}")
public class TeacherLessonController {

    private static final String SAVED = "Lesson saved.";

    private final TeacherLessonEditorService editor;

    public TeacherLessonController(TeacherLessonEditorService editor) {
        this.editor = editor;
    }

    @GetMapping
    public ApiResponse<LessonEditor> lesson(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Lesson retrieved successfully.", editor.editor(currentUser, lessonId));
    }

    @PutMapping("/content")
    public ApiResponse<LessonEditor> saveContent(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody ClassRequests.LessonContent request) {
        return ApiResponse.success(SAVED, editor.saveContent(currentUser, lessonId, request));
    }

    @PostMapping(value = "/video", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<LessonEditor> saveVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            @RequestParam("title") String title,
            @NotBlank(message = "Source type is required.")
            @Size(max = 30, message = "Source type must be 30 characters or fewer.")
            @RequestParam("sourceType") String sourceType,
            @Size(max = 500, message = "External video URL must be 500 characters or fewer.")
            @Pattern(regexp = OPTIONAL_HTTP_URL_PATTERN, message = "External video URL must start with http:// or https:// and contain no whitespace.")
            @RequestParam(value = "externalVideoUrl", required = false) String externalVideoUrl,
            @PositiveOrZero(message = "Duration must not be negative.")
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success("Video saved.",
                editor.saveVideo(currentUser, lessonId, title, sourceType, externalVideoUrl, durationSeconds, file));
    }

    @DeleteMapping("/video")
    public ApiResponse<LessonEditor> removeVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Video removed.", editor.removeVideo(currentUser, lessonId));
    }

    // ----- activities -----

    @PostMapping("/activities")
    public ApiResponse<LessonEditor> addActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody ClassRequests.Activity request) {
        return ApiResponse.success("Activity added.", editor.addActivity(currentUser, lessonId, request));
    }

    @PutMapping("/activities/{type}/{activityId}")
    public ApiResponse<LessonEditor> updateActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable String type,
            @PathVariable Long activityId,
            @Valid @RequestBody ClassRequests.Activity request) {
        return ApiResponse.success("Activity saved.", editor.updateActivity(currentUser, lessonId, type, activityId, request));
    }

    @DeleteMapping("/activities/{type}/{activityId}")
    public ApiResponse<LessonEditor> deleteActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable String type,
            @PathVariable Long activityId) {
        return ApiResponse.success("Activity removed.", editor.deleteActivity(currentUser, lessonId, type, activityId));
    }

    @PostMapping("/activities/{type}/{activityId}/duplicate")
    public ApiResponse<LessonEditor> duplicateActivity(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable String type,
            @PathVariable Long activityId) {
        return ApiResponse.success("Activity duplicated.", editor.duplicateActivity(currentUser, lessonId, type, activityId));
    }

    @PutMapping("/activity-order")
    public ApiResponse<LessonEditor> reorderActivities(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody ClassRequests.ActivityOrder request) {
        return ApiResponse.success("New order saved.", editor.reorderActivities(currentUser, lessonId, request));
    }

    // ----- quizzes -----

    @PostMapping("/quizzes")
    public ApiResponse<LessonEditor> addQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody QuizRequest request) {
        return ApiResponse.success("Quiz added.", editor.addQuiz(currentUser, lessonId, request));
    }

    @PutMapping("/quizzes/{quizId}")
    public ApiResponse<LessonEditor> updateQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizRequest request) {
        return ApiResponse.success("Quiz saved.", editor.updateQuiz(currentUser, lessonId, quizId, request));
    }

    @PostMapping("/quizzes/{quizId}/archive")
    public ApiResponse<LessonEditor> archiveQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long quizId) {
        return ApiResponse.success("Quiz archived.", editor.archiveQuiz(currentUser, lessonId, quizId));
    }

    @PostMapping("/quizzes/{quizId}/duplicate")
    public ApiResponse<LessonEditor> duplicateQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long quizId) {
        return ApiResponse.success("Quiz duplicated.", editor.duplicateQuiz(currentUser, lessonId, quizId));
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ApiResponse<LessonEditor> addQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long quizId,
            @Valid @RequestBody QuizQuestionRequest request) {
        return ApiResponse.success("Question added.", editor.addQuestion(currentUser, lessonId, quizId, request));
    }

    @PutMapping("/quizzes/{quizId}/question-order")
    public ApiResponse<LessonEditor> reorderQuestions(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long quizId,
            @Valid @RequestBody ClassRequests.QuestionOrder request) {
        return ApiResponse.success("New order saved.", editor.reorderQuestions(currentUser, lessonId, quizId, request));
    }

    @PutMapping("/questions/{questionId}")
    public ApiResponse<LessonEditor> updateQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long questionId,
            @Valid @RequestBody QuizQuestionRequest request) {
        return ApiResponse.success("Question saved.", editor.updateQuestion(currentUser, lessonId, questionId, request));
    }

    @DeleteMapping("/questions/{questionId}")
    public ApiResponse<LessonEditor> deleteQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long questionId) {
        return ApiResponse.success("Question removed.", editor.deleteQuestion(currentUser, lessonId, questionId));
    }

    @PostMapping("/questions/{questionId}/duplicate")
    public ApiResponse<LessonEditor> duplicateQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long questionId) {
        return ApiResponse.success("Question duplicated.", editor.duplicateQuestion(currentUser, lessonId, questionId));
    }

    // ----- review workflow -----

    @PostMapping("/submit")
    public ApiResponse<LessonEditor> submit(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Submitted for admin review.", editor.submit(currentUser, lessonId));
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

    @DeleteMapping("/revision")
    public ApiResponse<LessonEditor> discardRevision(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId) {
        return ApiResponse.success("Changes discarded.", editor.discardRevision(currentUser, lessonId));
    }
}
