package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learning.LessonVideoProgressSummaryResponse;
import com.example.aichinese.dto.learning.VideoProgressRequest;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

@Service
public class VideoProgressService {

    private final VideoProgressRepository videoProgressRepository;
    private final LiveLearningAccessService liveLearningAccessService;
    private final LiveLearningMapper liveLearningMapper;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public VideoProgressService(
            VideoProgressRepository videoProgressRepository,
            LiveLearningAccessService liveLearningAccessService,
            LiveLearningMapper liveLearningMapper,
            NotificationService notificationService,
            AuditLogService auditLogService) {
        this.videoProgressRepository = videoProgressRepository;
        this.liveLearningAccessService = liveLearningAccessService;
        this.liveLearningMapper = liveLearningMapper;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @Transactional(readOnly = true)
    public VideoProgressResponse getProgress(SecurityUserDetails currentUser, Long videoId) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);
        RecordedLessonVideo video = liveLearningAccessService.requireAccessibleRecordedVideo(
                currentUser,
                liveLearningAccessService.requireActiveRecordedVideo(videoId).getLesson().getId(),
                videoId);
        VideoProgress progress = videoProgressRepository.findByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .orElseGet(() -> createDefaultProgress(student, video));
        return liveLearningMapper.toVideoProgressResponse(progress);
    }

    @Transactional
    public VideoProgressResponse upsertProgress(SecurityUserDetails currentUser, Long videoId, VideoProgressRequest request) {
        StudentProfile student = liveLearningAccessService.requireLockedStudent(currentUser);
        RecordedLessonVideo video = liveLearningAccessService.requireAccessibleRecordedVideo(
                currentUser,
                liveLearningAccessService.requireActiveRecordedVideo(videoId).getLesson().getId(),
                videoId);
        validateRequest(request);

        VideoProgress progress = videoProgressRepository.findLockedByStudentIdAndRecordedVideoId(student.getId(), video.getId())
                .orElseGet(() -> createDefaultProgress(student, video));
        // Capture the prior completion flag BEFORE applyProgress mutates it.
        // The completion notification + audit row should fire exactly once,
        // on the transition from incomplete to complete. Without this guard
        // a student who refreshes the player page after finishing a video
        // would emit a fresh "completed" notification + audit row every
        // time the player POSTs another progress heartbeat, polluting the
        // teacher's notification feed and the audit log. Same bug family
        // as PaymentService.markPaymentPaid (Wave 5.1).
        boolean wasAlreadyCompleted = progress.isCompleted();
        applyProgress(progress, request, video);
        VideoProgress saved = videoProgressRepository.save(progress);

        if (saved.isCompleted() && !wasAlreadyCompleted) {
            notificationService.safeCreateNotification(
                    video.getLesson().getTeacher().getUser(),
                    NotificationType.PROGRESS_UPDATE,
                    "Recorded video completed",
                    student.getUser().getDisplayName() + " completed " + video.getTitle() + ".",
                    "RECORDED_VIDEO",
                    video.getId(),
                    "/teacher/classes",
                    student.getUser());
            auditLogService.record(
                    student.getUser(),
                    "STUDENT_RECORDED_VIDEO_COMPLETE",
                    "RecordedLessonVideo",
                    video.getId(),
                    "Student completed a recorded video.");
        }

        return liveLearningMapper.toVideoProgressResponse(saved);
    }

    @Transactional
    public VideoProgressResponse completeVideo(SecurityUserDetails currentUser, Long videoId) {
        RecordedLessonVideo video = liveLearningAccessService.requireActiveRecordedVideo(videoId);
        Integer totalSeconds = video.getDurationSeconds() == null ? 0 : video.getDurationSeconds();
        return upsertProgress(currentUser, videoId, new VideoProgressRequest(totalSeconds, totalSeconds, 100, true));
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
        List<VideoProgress> accessibleProgress = videoProgressRepository.findByLessonTeacherUserIdAndLessonIdOrderByLastWatchedAtDesc(
                currentUser.getUserId(),
                lessonId).stream()
                .filter(this::hasCurrentProgressAccess)
                .toList();
        int startIndex = PaginationUtils.startIndex(safePage, safeSize, accessibleProgress.size());
        int endIndex = PaginationUtils.endIndex(startIndex, safeSize, accessibleProgress.size());
        return new PageResponse<>(
                accessibleProgress.subList(startIndex, endIndex).stream()
                        .map(this::toLessonVideoProgressSummary)
                        .toList(),
                safePage,
                safeSize,
                accessibleProgress.size(),
                PaginationUtils.totalPages(accessibleProgress.size(), safeSize));
    }

    private boolean hasCurrentProgressAccess(VideoProgress progress) {
        if (progress.getStudent() == null) {
            return false;
        }
        boolean lessonAccess = progress.getLesson() == null
                || liveLearningAccessService.hasStudentLessonAccess(progress.getStudent(), progress.getLesson());
        boolean packageAccess = progress.getRecordedVideo() == null
                || progress.getRecordedVideo().getCoursePackage() == null
                || liveLearningAccessService.hasStudentPackageAccess(progress.getStudent(), progress.getRecordedVideo().getCoursePackage());
        return lessonAccess && packageAccess;
    }

    private LessonVideoProgressSummaryResponse toLessonVideoProgressSummary(VideoProgress progress) {
        return new LessonVideoProgressSummaryResponse(
                progress.getStudent().getId(),
                progress.getStudent().getUser() == null
                        ? progress.getStudent().getDisplayName()
                        : progress.getStudent().getUser().getDisplayName(),
                progress.getStudent().getUser() == null ? null : progress.getStudent().getUser().getEmail(),
                progress.getRecordedVideo() == null ? null : progress.getRecordedVideo().getId(),
                progress.getRecordedVideo() == null ? null : progress.getRecordedVideo().getTitle(),
                progress.getProgressPercentage(),
                progress.isCompleted(),
                progress.getWatchedSeconds(),
                progress.getTotalSeconds(),
                progress.getLastWatchedAt() == null ? null : progress.getLastWatchedAt().toString());
    }

    private VideoProgress createDefaultProgress(StudentProfile student, RecordedLessonVideo video) {
        VideoProgress progress = new VideoProgress();
        progress.setStudent(student);
        progress.setLesson(video.getLesson());
        progress.setRecordedVideo(video);
        progress.setWatchedSeconds(0);
        progress.setTotalSeconds(video.getDurationSeconds() == null ? 0 : video.getDurationSeconds());
        progress.setProgressPercentage(0);
        progress.setCompleted(false);
        progress.setLastWatchedAt(null);
        return progress;
    }

    private void applyProgress(VideoProgress progress, VideoProgressRequest request, RecordedLessonVideo video) {
        Integer watchedSeconds = request.watchedSeconds() == null ? progress.getWatchedSeconds() : request.watchedSeconds();
        Integer totalSeconds = request.totalSeconds() == null
                ? (progress.getTotalSeconds() == null ? video.getDurationSeconds() : progress.getTotalSeconds())
                : request.totalSeconds();
        Integer progressPercentage = request.progressPercentage();

        if (watchedSeconds != null && watchedSeconds < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Watched seconds must not be negative.");
        }
        if (totalSeconds != null && totalSeconds < 0) {
            throw new ResponseStatusException(BAD_REQUEST, "Total seconds must not be negative.");
        }
        if (watchedSeconds != null && totalSeconds != null && watchedSeconds > totalSeconds) {
            throw new ResponseStatusException(BAD_REQUEST, "Watched seconds must not exceed total seconds.");
        }

        if (progressPercentage == null) {
            if (Boolean.TRUE.equals(request.completed())) {
                progressPercentage = 100;
            } else if (watchedSeconds != null && totalSeconds != null && totalSeconds > 0) {
                progressPercentage = Math.min(100, Math.max(0, (int) Math.round((watchedSeconds * 100.0) / totalSeconds)));
            } else {
                progressPercentage = progress.getProgressPercentage() == null ? 0 : progress.getProgressPercentage();
            }
        }

        if (progressPercentage < 0 || progressPercentage > 100) {
            throw new ResponseStatusException(BAD_REQUEST, "Progress percentage must be between 0 and 100.");
        }

        boolean completed = Boolean.TRUE.equals(request.completed()) || progressPercentage >= 100;
        progress.setWatchedSeconds(watchedSeconds == null ? 0 : watchedSeconds);
        progress.setTotalSeconds(totalSeconds == null ? 0 : totalSeconds);
        progress.setProgressPercentage(completed ? 100 : progressPercentage);
        progress.setCompleted(completed);
        progress.setLastWatchedAt(LocalDateTime.now());
    }

    private void validateRequest(VideoProgressRequest request) {
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
                && (request.progressPercentage() < 0 || request.progressPercentage() > 100)) {
            throw new ResponseStatusException(BAD_REQUEST, "Progress percentage must be between 0 and 100.");
        }
    }
}
