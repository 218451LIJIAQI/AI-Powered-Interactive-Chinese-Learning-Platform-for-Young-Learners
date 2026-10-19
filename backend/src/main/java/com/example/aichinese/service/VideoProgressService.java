package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.Objects;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.LessonVideoProgressSummaryResponse;
import com.example.aichinese.dto.learning.VideoProgressRequest;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

/**
 * Persists per-student playback position for recorded lesson videos
 * and surfaces aggregate lesson-video progress to the front-end
 * progress UI.
 *
 * <p>Progress is upserted on every heartbeat — students with multiple
 * tabs / devices will overwrite each other's playback position rather than
 * diverge, which matches the platform's "resume from last-known position" UX.
 * The progress percentage never decreases and completion is sticky.
 * The video (and its lesson/course) is re-checked on every read and write via
 * {@link LiveLearningAccessService} so progress cannot be recorded against
 * content that is no longer published.</p>
 *
 * <p>A {@code PROGRESS_UPDATE} notification is sent to the lesson's teacher the
 * first time the student crosses the completion threshold, so teachers see
 * engagement signals without double-firing on subsequent rewatch.</p>
 */
@Service
public class VideoProgressService {

    private static final int ZERO_PROGRESS = 0;
    private static final int COMPLETE_PROGRESS = 100;
    private static final String VIDEO_PROGRESS_REQUEST_REQUIRED = "Video progress request is required.";
    private static final String TARGET_RECORDED_VIDEO = "RecordedLessonVideo";
    private static final String TARGET_TYPE_RECORDED_VIDEO = "RECORDED_VIDEO";
    private static final String ACTION_VIDEO_COMPLETE = "STUDENT_RECORDED_VIDEO_COMPLETE";
    private static final String TEACHER_CLASSES_PATH = "/teacher/classes";

    private final VideoProgressRepository videoProgressRepository;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;
    private final StudentLearningProgressService studentLearningProgressService;

    public VideoProgressService(
            VideoProgressRepository videoProgressRepository,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper,
            NotificationService notificationService,
            AuditLogService auditLogService,
            StudentLearningProgressService studentLearningProgressService) {
        this.videoProgressRepository = Objects.requireNonNull(videoProgressRepository, "videoProgressRepository");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
        this.liveLearningMapper = Objects.requireNonNull(liveLearningMapper, "liveLearningMapper");
        this.notificationService = Objects.requireNonNull(notificationService, "notificationService");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
        this.studentLearningProgressService = Objects.requireNonNull(
                studentLearningProgressService, "studentLearningProgressService");
    }

    @Transactional(readOnly = true)
    public VideoProgressResponse getProgress(SecurityUserDetails currentUser, Long videoId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        RecordedLessonVideo video = requireAccessibleVideo(currentUser, videoId);
        // Before the first save there is no row yet: return a zero record instead of mapping an unsaved entity.
        return videoProgressRepository.findByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .map(liveLearningMapper::toVideoProgressResponse)
                .orElseGet(() -> new VideoProgressResponse(
                        null,
                        video.getLesson().getId(),
                        video.getId(),
                        ZERO_PROGRESS,
                        video.getDurationSeconds(),
                        ZERO_PROGRESS,
                        false,
                        null,
                        null));
    }

    @Transactional
    public VideoProgressResponse upsertProgress(SecurityUserDetails currentUser, Long videoId, VideoProgressRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, VIDEO_PROGRESS_REQUEST_REQUIRED);
        }
        StudentProfile student = liveLearningAccessService.requireLockedStudent(currentUser);
        RecordedLessonVideo video = requireAccessibleVideo(currentUser, videoId);
        validateRequest(request);

        VideoProgress progress = videoProgressRepository.findLockedByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .orElseGet(() -> createDefaultProgress(student, video));
        // Capture the prior completion flag BEFORE applyProgress mutates it.
        // The completion notification + audit row should fire exactly once,
        // on the transition from incomplete to complete. Without this guard
        // a student who refreshes the player page after finishing a video
        // would emit a fresh "completed" notification + audit row every
        // time the player POSTs another progress heartbeat, polluting the
        // teacher's notification feed and the audit log.
        boolean wasAlreadyCompleted = progress.isCompleted();
        applyProgress(progress, request, video);
        VideoProgress saved = videoProgressRepository.save(progress);

        if (saved.isCompleted() && !wasAlreadyCompleted) {
            notificationService.safeCreateNotification(
                    teacherUser(video),
                    NotificationType.PROGRESS_UPDATE,
                    "Recorded video completed",
                    studentDisplayName(student) + " completed " + video.getTitle() + ".",
                    TARGET_TYPE_RECORDED_VIDEO,
                    video.getId(),
                    TEACHER_CLASSES_PATH,
                    student.getUser());
            auditLogService.record(
                    student.getUser(),
                    ACTION_VIDEO_COMPLETE,
                    TARGET_RECORDED_VIDEO,
                    video.getId(),
                    "Student completed a recorded video.");
            // Lessons without quizzes complete from content + recorded videos (D25).
            studentLearningProgressService.recordLessonVideoCompleted(student, video.getLesson());
        }

        return liveLearningMapper.toVideoProgressResponse(saved);
    }

    @Transactional
    public VideoProgressResponse completeVideo(SecurityUserDetails currentUser, Long videoId) {
        RecordedLessonVideo video = liveLearningAccessService.requireActiveRecordedVideo(videoId);
        int totalSeconds = Objects.requireNonNullElse(video.getDurationSeconds(), ZERO_PROGRESS);
        return upsertProgress(currentUser, videoId, new VideoProgressRequest(totalSeconds, totalSeconds, COMPLETE_PROGRESS, true));
    }

    @Transactional(readOnly = true)
    public PageResponse<LessonVideoProgressSummaryResponse> getTeacherLessonProgress(
            SecurityUserDetails currentUser,
            Long lessonId,
            int page,
            int size) {
        liveLearningAccessService.requireOwnedLesson(currentUser, lessonId);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Page<VideoProgress> progressPage = videoProgressRepository.findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(
                currentUser.getUserId(),
                lessonId,
                PageRequest.of(safePage, safeSize));
        return new PageResponse<>(
                progressPage.getContent().stream()
                        .map(VideoProgressService::toLessonVideoProgressSummary)
                        .toList(),
                safePage,
                safeSize,
                progressPage.getTotalElements(),
                progressPage.getTotalPages());
    }

    private RecordedLessonVideo requireAccessibleVideo(SecurityUserDetails currentUser, Long videoId) {
        RecordedLessonVideo video = liveLearningAccessService.requireActiveRecordedVideo(videoId);
        return liveLearningAccessService.requireAccessibleRecordedVideo(
                currentUser,
                video.getLesson().getId(),
                videoId);
    }

    private static String studentDisplayName(StudentProfile student) {
        if (student.getUser() != null && student.getUser().getDisplayName() != null
                && !student.getUser().getDisplayName().isBlank()) {
            return student.getUser().getDisplayName();
        }
        return student.getDisplayName() == null || student.getDisplayName().isBlank()
                ? "Student"
                : student.getDisplayName();
    }

    private static User teacherUser(RecordedLessonVideo video) {
        return video.getLesson() != null && video.getLesson().getTeacher() != null
                ? video.getLesson().getTeacher().getUser()
                : null;
    }

    private static LessonVideoProgressSummaryResponse toLessonVideoProgressSummary(VideoProgress progress) {
        StudentProfile student = progress.getStudent();
        RecordedLessonVideo video = progress.getRecordedVideo();
        var user = student.getUser();
        return new LessonVideoProgressSummaryResponse(
                student.getId(),
                user == null
                        ? student.getDisplayName()
                        : user.getDisplayName(),
                video == null ? null : video.getId(),
                video == null ? null : video.getTitle(),
                progress.getProgressPercentage(),
                progress.isCompleted(),
                progress.getWatchedSeconds(),
                progress.getTotalSeconds(),
                progress.getLastWatchedAt());
    }

    private static VideoProgress createDefaultProgress(StudentProfile student, RecordedLessonVideo video) {
        VideoProgress progress = new VideoProgress();
        progress.setStudent(student);
        progress.setLesson(video.getLesson());
        progress.setRecordedVideo(video);
        progress.setWatchedSeconds(ZERO_PROGRESS);
        progress.setTotalSeconds(Objects.requireNonNullElse(video.getDurationSeconds(), ZERO_PROGRESS));
        progress.setProgressPercentage(ZERO_PROGRESS);
        progress.setCompleted(false);
        progress.setLastWatchedAt(null);
        return progress;
    }

    private static void applyProgress(VideoProgress progress, VideoProgressRequest request, RecordedLessonVideo video) {
        Integer watchedSeconds = request.watchedSeconds() == null ? progress.getWatchedSeconds() : request.watchedSeconds();
        Integer totalSeconds = request.totalSeconds() == null
                ? (progress.getTotalSeconds() == null ? video.getDurationSeconds() : progress.getTotalSeconds())
                : request.totalSeconds();
        Integer progressPercentage = request.progressPercentage();

        if (progressPercentage == null) {
            if (Boolean.TRUE.equals(request.completed())) {
                progressPercentage = COMPLETE_PROGRESS;
            } else if (watchedSeconds != null && totalSeconds != null && totalSeconds > 0) {
                progressPercentage = Math.min(
                        COMPLETE_PROGRESS,
                        Math.max(ZERO_PROGRESS, (int) Math.round(watchedSeconds * 100.0 / totalSeconds)));
            } else {
                progressPercentage = Objects.requireNonNullElse(progress.getProgressPercentage(), ZERO_PROGRESS);
            }
        }

        // watchedSeconds is the resume position and may move back on a rewatch, but the reported
        // progress never drops and a completed video stays completed (re-opening it must not undo
        // the completion or fire the completion notification again).
        progressPercentage = Math.max(
                progressPercentage,
                Objects.requireNonNullElse(progress.getProgressPercentage(), ZERO_PROGRESS));
        boolean completed = progress.isCompleted()
                || Boolean.TRUE.equals(request.completed())
                || progressPercentage >= COMPLETE_PROGRESS;
        if (completed) {
            progressPercentage = COMPLETE_PROGRESS;
        }
        progress.setWatchedSeconds(Objects.requireNonNullElse(watchedSeconds, ZERO_PROGRESS));
        progress.setTotalSeconds(Objects.requireNonNullElse(totalSeconds, ZERO_PROGRESS));
        progress.setProgressPercentage(progressPercentage);
        progress.setCompleted(completed);
        progress.setLastWatchedAt(LocalDateTime.now());
    }

    private static void validateRequest(VideoProgressRequest request) {
        if (request.watchedSeconds() != null && request.watchedSeconds() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Watched seconds must not be negative.");
        }
        if (request.totalSeconds() != null && request.totalSeconds() < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Total seconds must not be negative.");
        }
        if (request.watchedSeconds() != null
                && request.totalSeconds() != null
                && request.watchedSeconds() > request.totalSeconds()) {
            throw new ResponseStatusException(BAD_REQUEST, "Watched seconds must not exceed total seconds.");
        }
        if (request.progressPercentage() != null
                && (request.progressPercentage() < ZERO_PROGRESS || request.progressPercentage() > COMPLETE_PROGRESS)) {
            throw new ResponseStatusException(BAD_REQUEST, "Progress percentage must be between 0 and 100.");
        }
    }
}
