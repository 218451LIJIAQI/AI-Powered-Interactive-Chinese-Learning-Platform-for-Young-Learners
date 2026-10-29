package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearningType;

/**
 * Admin review of teacher classes. A review item is a new class (with the lessons submitted with it), a change to
 * the details of a published class, a new lesson of a published class or a revision of a published lesson.
 */
public final class ReviewViews {

    private ReviewViews() {
    }

    /** {@code kind}: NEW_CLASS, CLASS_DETAILS, NEW_LESSON or LESSON_REVISION; {@code type} for links: CLASS or LESSON. */
    public record QueueItem(
            String kind,
            String type,
            Long classId,
            Long lessonId,
            String title,
            String classTitle,
            String teacherName,
            LocalDateTime submittedAt,
            List<String> lessonTitles,
            boolean changesQuiz) {
    }

    public record Queue(List<QueueItem> items, long total) {
    }

    /** The class as students will see it after approval. */
    public record ClassPreview(
            String title,
            String description,
            String levelName,
            String topicName,
            LearningType learningType,
            String teacherName,
            int unitCount,
            int lessonCount,
            List<LessonTitle> lessons) {
    }

    public record LessonTitle(Long id, String title, String unitTitle) {
    }

    public record ActivityPreview(String type, String title, String stage, boolean isNew) {
    }

    public record QuizPreview(String title, String stage, int questionCount, boolean everyQuestionExplained, boolean isNew) {
    }

    /** A lesson as students will see it after approval. */
    public record LessonPreview(
            Long lessonId,
            String title,
            String classTitle,
            String unitTitle,
            String summary,
            List<TeacherClassViews.KeyPhrase> keyPhrases,
            String videoTitle,
            Integer videoDurationSeconds,
            List<ActivityPreview> activities,
            List<QuizPreview> quizzes) {
    }

    public record Detail(
            QueueItem item,
            ClassPreview classPreview,
            List<LessonPreview> lessons,
            List<TeacherClassViews.Change> changes,
            List<String> notInReview,
            List<TeacherClassViews.Event> history) {
    }

    /** One class or lesson in the admin Teacher classes list. */
    public record ContentRow(
            String type,
            Long id,
            String title,
            String classTitle,
            String teacherName,
            String status,
            String revisionStatus,
            LocalDateTime updatedAt) {
    }

    public record ContentDetail(
            ContentRow row,
            String reviewNote,
            String reviewNoteBy,
            LocalDateTime reviewNoteAt,
            String unitTitle,
            int unitCount,
            int lessonCount,
            int activityCount,
            int questionCount,
            int studentCount,
            List<TeacherClassViews.Event> history,
            boolean canArchive,
            boolean canRestore) {
    }

    public record TeacherOption(Long id, String name) {
    }
}
