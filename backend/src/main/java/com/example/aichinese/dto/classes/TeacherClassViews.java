package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearningType;

/**
 * The teacher's classes: the class list, the class page (units and lessons, details, history) and the lesson
 * editor. Statuses are the stored content statuses (DRAFT, SUBMITTED, PUBLISHED, REJECTED = changes requested,
 * ARCHIVED); {@code revisionStatus} is the state of a pending change to a published item (DRAFT, SUBMITTED,
 * REJECTED) or null.
 */
public final class TeacherClassViews {

    private TeacherClassViews() {
    }

    public record ClassListItem(
            Long id,
            String title,
            String status,
            String revisionStatus,
            String levelName,
            String topicName,
            LearningType learningType,
            int unitCount,
            int lessonCount,
            int publishedLessons,
            int lessonsInReview,
            int draftLessons,
            int lessonsWithChangesRequested,
            int revisionsInReview,
            int revisionsWithChangesRequested,
            int studentCount,
            LocalDateTime updatedAt) {
    }

    /** One lesson row on the class page. */
    public record LessonRow(
            Long id,
            String title,
            String status,
            String revisionStatus,
            String reviewNote,
            String reviewNoteBy,
            int activityCount,
            int questionCount,
            LocalDateTime updatedAt) {
    }

    public record Unit(Long id, String title, boolean archived, List<LessonRow> lessons) {
    }

    /** The pending change to the details of a published class. */
    public record DetailsRevision(
            String status,
            String title,
            String description,
            Long levelId,
            Long topicId,
            LearningType learningType,
            String rejectionReason) {
    }

    public record ClassPage(
            Long id,
            String title,
            String description,
            String teacherNote,
            Long levelId,
            String levelName,
            Long topicId,
            String topicName,
            LearningType learningType,
            String status,
            String rejectionReason,
            DetailsRevision revision,
            List<Unit> units,
            int studentCount,
            boolean canDelete) {
    }

    /** One change in a submitted revision; the frontend words it from {@code kind}. */
    public record Change(String kind, String title, Integer count, Integer total) {
    }

    /** One step in the history of a class or lesson. {@code actorRole} is TEACHER or ADMIN. */
    public record Event(
            Long id,
            String type,
            String subjectTitle,
            Long lessonId,
            String actorName,
            String actorRole,
            String note,
            List<Change> changes,
            LocalDateTime createdAt) {
    }

    // ----- lesson editor -----

    public record KeyPhrase(String chinese, String pinyin, String meaning) {
    }

    public record Video(String title, Integer durationSeconds, int watchedCount, int studentCount) {
    }

    public record TraceCharacter(String character, String pinyin, String meaning) {
    }

    public record MatchPair(String chinese, String pinyin, String meaning) {
    }

    public record Activity(
            String type,
            Long id,
            String title,
            String stage,
            int number,
            List<TraceCharacter> characters,
            List<MatchPair> pairs) {
    }

    public record QuestionOption(Long id, String text, boolean correct) {
    }

    public record Question(
            Long id,
            int number,
            String type,
            String text,
            int points,
            String correctAnswerText,
            String explanation,
            String hint,
            String audioUrl,
            String imageUrl,
            List<QuestionOption> options) {
    }

    public record AttemptSummary(String studentName, LocalDateTime completedAt, int percent) {
    }

    public record Quiz(
            Long id,
            String title,
            String description,
            String stage,
            Integer timeLimitMinutes,
            int totalPoints,
            boolean questionsLocked,
            int attemptCount,
            Integer averagePercent,
            List<AttemptSummary> recentAttempts,
            List<Question> questions) {
    }

    /** What the submit dialog checks; the server applies the same checks when the lesson is submitted. */
    public record Checklist(
            int activityCount,
            int quizCount,
            int questionCount,
            boolean quizzesReady,
            boolean pinyinComplete) {
    }

    public record UnitOption(Long id, String title) {
    }

    /**
     * The lesson editor. Content comes from the working copy: the lesson itself while it was never published, or its
     * revision once it is (students keep the published lesson until the revision is approved).
     */
    public record LessonEditor(
            Long lessonId,
            Long classId,
            String classTitle,
            String classStatus,
            Long unitId,
            String unitTitle,
            List<UnitOption> units,
            String status,
            String revisionStatus,
            boolean editable,
            String reviewNote,
            String reviewNoteBy,
            LocalDateTime reviewNoteAt,
            String title,
            Integer minutes,
            String summary,
            List<KeyPhrase> keyPhrases,
            Video video,
            List<Activity> activities,
            List<Quiz> quizzes,
            Checklist checklist) {
    }
}
