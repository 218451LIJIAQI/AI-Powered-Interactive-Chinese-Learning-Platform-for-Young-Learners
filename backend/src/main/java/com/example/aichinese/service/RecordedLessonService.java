package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learning.RecordedLessonVideoRequest;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.enums.RecordedVideoSourceType;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.TextUtils;

@Service
public class RecordedLessonService {

    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final LiveLearningAccessService liveLearningAccessService;
    private final RecordedVideoStorageService recordedVideoStorageService;
    private final LiveLearningMapper liveLearningMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public RecordedLessonService(
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            LiveLearningAccessService liveLearningAccessService,
            RecordedVideoStorageService recordedVideoStorageService,
            LiveLearningMapper liveLearningMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.recordedLessonVideoRepository = recordedLessonVideoRepository;
        this.liveLearningAccessService = liveLearningAccessService;
        this.recordedVideoStorageService = recordedVideoStorageService;
        this.liveLearningMapper = liveLearningMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public RecordedLessonVideoResponse createRecordedVideo(
            SecurityUserDetails currentUser,
            Long lessonId,
            RecordedLessonVideoRequest request,
            MultipartFile file) {
        Lesson lesson = liveLearningAccessService.requireOwnedLesson(currentUser, lessonId);
        RecordedVideoSourceType sourceType = liveLearningAccessService.parseRecordedVideoSourceType(request.sourceType());
        CoursePackage coursePackage = request.packageId() == null
                ? null
                : liveLearningAccessService.requireOwnedPackage(currentUser, request.packageId());
        UploadedFile uploadedFile = sourceType == RecordedVideoSourceType.UPLOADED_FILE
                ? recordedVideoStorageService.storeRecordedVideo(file, lesson.getTeacher().getUser())
                : null;
        liveLearningAccessService.validateRecordedVideoSource(sourceType, request.externalVideoUrl(), uploadedFile);

        RecordedLessonVideo video = new RecordedLessonVideo();
        applyVideoUpdate(video, lesson, coursePackage, request, sourceType, uploadedFile);
        RecordedLessonVideo saved = recordedLessonVideoRepository.save(video);

        notificationService.createNotification(
                lesson.getTeacher().getUser(),
                "Recorded lesson video added",
                "A recorded lesson video was added to " + lesson.getTitle() + ".",
                "/teacher/recorded-lessons");
        auditLogService.record(
                lesson.getTeacher().getUser(),
                "TEACHER_RECORDED_VIDEO_CREATE",
                "RecordedLessonVideo",
                saved.getId(),
                "Teacher added a recorded lesson video.");

        return toTeacherResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecordedLessonVideoResponse> getTeacherRecordedVideos(SecurityUserDetails currentUser, Long lessonId) {
        liveLearningAccessService.requireOwnedLesson(currentUser, lessonId);
        return recordedLessonVideoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(lessonId).stream()
                .map(this::toTeacherResponse)
                .toList();
    }

    @Transactional
    public RecordedLessonVideoResponse updateRecordedVideo(
            SecurityUserDetails currentUser,
            Long lessonId,
            Long videoId,
            RecordedLessonVideoRequest request,
            MultipartFile file) {
        RecordedLessonVideo video = liveLearningAccessService.requireTeacherOwnedRecordedVideo(currentUser, lessonId, videoId);
        RecordedVideoSourceType sourceType = liveLearningAccessService.parseRecordedVideoSourceType(request.sourceType());
        CoursePackage coursePackage = request.packageId() == null
                ? null
                : liveLearningAccessService.requireOwnedPackage(currentUser, request.packageId());
        UploadedFile uploadedFile = video.getUploadedFile();
        if (sourceType == RecordedVideoSourceType.UPLOADED_FILE && file != null && !file.isEmpty()) {
            uploadedFile = recordedVideoStorageService.storeRecordedVideo(file, video.getLesson().getTeacher().getUser());
        }
        liveLearningAccessService.validateRecordedVideoSource(sourceType, request.externalVideoUrl(), uploadedFile);
        applyVideoUpdate(video, video.getLesson(), coursePackage, request, sourceType, uploadedFile);
        RecordedLessonVideo saved = recordedLessonVideoRepository.save(video);

        auditLogService.record(
                video.getLesson().getTeacher().getUser(),
                "TEACHER_RECORDED_VIDEO_UPDATE",
                "RecordedLessonVideo",
                saved.getId(),
                "Teacher updated a recorded lesson video.");

        return toTeacherResponse(saved);
    }

    @Transactional
    public RecordedLessonVideoResponse archiveRecordedVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        RecordedLessonVideo video = liveLearningAccessService.requireTeacherOwnedRecordedVideo(currentUser, lessonId, videoId);
        video.setActive(false);
        RecordedLessonVideo saved = recordedLessonVideoRepository.save(video);

        auditLogService.record(
                video.getLesson().getTeacher().getUser(),
                "TEACHER_RECORDED_VIDEO_ARCHIVE",
                "RecordedLessonVideo",
                saved.getId(),
                "Teacher archived a recorded lesson video.");

        return toTeacherResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RecordedLessonVideoResponse> getStudentRecordedVideos(SecurityUserDetails currentUser, Long lessonId) {
        var student = liveLearningAccessService.requireStudent(currentUser);
        var lesson = liveLearningAccessService.requirePublishedLesson(lessonId);
        liveLearningAccessService.ensureStudentHasLessonAccess(student, lesson);
        return recordedLessonVideoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscCreatedAtAsc(lessonId).stream()
                .filter(video -> canStudentAccessVideo(currentUser, video))
                .map(this::toStudentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public RecordedLessonVideoResponse getStudentRecordedVideo(SecurityUserDetails currentUser, Long lessonId, Long videoId) {
        return toStudentResponse(liveLearningAccessService.requireAccessibleRecordedVideo(currentUser, lessonId, videoId));
    }

    @Transactional(readOnly = true)
    public StoredFileDownload loadStudentRecordedVideoDownload(SecurityUserDetails currentUser, Long videoId) {
        UploadedFile uploadedFile = liveLearningAccessService.requireAccessibleRecordedFile(currentUser, videoId);
        return recordedVideoStorageService.loadAsDownload(uploadedFile);
    }

    @Transactional(readOnly = true)
    public StoredFileDownload loadTeacherRecordedVideoDownload(
            SecurityUserDetails currentUser,
            Long lessonId,
            Long videoId) {
        RecordedLessonVideo video = liveLearningAccessService.requireTeacherOwnedRecordedVideo(currentUser, lessonId, videoId);
        if (video.getUploadedFile() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is required for recorded video.");
        }
        return recordedVideoStorageService.loadAsDownload(video.getUploadedFile());
    }

    private void applyVideoUpdate(
            RecordedLessonVideo video,
            Lesson lesson,
            CoursePackage coursePackage,
            RecordedLessonVideoRequest request,
            RecordedVideoSourceType sourceType,
            UploadedFile uploadedFile) {
        if (sourceType == RecordedVideoSourceType.UPLOADED_FILE && uploadedFile == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Uploaded file is required for recorded video.");
        }

        video.setLesson(lesson);
        video.setCoursePackage(coursePackage);
        video.setTitle(request.title().trim());
        video.setDescription(TextUtils.trimToNull(request.description()));
        video.setSourceType(sourceType);
        video.setExternalVideoUrl(sourceType == RecordedVideoSourceType.EXTERNAL_URL && request.externalVideoUrl() != null
                ? request.externalVideoUrl().trim()
                : null);
        video.setUploadedFile(sourceType == RecordedVideoSourceType.UPLOADED_FILE ? uploadedFile : null);
        video.setDurationSeconds(request.durationSeconds());
        video.setSortOrder(request.sortOrder());
        video.setActive(true);
    }

    private boolean canStudentAccessVideo(SecurityUserDetails currentUser, RecordedLessonVideo video) {
        try {
            liveLearningAccessService.requireAccessibleRecordedVideo(currentUser, video.getLesson().getId(), video.getId());
            return true;
        } catch (ResponseStatusException exception) {
            return false;
        }
    }

    private RecordedLessonVideoResponse toTeacherResponse(RecordedLessonVideo video) {
        String streamUrl = video.getSourceType() == RecordedVideoSourceType.UPLOADED_FILE
                ? "/api/v1/teachers/lessons/" + video.getLesson().getId() + "/recorded-videos/" + video.getId() + "/stream"
                : null;
        String externalUrl = video.getSourceType() == RecordedVideoSourceType.EXTERNAL_URL ? video.getExternalVideoUrl() : null;
        return liveLearningMapper.toRecordedVideoResponse(video, streamUrl, externalUrl);
    }

    private RecordedLessonVideoResponse toStudentResponse(RecordedLessonVideo video) {
        String streamUrl = video.getSourceType() == RecordedVideoSourceType.UPLOADED_FILE
                ? "/api/v1/students/recorded-videos/" + video.getId() + "/stream"
                : null;
        String externalUrl = video.getSourceType() == RecordedVideoSourceType.EXTERNAL_URL ? video.getExternalVideoUrl() : null;
        return liveLearningMapper.toRecordedVideoResponse(video, streamUrl, externalUrl);
    }
}
