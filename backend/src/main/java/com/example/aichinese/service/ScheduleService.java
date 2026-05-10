package com.example.aichinese.service;

import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learning.AdminScheduleResponse;
import com.example.aichinese.dto.learning.ScheduleItemResponse;
import com.example.aichinese.dto.learning.StudentScheduleResponse;
import com.example.aichinese.dto.learning.TeacherScheduleResponse;
import com.example.aichinese.entity.LearningTask;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.VideoProgress;
import com.example.aichinese.entity.enums.LiveClassEnrolmentStatus;
import com.example.aichinese.repository.LearningTaskRepository;
import com.example.aichinese.repository.LiveClassEnrolmentRepository;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.VideoProgressRepository;
import com.example.aichinese.security.SecurityUserDetails;

@Service
public class ScheduleService {

    private final LiveClassRepository liveClassRepository;
    private final LiveClassEnrolmentRepository liveClassEnrolmentRepository;
    private final LearningTaskRepository learningTaskRepository;
    private final VideoProgressRepository videoProgressRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final LiveLearningAccessService liveLearningAccessService;

    public ScheduleService(
            LiveClassRepository liveClassRepository,
            LiveClassEnrolmentRepository liveClassEnrolmentRepository,
            LearningTaskRepository learningTaskRepository,
            VideoProgressRepository videoProgressRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            LiveLearningAccessService liveLearningAccessService) {
        this.liveClassRepository = liveClassRepository;
        this.liveClassEnrolmentRepository = liveClassEnrolmentRepository;
        this.learningTaskRepository = learningTaskRepository;
        this.videoProgressRepository = videoProgressRepository;
        this.recordedLessonVideoRepository = recordedLessonVideoRepository;
        this.liveLearningAccessService = liveLearningAccessService;
    }

    @Transactional(readOnly = true)
    public StudentScheduleResponse getStudentSchedule(SecurityUserDetails currentUser) {
        StudentProfile student = liveLearningAccessService.requireStudent(currentUser);

        List<ScheduleItemResponse> upcomingLiveClasses = liveClassEnrolmentRepository
                .findByStudentIdAndEnrolmentStatusOrderByLiveClassScheduledStartAtAsc(
                        student.getId(),
                        LiveClassEnrolmentStatus.ENROLLED)
                .stream()
                .map(this::toStudentLiveClassItem)
                .sorted(Comparator.comparing(ScheduleItemResponse::startTime, Comparator.nullsLast(Comparator.naturalOrder())))
                .toList();

        List<ScheduleItemResponse> learningTasks = learningTaskRepository.findByStudentIdOrderByDueAtAscCreatedAtAsc(student.getId())
                .stream()
                .map(this::toLearningTaskItem)
                .toList();

        List<ScheduleItemResponse> continueWatching = videoProgressRepository.findByStudentIdAndCompletedFalseOrderByLastWatchedAtDesc(student.getId())
                .stream()
                .map(this::toContinueWatchingItem)
                .toList();

        return new StudentScheduleResponse(upcomingLiveClasses, learningTasks, continueWatching);
    }

    @Transactional(readOnly = true)
    public TeacherScheduleResponse getTeacherSchedule(SecurityUserDetails currentUser) {
        TeacherProfile teacher = liveLearningAccessService.requireApprovedTeacher(currentUser);

        List<ScheduleItemResponse> liveClasses = liveClassRepository.findByTeacherUserIdOrderByScheduledStartAtAsc(currentUser.getUserId()).stream()
                .map(this::toTeacherLiveClassItem)
                .toList();

        List<ScheduleItemResponse> recordedUpdates = recordedLessonVideoRepository.findAll().stream()
                .filter(video -> video.isActive() && video.getLesson() != null && video.getLesson().getTeacher() != null
                        && teacher.getId().equals(video.getLesson().getTeacher().getId()))
                .map(this::toRecordedVideoItem)
                .toList();

        return new TeacherScheduleResponse(liveClasses, recordedUpdates);
    }

    @Transactional(readOnly = true)
    public AdminScheduleResponse getAdminSchedule(SecurityUserDetails currentUser) {
        List<ScheduleItemResponse> items = liveClassRepository.findAll().stream()
                .map(this::toAdminLiveClassItem)
                .toList();
        return new AdminScheduleResponse(items);
    }

    private ScheduleItemResponse toStudentLiveClassItem(LiveClassEnrolment enrolment) {
        LiveClass liveClass = enrolment.getLiveClass();
        return new ScheduleItemResponse(
                "LIVE_CLASS",
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getDescription(),
                liveClass.getLiveClassStatus() == null ? null : liveClass.getLiveClassStatus().name(),
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt(),
                liveClass.getMeetingPlatform() == null ? null : liveClass.getMeetingPlatform().name(),
                "/student/live-classes/" + liveClass.getId(),
                true);
    }

    private ScheduleItemResponse toLearningTaskItem(LearningTask task) {
        return new ScheduleItemResponse(
                "LEARNING_TASK",
                task.getId(),
                task.getTitle(),
                task.getLesson() == null ? null : task.getLesson().getTitle(),
                task.getTaskStatus(),
                task.getDueAt(),
                null,
                task.getLesson() == null ? null : task.getLesson().getTitle(),
                task.getLesson() == null ? null : "/student/lessons/" + task.getLesson().getId(),
                task.getLesson() != null);
    }

    private ScheduleItemResponse toContinueWatchingItem(VideoProgress progress) {
        RecordedLessonVideo video = progress.getRecordedVideo();
        return new ScheduleItemResponse(
                "RECORDED_VIDEO",
                video == null ? progress.getId() : video.getId(),
                video == null ? progress.getLesson().getTitle() : video.getTitle(),
                video == null ? progress.getLesson().getSummary() : video.getDescription(),
                progress.isCompleted() ? "COMPLETED" : "IN_PROGRESS",
                progress.getLastWatchedAt(),
                null,
                progress.getProgressPercentage() + "%",
                video == null ? null : "/student/recorded-lessons/" + video.getId() + "?lessonId=" + progress.getLesson().getId(),
                true);
    }

    private ScheduleItemResponse toTeacherLiveClassItem(LiveClass liveClass) {
        return new ScheduleItemResponse(
                "LIVE_CLASS",
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getDescription(),
                liveClass.getLiveClassStatus() == null ? null : liveClass.getLiveClassStatus().name(),
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt(),
                liveClass.getLesson() == null ? null : liveClass.getLesson().getTitle(),
                "/teacher/live-classes",
                true);
    }

    private ScheduleItemResponse toRecordedVideoItem(RecordedLessonVideo video) {
        return new ScheduleItemResponse(
                "RECORDED_VIDEO",
                video.getId(),
                video.getTitle(),
                video.getDescription(),
                video.isActive() ? "ACTIVE" : "ARCHIVED",
                video.getCreatedAt(),
                null,
                video.getLesson() == null ? null : video.getLesson().getTitle(),
                "/teacher/recorded-lessons",
                true);
    }

    private ScheduleItemResponse toAdminLiveClassItem(LiveClass liveClass) {
        return new ScheduleItemResponse(
                "LIVE_CLASS",
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getDescription(),
                liveClass.getLiveClassStatus() == null ? null : liveClass.getLiveClassStatus().name(),
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt(),
                liveClass.getTeacher() == null || liveClass.getTeacher().getUser() == null
                        ? null
                        : liveClass.getTeacher().getUser().getDisplayName(),
                "/admin/live-classes",
                false);
    }
}
