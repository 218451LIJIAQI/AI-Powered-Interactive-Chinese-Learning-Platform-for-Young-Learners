package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearningType;

/** Class cards, previews and the student's class pages (public catalogue, Find classes, My classes, class page). */
public final class ClassViews {

    private ClassViews() {
    }

    /** The teacher on a class card ("A class by Li Wei"); {@code verified} = approved by an admin. */
    public record TeacherView(Long id, String name, boolean verified, String bio) {
    }

    /** One class in the catalogue (public Classes, Find classes, featured classes). */
    public record ClassCard(
            Long id,
            String title,
            String description,
            String levelName,
            String topicName,
            LearningType learningType,
            TeacherView teacher,
            int unitCount,
            int lessonCount,
            int upcomingLiveSessionCount) {
    }

    /** Totals and filter options of the catalogue ("8 classes · 6 topics"). */
    public record CatalogueSummary(List<Option> topics, List<Option> levels) {
    }

    /** A filter option with how many published classes use it. */
    public record Option(Long id, String name) {
    }

    /** A lesson in a class preview: what it holds, not its content. */
    public record LessonPreview(Long id, String title, int activityCount, int quizCount) {
    }

    public record UnitPreview(Long id, String title, List<LessonPreview> lessons) {
    }

    /** Public class page and the preview of a class the student has not joined yet. */
    public record ClassPreview(
            ClassCard summary,
            String teacherNote,
            List<UnitPreview> units,
            List<LiveSessionViews.SessionBrief> liveSessions) {
    }

    /** A lesson row with the student's progress (class page, My classes notebook list). */
    public record LessonProgressRow(
            Long id,
            String title,
            int number,
            String status,
            int percent,
            Integer bestQuizPercent,
            int activityCount,
            boolean available) {
    }

    public record UnitProgress(Long id, String title, int lessonCount, int completedCount, List<LessonProgressRow> lessons) {
    }

    /** The lesson to continue with and where the student is in it. */
    public record NextStep(
            Long lessonId,
            String lessonTitle,
            int lessonNumber,
            int activitiesDone,
            int activityCount,
            boolean quizDone) {
    }

    /** One joined class on My classes. */
    public record MyClassCard(
            ClassCard summary,
            LocalDateTime joinedAt,
            int completedLessons,
            int progressPercent,
            List<LessonProgressRow> lessons,
            NextStep nextStep,
            LiveSessionViews.SessionBrief nextLiveSession,
            boolean archived) {
    }

    public record MyClasses(
            List<MyClassCard> classes,
            LiveSessionViews.SessionBrief liveToday,
            long moreClassCount,
            List<TeacherView> moreTeachers,
            long upcomingLiveSessionCount) {
    }

    /**
     * The student's class page. Before joining only {@code summary}, {@code teacherNote}, {@code preview} and
     * {@code liveSessions} are filled; after joining the progress, units and tab counts are too.
     */
    public record StudentClass(
            boolean joined,
            boolean archived,
            ClassCard summary,
            String teacherNote,
            List<UnitPreview> preview,
            int progressPercent,
            int completedLessons,
            int lessonCount,
            NextStep nextStep,
            List<UnitProgress> units,
            List<LiveSessionViews.SessionBrief> liveSessions,
            long quizAttemptCount,
            long feedbackCount) {
    }

    /** One submitted lesson-quiz attempt (Quiz results tab, teacher's student detail). */
    public record AttemptRow(
            Long attemptId,
            Long quizId,
            String quizTitle,
            String lessonTitle,
            LocalDateTime completedAt,
            int score,
            int maxScore,
            int percent) {
    }

    public record QuizResults(Integer averagePercent, List<AttemptRow> attempts) {
    }

    /** Teacher feedback as the student sees it in the class Feedback tab. */
    public record FeedbackItem(
            Long id,
            String teacherName,
            String type,
            String about,
            String lessonTitle,
            String message,
            LocalDateTime createdAt,
            LocalDateTime editedAt) {
    }
}
