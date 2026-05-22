package com.example.aichinese.controller;

import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
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
import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.analytics.TeacherAnalyticsResponse;
import com.example.aichinese.dto.content.CoursePackageResponse;
import com.example.aichinese.dto.content.CreateCoursePackageRequest;
import com.example.aichinese.dto.content.CreateLessonRequest;
import com.example.aichinese.dto.content.CreateModuleRequest;
import com.example.aichinese.dto.content.LearningModuleResponse;
import com.example.aichinese.dto.content.LessonResponse;
import com.example.aichinese.dto.content.PackageItemRequest;
import com.example.aichinese.dto.content.UpdateCoursePackageRequest;
import com.example.aichinese.dto.content.UpdateLessonRequest;
import com.example.aichinese.dto.content.UpdateModuleRequest;
import com.example.aichinese.dto.dashboard.TeacherDashboardResponse;
import com.example.aichinese.dto.learning.CreateLiveClassRequest;
import com.example.aichinese.dto.learning.CreateQuizQuestionRequest;
import com.example.aichinese.dto.learning.CreateQuizRequest;
import com.example.aichinese.dto.learning.LiveClassEnrolmentResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.LessonVideoProgressSummaryResponse;
import com.example.aichinese.dto.learning.RecordedLessonVideoRequest;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.RescheduleLiveClassRequest;
import com.example.aichinese.dto.learning.TeacherQuizAttemptSummaryResponse;
import com.example.aichinese.dto.learning.TeacherQuizResponse;
import com.example.aichinese.dto.learning.UpdateLiveClassRequest;
import com.example.aichinese.dto.learning.UpdateLiveClassStatusRequest;
import com.example.aichinese.dto.teacher.TeacherProfileResponse;
import com.example.aichinese.dto.teacher.TeacherStudentSummaryResponse;
import com.example.aichinese.dto.teacher.TeacherVerificationStatusResponse;
import com.example.aichinese.dto.teacher.UpdateTeacherProfileRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.DashboardService;
import com.example.aichinese.service.AnalyticsService;
import com.example.aichinese.service.LiveClassService;
import com.example.aichinese.service.RecordedLessonService;
import com.example.aichinese.service.StoredFileDownload;
import com.example.aichinese.service.TeacherLessonService;
import com.example.aichinese.service.TeacherManagementService;
import com.example.aichinese.service.TeacherModuleService;
import com.example.aichinese.service.TeacherPackageService;
import com.example.aichinese.service.TeacherQuizService;
import com.example.aichinese.service.TeacherStudentService;
import com.example.aichinese.service.VideoProgressService;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Validated
@RestController
@RequestMapping("/api/v1/teachers")
public class TeacherController {

    private final DashboardService dashboardService;
    private final AnalyticsService analyticsService;
    private final TeacherManagementService teacherManagementService;
    private final TeacherModuleService teacherModuleService;
    private final TeacherLessonService teacherLessonService;
    private final TeacherPackageService teacherPackageService;
    private final TeacherQuizService teacherQuizService;
    private final TeacherStudentService teacherStudentService;
    private final LiveClassService liveClassService;
    private final RecordedLessonService recordedLessonService;
    private final VideoProgressService videoProgressService;

    public TeacherController(
            DashboardService dashboardService,
            AnalyticsService analyticsService,
            TeacherManagementService teacherManagementService,
            TeacherModuleService teacherModuleService,
            TeacherLessonService teacherLessonService,
            TeacherPackageService teacherPackageService,
            TeacherQuizService teacherQuizService,
            TeacherStudentService teacherStudentService,
            LiveClassService liveClassService,
            RecordedLessonService recordedLessonService,
            VideoProgressService videoProgressService) {
        this.dashboardService = dashboardService;
        this.analyticsService = analyticsService;
        this.teacherManagementService = teacherManagementService;
        this.teacherModuleService = teacherModuleService;
        this.teacherLessonService = teacherLessonService;
        this.teacherPackageService = teacherPackageService;
        this.teacherQuizService = teacherQuizService;
        this.teacherStudentService = teacherStudentService;
        this.liveClassService = liveClassService;
        this.recordedLessonService = recordedLessonService;
        this.videoProgressService = videoProgressService;
    }

    @GetMapping("/dashboard")
    public ApiResponse<TeacherDashboardResponse> getDashboard(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher dashboard retrieved successfully.",
                dashboardService.getTeacherDashboard(currentUser));
    }

    @GetMapping("/analytics")
    public ApiResponse<TeacherAnalyticsResponse> getAnalytics(
            @AuthenticationPrincipal SecurityUserDetails currentUser) {
        return ApiResponse.success(
                "Teacher analytics retrieved successfully.",
                analyticsService.getTeacherAnalytics(currentUser));
    }

    @GetMapping("/students")
    public ApiResponse<PageResponse<TeacherStudentSummaryResponse>> getTeacherStudents(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher students retrieved successfully.",
                teacherStudentService.getTeacherStudents(currentUser, page, size));
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
            @Size(max = 40, message = "Document type must not exceed 40 characters.")
            @RequestParam(value = "documentType", required = false) String documentType,
            @Size(max = 1000, message = "Description must not exceed 1000 characters.")
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
        StoredFileDownload download = teacherManagementService.getTeacherOwnedDocument(currentUser, id);
        return buildAttachmentDownloadResponse(download);
    }

    @GetMapping("/modules")
    public ApiResponse<PageResponse<LearningModuleResponse>> getTeacherModules(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher modules retrieved successfully.",
                teacherModuleService.getTeacherModules(currentUser, page, size));
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

    @GetMapping("/lessons")
    public ApiResponse<PageResponse<LessonResponse>> getTeacherLessons(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher lessons retrieved successfully.",
                teacherLessonService.getTeacherLessons(currentUser, page, size));
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


    @GetMapping("/live-classes")
    public ApiResponse<PageResponse<LiveClassResponse>> getTeacherLiveClasses(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher live classes retrieved successfully.",
                liveClassService.getTeacherLiveClasses(currentUser, page, size));
    }

    @PostMapping("/live-classes")
    public ApiResponse<LiveClassResponse> createLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @Valid @RequestBody CreateLiveClassRequest request) {
        return ApiResponse.success(
                "Live class created successfully.",
                liveClassService.createLiveClass(currentUser, request));
    }

    @GetMapping("/live-classes/{id}")
    public ApiResponse<LiveClassResponse> getTeacherLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Teacher live class retrieved successfully.",
                liveClassService.getTeacherLiveClass(currentUser, id));
    }

    @PutMapping("/live-classes/{id}")
    public ApiResponse<LiveClassResponse> updateLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLiveClassRequest request) {
        return ApiResponse.success(
                "Live class updated successfully.",
                liveClassService.updateLiveClass(currentUser, id, request));
    }

    @PatchMapping("/live-classes/{id}/status")
    public ApiResponse<LiveClassResponse> updateLiveClassStatus(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody UpdateLiveClassStatusRequest request) {
        return ApiResponse.success(
                "Live class status updated successfully.",
                liveClassService.updateStatus(currentUser, id, request.status()));
    }

    @PatchMapping("/live-classes/{id}/cancel")
    public ApiResponse<LiveClassResponse> cancelLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id) {
        return ApiResponse.success(
                "Live class cancelled successfully.",
                liveClassService.cancelLiveClass(currentUser, id));
    }

    @PatchMapping("/live-classes/{id}/reschedule")
    public ApiResponse<LiveClassResponse> rescheduleLiveClass(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @Valid @RequestBody RescheduleLiveClassRequest request) {
        return ApiResponse.success(
                "Live class rescheduled successfully.",
                liveClassService.rescheduleLiveClass(currentUser, id, request));
    }

    @GetMapping("/live-classes/{id}/enrolments")
    public ApiResponse<PageResponse<LiveClassEnrolmentResponse>> getLiveClassEnrolments(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Live class enrolments retrieved successfully.",
                liveClassService.getTeacherEnrolments(currentUser, id, page, size));
    }

    @GetMapping("/lessons/{lessonId}/recorded-videos")
    public ApiResponse<PageResponse<RecordedLessonVideoResponse>> getRecordedVideos(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Recorded lesson videos retrieved successfully.",
                recordedLessonService.getTeacherRecordedVideos(currentUser, lessonId, page, size));
    }

    @PostMapping(value = "/lessons/{lessonId}/recorded-videos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecordedLessonVideoResponse> createRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            @RequestParam("title") String title,
            @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "packageId", required = false) Long packageId,
            @NotBlank(message = "Source type is required.")
            @Size(max = 40, message = "Source type must not exceed 40 characters.")
            @RequestParam("sourceType") String sourceType,
            @Size(max = 2048, message = "External video URL must not exceed 2048 characters.")
            @Pattern(regexp = "^(https?://.+)?$", message = "External video URL must start with http:// or https://.")
            @RequestParam(value = "externalVideoUrl", required = false) String externalVideoUrl,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam(value = "sortOrder", defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success(
                "Recorded lesson video created successfully.",
                recordedLessonService.createRecordedVideo(
                        currentUser,
                        lessonId,
                        new RecordedLessonVideoRequest(
                                title,
                                description,
                                packageId,
                                sourceType,
                                externalVideoUrl,
                                durationSeconds,
                                sortOrder),
                        file));
    }

    @PutMapping(value = "/lessons/{lessonId}/recorded-videos/{videoId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RecordedLessonVideoResponse> updateRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId,
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            @RequestParam("title") String title,
            @Size(max = 1000, message = "Description must be 1000 characters or fewer.")
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "packageId", required = false) Long packageId,
            @NotBlank(message = "Source type is required.")
            @Size(max = 40, message = "Source type must not exceed 40 characters.")
            @RequestParam("sourceType") String sourceType,
            @Size(max = 2048, message = "External video URL must not exceed 2048 characters.")
            @Pattern(regexp = "^(https?://.+)?$", message = "External video URL must start with http:// or https://.")
            @RequestParam(value = "externalVideoUrl", required = false) String externalVideoUrl,
            @RequestParam(value = "durationSeconds", required = false) Integer durationSeconds,
            @RequestParam(value = "sortOrder", defaultValue = "0") Integer sortOrder,
            @RequestParam(value = "file", required = false) MultipartFile file) {
        return ApiResponse.success(
                "Recorded lesson video updated successfully.",
                recordedLessonService.updateRecordedVideo(
                        currentUser,
                        lessonId,
                        videoId,
                        new RecordedLessonVideoRequest(
                                title,
                                description,
                                packageId,
                                sourceType,
                                externalVideoUrl,
                                durationSeconds,
                                sortOrder),
                        file));
    }

    @PatchMapping("/lessons/{lessonId}/recorded-videos/{videoId}/archive")
    public ApiResponse<RecordedLessonVideoResponse> archiveRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId) {
        return ApiResponse.success(
                "Recorded lesson video archived successfully.",
                recordedLessonService.archiveRecordedVideo(currentUser, lessonId, videoId));
    }

    @GetMapping("/lessons/{lessonId}/recorded-videos/{videoId}/stream")
    public ResponseEntity<Resource> streamRecordedVideo(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @PathVariable Long videoId) {
        StoredFileDownload download = recordedLessonService.loadTeacherRecordedVideoDownload(currentUser, lessonId, videoId);
        return buildInlineDownloadResponse(download);
    }

    @GetMapping("/lessons/{lessonId}/video-progress")
    public ApiResponse<PageResponse<LessonVideoProgressSummaryResponse>> getLessonVideoProgress(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Lesson video progress retrieved successfully.",
                videoProgressService.getTeacherLessonProgress(currentUser, lessonId, page, size));
    }

    private ResponseEntity<Resource> buildInlineDownloadResponse(StoredFileDownload download) {
        return FileDownloadResponseBuilder.buildInlineDownloadResponse(download);
    }

    private ResponseEntity<Resource> buildAttachmentDownloadResponse(StoredFileDownload download) {
        return FileDownloadResponseBuilder.buildAttachmentDownloadResponse(download);
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

    @GetMapping("/packages")
    public ApiResponse<PageResponse<CoursePackageResponse>> getTeacherPackages(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Teacher packages retrieved successfully.",
                teacherPackageService.getTeacherPackages(currentUser, page, size));
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

    @GetMapping("/lessons/{lessonId}/quizzes")
    public ApiResponse<PageResponse<TeacherQuizResponse>> getLessonQuizzes(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Quizzes retrieved successfully.",
                teacherQuizService.getQuizzes(currentUser, lessonId, page, size));
    }

    @PostMapping("/lessons/{lessonId}/quizzes")
    public ApiResponse<TeacherQuizResponse> createQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long lessonId,
            @Valid @RequestBody CreateQuizRequest request) {
        return ApiResponse.success(
                "Quiz created successfully.",
                teacherQuizService.createQuiz(currentUser, lessonId, request));
    }

    @PutMapping("/quizzes/{quizId}")
    public ApiResponse<TeacherQuizResponse> updateQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateQuizRequest request) {
        return ApiResponse.success(
                "Quiz updated successfully.",
                teacherQuizService.updateQuiz(currentUser, quizId, request));
    }

    @DeleteMapping("/quizzes/{quizId}")
    public ApiResponse<Void> deleteQuiz(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId) {
        teacherQuizService.deleteQuiz(currentUser, quizId);
        return ApiResponse.success("Quiz archived successfully.", null);
    }

    @PostMapping("/quizzes/{quizId}/questions")
    public ApiResponse<TeacherQuizResponse> createQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @Valid @RequestBody CreateQuizQuestionRequest request) {
        return ApiResponse.success(
                "Quiz question created successfully.",
                teacherQuizService.addQuizQuestion(currentUser, quizId, request));
    }

    @PutMapping("/quiz-questions/{questionId}")
    public ApiResponse<TeacherQuizResponse> updateQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long questionId,
            @Valid @RequestBody com.example.aichinese.dto.learning.UpdateQuizQuestionRequest request) {
        return ApiResponse.success(
                "Quiz question updated successfully.",
                teacherQuizService.updateQuizQuestion(currentUser, questionId, request));
    }

    @DeleteMapping("/quiz-questions/{questionId}")
    public ApiResponse<TeacherQuizResponse> deleteQuizQuestion(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long questionId) {
        return ApiResponse.success(
                "Quiz question deleted successfully.",
                teacherQuizService.deleteQuizQuestion(currentUser, questionId));
    }

    @GetMapping("/quizzes/{quizId}/attempts")
    public ApiResponse<PageResponse<TeacherQuizAttemptSummaryResponse>> getQuizAttempts(
            @AuthenticationPrincipal SecurityUserDetails currentUser,
            @PathVariable Long quizId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.success(
                "Quiz attempts retrieved successfully.",
                teacherQuizService.getQuizAttempts(currentUser, quizId, page, size));
    }

}
