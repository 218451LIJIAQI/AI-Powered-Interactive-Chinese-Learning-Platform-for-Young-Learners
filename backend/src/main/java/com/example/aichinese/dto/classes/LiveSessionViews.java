package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Live sessions of classes. Start and end are instants with the session's offset, so the browser shows them in the
 * viewer's own time. {@code status} is the displayed state: SCHEDULED, RESCHEDULED, FULL, CANCELLED or COMPLETED.
 */
public final class LiveSessionViews {

    private LiveSessionViews() {
    }

    /** A session in a short list (class page, lesson page, teacher dashboard). */
    public record SessionBrief(
            Long id,
            String title,
            Long classId,
            String classTitle,
            Long lessonId,
            String lessonTitle,
            String teacherName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            String platform,
            String status,
            boolean booked,
            int bookedCount,
            Integer capacity) {
    }

    /** A session on the student's Live sessions page. */
    public record StudentSession(
            Long id,
            String title,
            Long classId,
            String classTitle,
            Long lessonId,
            String lessonTitle,
            String teacherName,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            OffsetDateTime joinOpensAt,
            String platform,
            String status,
            OffsetDateTime rescheduledFrom,
            LocalDateTime cancelledAt,
            Integer capacity,
            int bookedCount,
            Integer seatsLeft,
            boolean booked,
            boolean bookingCancelled,
            boolean attended,
            boolean canBook,
            boolean canJoin,
            String recordingUrl) {
    }

    public record StudentSessions(List<StudentSession> upcoming, List<StudentSession> past, long bookedCount) {
    }

    /** The meeting link a booked student may open now (attendance is recorded). */
    public record JoinLink(String meetingLink) {
    }

    /** A session on the teacher's Live sessions page and dashboard. */
    public record TeacherSession(
            Long id,
            String title,
            Long classId,
            String classTitle,
            Long lessonId,
            String lessonTitle,
            OffsetDateTime startsAt,
            OffsetDateTime endsAt,
            LocalDateTime localStart,
            LocalDateTime localEnd,
            String timeZone,
            String platform,
            String meetingLink,
            String status,
            OffsetDateTime rescheduledFrom,
            LocalDateTime cancelledAt,
            Integer capacity,
            int bookedCount,
            int attendedCount,
            String recordingUrl) {
    }

    /** A booked student in the session details (attendance once it has ended). */
    public record BookedStudent(
            Long studentId,
            String name,
            int xpLevel,
            Integer classProgressPercent,
            String attendance) {
    }

    public record TeacherSessionDetail(TeacherSession session, OffsetDateTime joinOpensAt, List<BookedStudent> students) {
    }

    public record TeacherSessions(List<TeacherSession> upcoming, List<TeacherSession> past) {
    }

    /** Published classes (and their lessons) a session can be scheduled for. */
    public record ScheduleOptions(List<ClassOption> classes) {
    }

    public record ClassOption(Long id, String title, List<LessonOption> lessons) {
    }

    public record LessonOption(Long id, String title) {
    }
}
