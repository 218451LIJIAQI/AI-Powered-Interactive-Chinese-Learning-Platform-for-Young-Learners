package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.dto.content.LearningCategoryResponse;

/**
 * Admin › Content › Classes: the list of classes and lessons, the class page (units and lessons, details) and the lesson
 * editor. Statuses are the stored content statuses: DRAFT, PUBLISHED or ARCHIVED.
 */
public final class AdminClassViews {

    private AdminClassViews() {
    }

    /**
     * One row of the Classes list: a class ({@code classId} is its own id and {@code classTitle} null) or a lesson
     * (with its class). {@code updatedAt} of a lesson includes its unpublished changes.
     */
    public record ContentRow(
            String type,
            Long id,
            String title,
            Long classId,
            String classTitle,
            String status,
            LocalDateTime updatedAt) {
    }

    /** One lesson on the class page; {@code hasUnpublishedChanges}: a published lesson with saved, unpublished edits. */
    public record LessonRow(Long id, String title, String status, boolean hasUnpublishedChanges) {
    }

    /** A unit of the class, with its lessons in order (archived ones included). */
    public record Unit(Long id, String title, List<LessonRow> lessons) {
    }

    /**
     * The class page, its units in order. {@code lessonCount} counts the lessons that are not archived.
     * {@code canPublish}: a draft class with 2 units holding published lessons and 4 to 6 published lessons in all.
     */
    public record ClassPage(
            Long id,
            String title,
            String description,
            LearningCategoryResponse category,
            Integer bestAfterUnit,
            String status,
            int lessonCount,
            List<Unit> units,
            boolean canPublish) {
    }

    // Lesson editor

    public record Video(String title, Integer durationSeconds) {
    }

    /**
     * One of the lesson's 3 activity slots ({@code type} FLASHCARDS, TRACING or MATCHING): a lesson has exactly one
     * activity of each type, which the editor fills and edits. Flashcards have their {@code cards} (Chinese · Pinyin ·
     * Meaning; empty for an older activity, whose learners review the lesson's key phrases).
     */
    public record Activity(
            String type,
            Long id,
            String title,
            int number,
            List<LessonViews.TraceCharacter> characters,
            List<LessonViews.KeyPhrase> pairs,
            List<LessonViews.KeyPhrase> cards) {
    }

    /** An answer of a quiz question; {@code feedback} is what a learner who picks it reads in the review, or null. */
    public record QuestionOption(Long id, String text, boolean correct, String feedback) {
    }

    /**
     * A quiz question. {@code aiDraft}: taken from the AI quiz draft and not checked yet; an admin keeps it as it is
     * ({@code POST .../questions/{questionId}/keep}) or edits it, and the lesson is not published before.
     */
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
            List<QuestionOption> options,
            boolean aiDraft) {
    }

    /**
     * The lesson's quiz. {@code hintsAllowed} is "Allow SparkBot hints"; {@code questionsLocked} once learners have
     * submitted attempts (the questions they answered stay as they are).
     */
    public record Quiz(Long id, boolean hintsAllowed, boolean questionsLocked, List<Question> questions) {
    }

    /**
     * What publishing needs, on the version the editor shows: a video, key phrases, all 3 activities, a quiz of exactly
     * 5 questions each with a correct answer, no unchecked AI draft question, and pinyin for every new word.
     * {@code missing} names what is not there yet, in that order: VIDEO, KEY_PHRASES, ACTIVITIES, QUIZ, AI_DRAFTS,
     * PINYIN (empty when the lesson is complete).
     */
    public record Checklist(
            boolean videoAdded,
            boolean keyPhrasesAdded,
            boolean activitiesComplete,
            boolean quizzesReady,
            boolean pinyinComplete,
            List<String> missing) {
    }

    public record UnitOption(Long id, String title) {
    }

    /**
     * The lesson editor. Content comes from the working copy: the lesson itself while it was never published, or its
     * unpublished changes once it is (learners keep the published lesson until those are published).
     * {@code editable}: neither the lesson nor its class is archived.
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
            boolean hasUnpublishedChanges,
            boolean editable,
            String title,
            List<LessonViews.KeyPhrase> keyPhrases,
            Video video,
            long videoUploadMaxBytes,
            List<Activity> activities,
            List<Quiz> quizzes,
            Checklist checklist) {
    }
}
