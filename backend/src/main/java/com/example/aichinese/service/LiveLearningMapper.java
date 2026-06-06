package com.example.aichinese.service;

import java.time.Duration;
import java.time.LocalDateTime;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.learning.LiveClassEnrolmentResponse;
import com.example.aichinese.dto.learning.LiveClassLessonSummaryResponse;
import com.example.aichinese.dto.learning.LiveClassPackageSummaryResponse;
import com.example.aichinese.dto.learning.LiveClassResponse;
import com.example.aichinese.dto.learning.LiveClassTeacherSummaryResponse;
import com.example.aichinese.dto.learning.RecordedLessonVideoResponse;
import com.example.aichinese.dto.learning.VideoProgressResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.LiveClassEnrolment;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
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
        return new LiveClassResponse(
                liveClass.getId(),
                liveClass.getTitle(),
                liveClass.getDescription(),
                liveClass.getMeetingPlatform() == null ? null : liveClass.getMeetingPlatform().name(),
                liveClass.getLiveClassStatus() == null ? null : liveClass.getLiveClassStatus().name(),
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt(),
                liveClass.getTimeZone(),
                durationMinutes(liveClass),
                liveClass.getMaxParticipants(),
                enrolledCount,
                enrolled,
                canEnrol,
                accessAllowed,
                canJoin,
                showMeetingLink,
                showMeetingLink ? liveClass.getMeetingLink() : null,
                teacherSummary(liveClass.getTeacher()),
                lessonSummary(liveClass.getLesson()),
                packageSummary(liveClass.getCoursePackage()),
                liveClass.getCreatedAt(),
                liveClass.getUpdatedAt());
    }

    public LiveClassEnrolmentResponse toEnrolmentResponse(LiveClassEnrolment enrolment) {
        return new LiveClassEnrolmentResponse(
                enrolment.getId(),
                enrolment.getLiveClass().getId(),
                enrolment.getStudent().getId(),
                studentDisplayName(enrolment.getStudent()),
                studentEmail(enrolment.getStudent()),
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
        Long progressId = requirePersistedId(progress, "Video progress must be persisted before it can be mapped.");
        Long lessonId = requirePersistedId(
                progress.getLesson(),
                "Video progress must be attached to a persisted lesson before it can be mapped.");
        Long recordedVideoId = progress.getRecordedVideo() == null
                ? null
                : requirePersistedId(
                        progress.getRecordedVideo(),
                        "Video progress must reference a persisted recorded video when the relation is present.");
        LocalDateTime updatedAt = requireUpdatedAt(progress);

        return new VideoProgressResponse(
                progressId,
                lessonId,
                recordedVideoId,
                progress.getWatchedSeconds(),
                progress.getTotalSeconds(),
                progress.getProgressPercentage(),
                progress.isCompleted(),
                progress.getLastWatchedAt(),
                updatedAt);
    }

    private static Integer clampDurationMinutes(long minutes) {
        if (minutes > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (minutes < 0) {
            return 0;
        }
        return (int) minutes;
    }

    private static Integer durationMinutes(LiveClass liveClass) {
        if (liveClass.getScheduledStartAt() == null || liveClass.getScheduledEndAt() == null) {
            return null;
        }
        return clampDurationMinutes(Duration.between(
                liveClass.getScheduledStartAt(),
                liveClass.getScheduledEndAt()).toMinutes());
    }

    private static LiveClassTeacherSummaryResponse teacherSummary(TeacherProfile teacher) {
        if (teacher == null || teacher.getUser() == null) {
            return null;
        }
        return new LiveClassTeacherSummaryResponse(
                teacher.getId(),
                teacher.getUser().getId(),
                teacher.getUser().getDisplayName());
    }

    private static LiveClassLessonSummaryResponse lessonSummary(Lesson lesson) {
        if (lesson == null) {
            return null;
        }
        return new LiveClassLessonSummaryResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getLearningType() == null ? null : lesson.getLearningType().name(),
                lesson.getAccessType() == null ? null : lesson.getAccessType().name());
    }

    private static LiveClassPackageSummaryResponse packageSummary(CoursePackage coursePackage) {
        if (coursePackage == null) {
            return null;
        }
        return new LiveClassPackageSummaryResponse(
                coursePackage.getId(),
                coursePackage.getTitle(),
                coursePackage.getLearningType() == null
                        ? null
                        : coursePackage.getLearningType().name(),
                coursePackage.getAccessType() == null
                        ? null
                        : coursePackage.getAccessType().name());
    }

    private static String studentDisplayName(StudentProfile student) {
        return student.getUser() == null ? student.getDisplayName() : student.getUser().getDisplayName();
    }

    private static String studentEmail(StudentProfile student) {
        return student.getUser() == null ? null : student.getUser().getEmail();
    }

    private static Long requirePersistedId(VideoProgress progress, String message) {
        if (progress == null || progress.getId() == null) {
            throw new IllegalStateException(message);
        }
        return progress.getId();
    }

    private static Long requirePersistedId(Lesson lesson, String message) {
        if (lesson == null || lesson.getId() == null) {
            throw new IllegalStateException(message);
        }
        return lesson.getId();
    }

    private static Long requirePersistedId(RecordedLessonVideo video, String message) {
        if (video == null || video.getId() == null) {
            throw new IllegalStateException(message);
        }
        return video.getId();
    }

    private static LocalDateTime requireUpdatedAt(VideoProgress progress) {
        if (progress.getUpdatedAt() == null) {
            throw new IllegalStateException("Video progress must carry an updated timestamp before it can be mapped.");
        }
        return progress.getUpdatedAt();
    }
}
