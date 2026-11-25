package com.example.aichinese.dto.classes;

import static com.example.aichinese.dto.DtoNormalization.redactLength;

import java.time.LocalDateTime;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

/** A class lesson as the learner works through it: the lesson page, its activities and its quizzes. */
public final class LessonViews {

    private LessonViews() {
    }

    /** A key phrase of the lesson; the admin lesson editor shows the same record. */
    public record KeyPhrase(String chinese, String pinyin, String meaning) {
    }

    /**
     * The lesson video; {@code streamPath} is set for an uploaded file, {@code externalUrl} for a link (neither in an
     * archived class, where the video no longer plays).
     */
    @Schema(name = "StudentLessonVideo")
    public record Video(
            String title,
            String externalUrl,
            String streamPath,
            Integer durationSeconds,
            boolean watched) {
    }

    /** A practice activity in the lesson, in lesson order. */
    public record ActivityStep(String type, Long id, String title, int number, boolean done) {
    }

    /**
     * A lesson quiz card: its size plus the learner's best score and attempts. {@code bestAttemptId} is the submitted
     * attempt with the best score (the newest of equal ones), behind "See best result"; null before the first attempt.
     */
    public record QuizCard(
            Long id,
            int questionCount,
            Integer bestPercent,
            int attemptCount,
            Long bestAttemptId,
            boolean inProgress) {
    }

    /**
     * A lesson of a joined class. {@code archived}: the class is archived, so the lesson is read-only (its key phrases,
     * activities and quiz results show, but the video, activities and quiz no longer open).
     */
    public record StudentLesson(
            Long classId,
            String classTitle,
            String title,
            boolean available,
            Video video,
            List<KeyPhrase> keyPhrases,
            List<ActivityStep> activities,
            List<QuizCard> quizzes,
            int stepsDone,
            int stepsTotal,
            String status,
            boolean archived) {
    }

    /** A character to trace, with its pinyin and meaning; the admin lesson editor shows the same record. */
    public record TraceCharacter(String character, String pinyin, String meaning) {
    }

    /** A Chinese word of a matching activity; the meanings come separately and shuffled. */
    public record MatchWord(Long id, String chinese, String pinyin) {
    }

    public record ActivityRef(String type, Long id) {
    }

    /**
     * One activity to play in focus mode. Only the part of its type is filled. {@code hintsAllowed}: the lesson's
     * "Allow SparkBot hints" is on (its quiz's setting), so SparkBot may offer hints here too.
     */
    public record ActivityPlay(
            String type,
            Long id,
            Long classId,
            Long lessonId,
            String title,
            boolean hintsAllowed,
            int number,
            int activityCount,
            String lessonTitle,
            ActivityRef next,
            List<KeyPhrase> cards,
            List<TraceCharacter> characters,
            List<MatchWord> words,
            List<String> meanings) {
    }

    /** Outcome of finishing or checking an activity, with the lesson's status afterwards. */
    public record ActivityOutcome(
            boolean done,
            List<PairResult> pairs,
            String lessonStatus,
            boolean lessonCompletedNow) {
    }

    public record PairResult(Long wordId, boolean correct) {
    }

    public record MatchHint(Long wordId, String meaning) {
    }

    // Lesson quiz

    public record QuizOption(Long id, String text) {
    }

    /** A question as the student answers it (no answer key). */
    public record QuizQuestion(
            Long id,
            String type,
            String text,
            String audioUrl,
            String imageUrl,
            boolean hasHint,
            List<QuizOption> options) {
    }

    public record SavedAnswer(Long questionId, Long optionId, String answerText) {

        @Override
        public String toString() {
            return "SavedAnswer[questionId=" + questionId
                    + ", optionId=" + optionId
                    + ", answerText=" + redactLength(answerText) + "]";
        }
    }

    /** The quiz and where it sits (its class and lesson), for the headers of the quiz and result pages. */
    public record QuizIntro(
            Long id,
            Long classId,
            String classTitle,
            Long lessonId,
            String lessonTitle) {
    }

    /** A started (or resumed) attempt: answers are saved one by one until it is submitted. */
    public record QuizSession(
            Long attemptId,
            QuizIntro quiz,
            List<QuizQuestion> questions,
            List<SavedAnswer> answers) {
    }

    public record QuizHint(String hint) {
    }

    /**
     * One question of a submitted attempt with the student's answer, the right answer and the explanation;
     * {@code optionFeedback} is the feedback of the answer the student picked when it was wrong ("That's 面"), else null.
     */
    public record ReviewedQuestion(
            Long questionId,
            int number,
            String text,
            String yourAnswer,
            String correctAnswer,
            boolean correct,
            String explanation,
            String optionFeedback) {

        @Override
        public String toString() {
            return "ReviewedQuestion[questionId=" + questionId
                    + ", number=" + number
                    + ", text=" + text
                    + ", yourAnswer=" + redactLength(yourAnswer)
                    + ", correctAnswer=" + correctAnswer
                    + ", correct=" + correct
                    + ", explanation=" + explanation
                    + ", optionFeedback=" + optionFeedback + "]";
        }
    }

    /**
     * A submitted attempt with its own {@code percent}; {@code bestPercent} is the best of the quiz's submitted attempts,
     * this one included, and {@code bestCorrect} of {@code bestTotal} the right answers of that best attempt ("Your best
     * is 4 out of 5 (80%)").
     */
    public record QuizResult(
            Long attemptId,
            QuizIntro quiz,
            LocalDateTime submittedAt,
            int percent,
            int bestPercent,
            int bestCorrect,
            int bestTotal,
            int correctCount,
            int wrongCount,
            List<ReviewedQuestion> questions,
            String lessonStatus,
            int activitiesLeft) {
    }
}
