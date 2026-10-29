package com.example.aichinese.dto.classes;

import java.time.LocalDateTime;
import java.util.List;

/** A class lesson as the student works through it: the lesson page, its activities and its quizzes. */
public final class LessonViews {

    private LessonViews() {
    }

    public record KeyPhrase(String chinese, String pinyin, String meaning) {
    }

    /** The lesson video; {@code streamPath} is set for an uploaded file, {@code externalUrl} for a link. */
    public record Video(
            String title,
            String externalUrl,
            String streamPath,
            Integer durationSeconds,
            boolean watched,
            int percentWatched) {
    }

    /** A practice activity in the lesson, in the teacher's recommended order. */
    public record ActivityStep(String type, Long id, String title, int number, String stage, boolean done) {
    }

    /** A lesson quiz card: settings plus the student's best score and attempts. */
    public record QuizCard(
            Long id,
            String title,
            String stage,
            int questionCount,
            int marks,
            Integer timeLimitMinutes,
            boolean hintsAvailable,
            Integer bestPercent,
            int attemptCount,
            boolean inProgress) {
    }

    public record StudentLesson(
            Long classId,
            String classTitle,
            String teacherName,
            String unitTitle,
            String title,
            int number,
            String summary,
            Integer minutes,
            boolean available,
            Video video,
            List<KeyPhrase> keyPhrases,
            List<ActivityStep> activities,
            List<QuizCard> quizzes,
            int stepsDone,
            int stepsTotal,
            int percent,
            String status,
            Integer bestQuizPercent,
            List<LiveSessionViews.SessionBrief> liveSessions) {
    }

    /** A character to trace, with its pinyin and meaning. */
    public record TraceCharacter(String character, String pinyin, String meaning) {
    }

    /** A Chinese word of a matching activity; the meanings come separately and shuffled. */
    public record MatchWord(Long id, String chinese, String pinyin) {
    }

    public record ActivityRef(String type, Long id, String title) {
    }

    /** One activity to play in focus mode. Only the part of its type is filled. */
    public record ActivityPlay(
            String type,
            Long id,
            String title,
            String stage,
            int number,
            int activityCount,
            String lessonTitle,
            boolean done,
            ActivityRef previous,
            ActivityRef next,
            List<KeyPhrase> cards,
            List<TraceCharacter> characters,
            List<MatchWord> words,
            List<String> meanings) {
    }

    /** Outcome of finishing or checking an activity, with the lesson's progress afterwards. */
    public record ActivityOutcome(
            boolean done,
            List<PairResult> pairs,
            int stepsDone,
            int stepsTotal,
            String lessonStatus) {
    }

    public record PairResult(Long wordId, boolean correct) {
    }

    public record MatchHint(Long wordId, String meaning) {
    }

    // ----- lesson quiz -----

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
    }

    /** The quiz before it starts: what it holds and how the student did so far. */
    public record QuizIntro(
            Long id,
            String title,
            String stage,
            int questionCount,
            int marks,
            Integer timeLimitMinutes,
            boolean hintsAvailable,
            Long classId,
            String classTitle,
            Long lessonId,
            String lessonTitle,
            Integer bestPercent,
            Long openAttemptId) {
    }

    /** A started (or resumed) attempt: answers are saved one by one until it is submitted or time is up. */
    public record QuizSession(
            Long attemptId,
            QuizIntro quiz,
            LocalDateTime expiresAt,
            Long secondsLeft,
            List<QuizQuestion> questions,
            List<SavedAnswer> answers) {
    }

    public record QuizHint(Long questionId, String hint) {
    }

    /** One question of a submitted attempt with the student's answer, the right answer and the explanation. */
    public record ReviewedQuestion(
            Long questionId,
            int number,
            String text,
            String yourAnswer,
            String correctAnswer,
            boolean correct,
            String explanation,
            boolean hintUsed) {
    }

    /** The activity of the lesson to practise after the quiz, picked from the skill most wrong answers are about. */
    public record PracticeSuggestion(String skill, ActivityRef activity) {
    }

    public record QuizResult(
            Long attemptId,
            QuizIntro quiz,
            LocalDateTime submittedAt,
            int score,
            int maxScore,
            int percent,
            Integer bestPercent,
            int hintsUsed,
            int correctCount,
            int wrongCount,
            List<ReviewedQuestion> questions,
            String lessonStatus,
            int activitiesLeft,
            PracticeSuggestion suggestion,
            List<ClassViews.FeedbackItem> feedback) {
    }
}
