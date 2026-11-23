package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Class cards, previews and the learner's class pages (public catalogue, Find classes, My classes, class page). Classes
 * are made by the MandarinSpark team: no view names a teacher.
 */
public final class ClassViews {

    private ClassViews() {
    }

    /** One class in the catalogue (public Classes, Find classes, featured classes). */
    public record ClassCard(
            Long id,
            String title,
            String description,
            String topicName,
            Integer bestAfterUnit,
            int lessonCount) {
    }

    public record LessonPreview(Long id, String title) {
    }

    public record UnitPreview(Long id, String title, List<LessonPreview> lessons) {
    }

    /** The public class page. */
    public record ClassPreview(ClassCard summary, List<UnitPreview> units) {
    }

    /** A lesson row with the learner's progress (class page, My classes). */
    public record LessonProgressRow(Long id, String title, String status, boolean available) {
    }

    public record UnitProgress(Long id, String title, int lessonCount, int completedCount, List<LessonProgressRow> lessons) {
    }

    /**
     * The lesson to continue with and where the learner is in it: its steps (activities and quizzes) done of all of
     * them ("3 of 4 steps done").
     */
    public record NextStep(Long lessonId, String lessonTitle, int lessonNumber, int stepsDone, int stepsTotal) {
    }

    /**
     * One joined class on My classes; an {@code archived} class (since {@code archivedAt}, else null) is read-only:
     * its lessons and results only.
     */
    public record MyClassCard(
            ClassCard summary,
            int completedLessons,
            int progressPercent,
            List<LessonProgressRow> lessons,
            NextStep nextStep,
            boolean archived,
            LocalDateTime archivedAt) {
    }

    /** My classes, with the number of catalogue classes the learner has not joined (the Find classes tab). */
    public record MyClasses(List<MyClassCard> classes, long moreClassCount) {
    }

    /** Account-specific additions to Find classes. The catalogue itself supplies the number still available. */
    public record CatalogueMembership(long joinedCount, List<Long> plannedClassIds) {
    }

    /**
     * The learner's class page. Before joining only {@code summary} and {@code preview} are filled; after joining the
     * progress and units are too. An {@code archived} class (since {@code archivedAt}, else null) is read-only: its
     * lessons and results stay open.
     */
    public record StudentClass(
            boolean joined,
            boolean archived,
            LocalDateTime archivedAt,
            ClassCard summary,
            List<UnitPreview> preview,
            int completedLessons,
            NextStep nextStep,
            List<UnitProgress> units) {
    }
}
