package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

/** The teacher's students (roster, student detail), feedback history and teaching dashboard. */
public final class ClassStudentViews {

    private ClassStudentViews() {
    }

    /**
     * One student in the roster. Progress is for the class they joined most recently, the quiz average over all the
     * teacher's classes they joined; {@code needsSupport} is a quiz average under 60 % or no activity for 7 days.
     */
    public record RosterRow(
            Long studentId,
            String name,
            int xpLevel,
            int progressPercent,
            Integer quizAverage,
            boolean needsSupport,
            LocalDateTime lastActiveAt) {
    }

    public record Roster(List<RosterRow> students, long total, long needsSupportCount) {
    }

    public record ClassRef(Long id, String title) {
    }

    public record LessonProgress(
            Long lessonId,
            String title,
            String status,
            int percent,
            Boolean videoWatched,
            Integer bestQuizPercent) {
    }

    /** A topic the student gets wrong: the question skill within one quiz, under 60 % correct. */
    public record WeakArea(
            String skill,
            Long quizId,
            String quizTitle,
            int correct,
            int total,
            int percent,
            LocalDateTime lastSeenAt,
            List<String> examples) {
    }

    public record FeedbackTarget(String kind, Long id, String label, Integer percent, LocalDateTime at) {
    }

    public record StudentDetail(
            Long studentId,
            String name,
            int xpLevel,
            List<ClassRef> classes,
            Long classId,
            String classTitle,
            LocalDateTime lastActiveAt,
            boolean needsSupport,
            int progressPercent,
            int lessonsCompleted,
            int lessonCount,
            int mastered,
            Integer quizAverage,
            List<LessonProgress> lessons,
            List<ClassViews.AttemptRow> quizResults,
            List<WeakArea> weakAreas,
            List<FeedbackRow> feedback,
            List<FeedbackTarget> feedbackTargets) {
    }

    /** Feedback a teacher sent (Feedback history, student detail). {@code about}: QUIZ_ATTEMPT, LESSON or GENERAL. */
    public record FeedbackRow(
            Long id,
            String studentName,
            String classTitle,
            String type,
            String about,
            String lessonTitle,
            Integer attemptPercent,
            String message,
            LocalDateTime createdAt,
            LocalDateTime editedAt) {
    }

    // ----- teaching dashboard -----

    /**
     * One To do entry. {@code kind}: LESSON_CHANGES_REQUESTED, REVISION_CHANGES_REQUESTED, CLASS_CHANGES_REQUESTED,
     * DETAILS_CHANGES_REQUESTED (need the teacher), REVISION_IN_REVIEW, LESSON_IN_REVIEW, CLASS_IN_REVIEW,
     * DETAILS_IN_REVIEW, NEXT_LIVE_SESSION (to keep an eye on).
     */
    public record TodoItem(
            String kind,
            boolean needsYou,
            Long classId,
            Long lessonId,
            Long sessionId,
            String title,
            String note,
            String noteBy,
            LocalDateTime at,
            String detail) {
    }

    public record Dashboard(
            List<TodoItem> todo,
            int studentCount,
            int needsSupportCount,
            int publishedClasses,
            int publishedLessons,
            int inReviewCount,
            int upcomingSessionCount,
            int upcomingBookings,
            Integer quizAverage,
            List<RosterRow> studentsNeedingSupport,
            List<LiveSessionViews.TeacherSession> upcomingSessions) {
    }
}
