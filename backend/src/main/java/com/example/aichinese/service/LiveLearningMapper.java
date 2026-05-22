package com.example.aichinese.service;

import java.time.Duration;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.learning.LiveClassEnrolmentResponse;
import com.example.aichinese.dto.learning.LiveClassLessonSummaryResponse;
import com.example.aichinese.dto.learning.LiveClassPackageSummaryResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.LiveClassTeacherSummaryResponse;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.VideoProgress;

@Component
public class LiveLearningMapper {

    public LiveClassResponse toLiveClassResponse(
            LiveClass liveClass,
            long enrolledCount,
            boolean enrolled,
            boolean canEnrol,
            boolean accessAllowed,
            boolean canJoin,
            boolean showMeetingLink) {
        Integer durationMinutes = null;
        if (liveClass.getScheduledStartAt() != null && liveClass.getScheduledEndAt() != null) {
            durationMinutes = Math.toIntExact(Duration.between(
                    liveClass.getScheduledStartAt(),
                    liveClass.getScheduledEndAt()).toMinutes());
        }

        return new LiveClassResponse(
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getDescription(),
                liveClass.getMeetingPlatform() == null ? null : liveClass.getMeetingPlatform().name(),
                liveClass.getLiveClassStatus() == null ? null : liveClass.getLiveClassStatus().name(),
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt(),
                durationMinutes,
                liveClass.getMaxParticipants(),
                enrolledCount,
                enrolled,
                canEnrol,
                accessAllowed,
                canJoin,
                showMeetingLink,
                showMeetingLink ? liveClass.getMeetingLink() : null,
                liveClass.getTeacher() == null || liveClass.getTeacher().getUser() == null
                        ? null
                        : new LiveClassTeacherSummaryResponse(
                                liveClass.getTeacher().getId(),
                                liveClass.getTeacher().getUser().getId(),
                                liveClass.getTeacher().getUser().getDisplayName()),
                liveClass.getLesson() == null
                        ? null
                        : new LiveClassLessonSummaryResponse(
                                liveClass.getLesson().getId(),
                                liveClass.getLesson().getTitle(),
                                liveClass.getLesson().getLearningType() == null
                                        ? null
                                        : liveClass.getLesson().getLearningType().name(),
                                liveClass.getLesson().getAccessType() == null
                                        ? null
                                        : liveClass.getLesson().getAccessType().name()),
                liveClass.getCoursePackage() == null
                        ? null
                        : new LiveClassPackageSummaryResponse(
                                liveClass.getCoursePackage().getId(),
                                liveClass.getCoursePackage().getTitle(),
                                liveClass.getCoursePackage().getLearningType() == null
                                        ? null
                                        : liveClass.getCoursePackage().getLearningType().name(),
                                liveClass.getCoursePackage().getAccessType() == null
                                        ? null
                                        : liveClass.getCoursePackage().getAccessType().name()),
                liveClass.getCreatedAt(),
                liveClass.getUpdatedAt());
    }

    public LiveClassEnrolmentResponse toEnrolmentResponse(LiveClassEnrolment enrolment) {
        return new LiveClassEnrolmentResponse(
                enrolment.getId(),
                enrolment.getLiveClass().getId(),
                enrolment.getStudent().getId(),
                enrolment.getStudent().getUser() == null
                        ? enrolment.getStudent().getDisplayName()
                        : enrolment.getStudent().getUser().getDisplayName(),
                enrolment.getStudent().getUser() == null ? null : enrolment.getStudent().getUser().getEmail(),
                enrolment.getEnrolmentStatus() == null ? null : enrolment.getEnrolmentStatus().name(),
                enrolment.getEnrolledAt(),
                enrolment.getCancelledAt(),
                enrolment.getAttendedAt());
    }

    public RecordedLessonVideoResponse toRecordedVideoResponse(RecordedLessonVideo video, String streamUrl, String externalUrl) {
        return new RecordedLessonVideoResponse(
                video.getId(),
                video.getLesson().getId(),
                video.getCoursePackage() == null ? null : video.getCoursePackage().getId(),
                video.getTitle(),
                video.getDescription(),
                video.getSourceType() == null ? null : video.getSourceType().name(),
                externalUrl,
                streamUrl,
                video.getDurationSeconds(),
                video.getSortOrder(),
                video.isActive(),
                video.getCreatedAt(),
                video.getUpdatedAt());
    }

    public VideoProgressResponse toVideoProgressResponse(VideoProgress progress) {
        return new VideoProgressResponse(
                progress.getId(),
                progress.getLesson().getId(),
                progress.getRecordedVideo() == null ? null : progress.getRecordedVideo().getId(),
                progress.getWatchedSeconds(),
                progress.getTotalSeconds(),
                progress.getProgressPercentage(),
                progress.isCompleted(),
                progress.getLastWatchedAt(),
                progress.getUpdatedAt());
    }
}
