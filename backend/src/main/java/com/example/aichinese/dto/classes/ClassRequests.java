package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.immutableListOrEmpty;
import static com.example.aichinese.dto.DtoNormalization.strip;
import static com.example.aichinese.dto.DtoNormalization.stripToNull;

import java.time.LocalDateTime;
import java.util.List;

import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.LearningType;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Request bodies of the class endpoints (teacher authoring, live sessions, feedback, student activities). */
public final class ClassRequests {

    private ClassRequests() {
    }

    /** Create a class, or save the details of a class (a pending revision once the class is published). */
    public record ClassDetails(
            @NotBlank(message = "Class title is required.")
            @Size(max = 200, message = "Class title must be 200 characters or fewer.")
            String title,

            @Size(max = 2000, message = "Description must be 2000 characters or fewer.")
            String description,

            @Positive(message = "Level id must be positive.")
            Long levelId,

            @Positive(message = "Topic id must be positive.")
            Long topicId,

            LearningType learningType) {

        public ClassDetails {
            title = strip(title);
            description = stripToNull(description);
        }
    }

    public record TeacherNote(
            @Size(max = 500, message = "The note must be 500 characters or fewer.")
            String note) {

        public TeacherNote {
            note = stripToNull(note);
        }
    }

    public record UnitTitle(
            @NotBlank(message = "Unit title is required.")
            @Size(max = 200, message = "Unit title must be 200 characters or fewer.")
            String title) {

        public UnitTitle {
            title = strip(title);
        }
    }

    public record LessonTitle(
            @NotBlank(message = "Lesson title is required.")
            @Size(max = 200, message = "Lesson title must be 200 characters or fewer.")
            String title) {

        public LessonTitle {
            title = strip(title);
        }
    }

    /** The new order of the class: every unit with its lessons in order (lessons may move between units). */
    public record Structure(
            @NotNull(message = "Units are required.")
            @Size(max = 100, message = "A class cannot have more than 100 units.")
            @Valid
            List<@NotNull(message = "Unit must not be null.") UnitOrder> units) {

        public Structure {
            units = immutableListOrEmpty(units);
        }
    }

    public record UnitOrder(
            @NotNull(message = "Unit id is required.") Long unitId,
            @Size(max = 200, message = "A unit cannot have more than 200 lessons.")
            List<@NotNull(message = "Lesson id must not be null.") Long> lessonIds) {

        public UnitOrder {
            lessonIds = immutableListOrEmpty(lessonIds);
        }
    }

    public record KeyPhrase(
            @NotBlank(message = "Chinese is required for every key phrase.")
            @Size(max = 100, message = "Chinese must be 100 characters or fewer.")
            String chinese,

            @NotBlank(message = "Pinyin is required for every key phrase.")
            @Size(max = 200, message = "Pinyin must be 200 characters or fewer.")
            String pinyin,

            @NotBlank(message = "Meaning is required for every key phrase.")
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.")
            String meaning) {

        public KeyPhrase {
            chinese = strip(chinese);
            pinyin = strip(pinyin);
            meaning = strip(meaning);
        }
    }

    /** The Content tab of the lesson editor: details and key phrases, saved together. */
    public record LessonContent(
            @NotBlank(message = "Lesson title is required.")
            @Size(max = 200, message = "Lesson title must be 200 characters or fewer.")
            String title,

            @Positive(message = "Unit id must be positive.")
            Long unitId,

            @Min(value = 1, message = "Minutes must be between 1 and 240.")
            @Max(value = 240, message = "Minutes must be between 1 and 240.")
            Integer minutes,

            @Size(max = 1000, message = "Summary must be 1000 characters or fewer.")
            String summary,

            @Size(max = 30, message = "A lesson can have at most 30 key phrases.")
            @Valid
            List<@NotNull(message = "Key phrase must not be null.") KeyPhrase> keyPhrases) {

        public LessonContent {
            title = strip(title);
            summary = stripToNull(summary);
            keyPhrases = immutableListOrEmpty(keyPhrases);
        }
    }

    public record TraceCharacter(
            @NotBlank(message = "Enter the character to trace.")
            @Size(max = 4, message = "Enter one character per row.")
            String character,

            @NotBlank(message = "Pinyin is required for every character.")
            @Size(max = 40, message = "Pinyin must be 40 characters or fewer.")
            String pinyin,

            @Size(max = 80, message = "Meaning must be 80 characters or fewer.")
            String meaning) {

        public TraceCharacter {
            character = strip(character);
            pinyin = strip(pinyin);
            meaning = stripToNull(meaning);
        }
    }

    public record MatchPair(
            @NotBlank(message = "Chinese is required for every pair.")
            @Size(max = 100, message = "Chinese must be 100 characters or fewer.")
            String chinese,

            @NotBlank(message = "Pinyin is required for every pair.")
            @Size(max = 200, message = "Pinyin must be 200 characters or fewer.")
            String pinyin,

            @NotBlank(message = "Meaning is required for every pair.")
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.")
            String meaning) {

        public MatchPair {
            chinese = strip(chinese);
            pinyin = strip(pinyin);
            meaning = strip(meaning);
        }
    }

    /**
     * An activity of a lesson. Flashcards review the lesson's key phrases; tracing needs {@code characters} and
     * matching needs {@code pairs}.
     */
    public record Activity(
            @NotBlank(message = "Activity type is required.")
            @Size(max = 20, message = "Activity type must be 20 characters or fewer.")
            String type,

            @NotBlank(message = "Activity title is required.")
            @Size(max = 200, message = "Activity title must be 200 characters or fewer.")
            String title,

            LearningStage stage,

            @Size(max = 8, message = "An activity can have at most 8 characters to trace.")
            @Valid
            List<@NotNull(message = "Character must not be null.") TraceCharacter> characters,

            @Size(max = 12, message = "A matching activity can have at most 12 pairs.")
            @Valid
            List<@NotNull(message = "Pair must not be null.") MatchPair> pairs) {

        public Activity {
            type = strip(type);
            title = strip(title);
            characters = immutableListOrEmpty(characters);
            pairs = immutableListOrEmpty(pairs);
        }
    }

    public record ActivityOrderItem(
            @NotBlank(message = "Activity type is required.") String type,
            @NotNull(message = "Activity id is required.") Long id) {
    }

    public record ActivityOrder(
            @NotNull(message = "Activities are required.")
            @Size(max = 50, message = "Too many activities.")
            @Valid
            List<@NotNull(message = "Activity must not be null.") ActivityOrderItem> activities) {

        public ActivityOrder {
            activities = immutableListOrEmpty(activities);
        }
    }

    public record QuestionOrder(
            @NotNull(message = "Questions are required.")
            @Size(max = 200, message = "Too many questions.")
            List<@NotNull(message = "Question id must not be null.") Long> questionIds) {

        public QuestionOrder {
            questionIds = immutableListOrEmpty(questionIds);
        }
    }

    /** Schedule or edit a live session; start and end are wall-clock times in {@code timeZone}. */
    public record LiveSession(
            @NotBlank(message = "Title is required.")
            @Size(max = 200, message = "Title must be 200 characters or fewer.")
            String title,

            @NotNull(message = "Choose a class for the session.")
            @Positive(message = "Class id must be positive.")
            Long classId,

            @Positive(message = "Lesson id must be positive.")
            Long lessonId,

            @NotNull(message = "Start time is required.")
            LocalDateTime startTime,

            @NotNull(message = "End time is required.")
            LocalDateTime endTime,

            @Size(max = 64, message = "Time zone must be 64 characters or fewer.")
            String timeZone,

            @Min(value = 1, message = "Capacity must be between 1 and 500.")
            @Max(value = 500, message = "Capacity must be between 1 and 500.")
            Integer capacity,

            @NotBlank(message = "Meeting link is required.")
            @Size(max = 500, message = "Meeting link must be 500 characters or fewer.")
            String meetingLink,

            @NotBlank(message = "Meeting platform is required.")
            @Size(max = 40, message = "Meeting platform must be 40 characters or fewer.")
            String meetingPlatform) {

        public LiveSession {
            title = strip(title);
            timeZone = stripToNull(timeZone);
            meetingLink = strip(meetingLink);
            meetingPlatform = strip(meetingPlatform);
        }
    }

    public record Recording(
            @NotBlank(message = "Recording link is required.")
            @Size(max = 500, message = "Recording link must be 500 characters or fewer.")
            String recordingUrl) {

        public Recording {
            recordingUrl = strip(recordingUrl);
        }
    }

    /** Teacher feedback to a student of one of the teacher's classes. */
    public record Feedback(
            @NotNull(message = "Student id is required.")
            @Positive(message = "Student id must be positive.")
            Long studentId,

            @NotNull(message = "Class id is required.")
            @Positive(message = "Class id must be positive.")
            Long classId,

            @Positive(message = "Lesson id must be positive.")
            Long lessonId,

            @Positive(message = "Quiz attempt id must be positive.")
            Long quizAttemptId,

            @NotBlank(message = "Feedback type is required.")
            @Size(max = 50, message = "Feedback type must be 50 characters or fewer.")
            String type,

            @NotBlank(message = "Feedback message is required.")
            @Size(min = 5, max = 2000, message = "Feedback must be between 5 and 2,000 characters.")
            String message) {

        public Feedback {
            type = strip(type);
            message = strip(message);
        }
    }

    public record FeedbackUpdate(
            @NotBlank(message = "Feedback type is required.")
            @Size(max = 50, message = "Feedback type must be 50 characters or fewer.")
            String type,

            @NotBlank(message = "Feedback message is required.")
            @Size(min = 5, max = 2000, message = "Feedback must be between 5 and 2,000 characters.")
            String message) {

        public FeedbackUpdate {
            type = strip(type);
            message = strip(message);
        }
    }

    /** The student's pairs of a matching activity: each Chinese word with the meaning chosen for it. */
    public record MatchingAnswer(
            @NotNull(message = "Pairs are required.")
            @Size(max = 50, message = "Too many pairs.")
            @Valid
            List<@NotNull(message = "Pair must not be null.") ChosenPair> pairs) {

        public MatchingAnswer {
            pairs = immutableListOrEmpty(pairs);
        }
    }

    public record ChosenPair(
            @NotNull(message = "Word id is required.") Long wordId,
            @Size(max = 300, message = "Meaning must be 300 characters or fewer.") String meaning) {
    }

    public record MatchingHint(@NotNull(message = "Word id is required.") Long wordId) {
    }

    public record HintRequest(@NotNull(message = "Question id is required.") Long questionId) {
    }
}
