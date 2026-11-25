package com.example.aichinese.service.classes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.service.classes.ClassContentSupport.ActivityKey;

import lombok.RequiredArgsConstructor;

/**
 * Read-only change detection between published lessons and their working copies ({@code revision_of_lesson_id}).
 * Batches lesson-level queries (key phrases, videos, activities, quizzes) and short-circuits earlier stages before
 * loading activity items or quiz questions.
 */
@Component
@RequiredArgsConstructor
class LessonRevisionDiff {

    private final ClassContentSupport content;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    /** Whether a working copy differs from its published lesson in anything learners would see. */
    boolean hasChanges(Lesson revision) {
        return !changedLessonIds(List.of(revision)).isEmpty();
    }

    /** Published lesson ids whose working copy differs from the live lesson. */
    Set<Long> changedLessonIds(Collection<Lesson> revisions) {
        if (revisions == null || revisions.isEmpty()) {
            return Set.of();
        }
        Set<Long> changed = new HashSet<>();
        List<Lesson> remaining = new ArrayList<>();
        for (Lesson revision : revisions) {
            Lesson lesson = revision.getRevisionOf();
            if (lesson == null) {
                continue;
            }
            if (detailsChanged(lesson, revision)) {
                changed.add(lesson.getId());
            } else {
                remaining.add(revision);
            }
        }
        if (remaining.isEmpty()) {
            return Set.copyOf(changed);
        }

        Map<Long, List<LessonViews.KeyPhrase>> phrasesByLesson = content.keyPhraseViewsByLesson(lessonAndRevisionIds(remaining));
        remaining = partitionChanged(remaining, changed, revision -> !phrasesByLesson
                .getOrDefault(revision.getRevisionOf().getId(), List.of())
                .equals(phrasesByLesson.getOrDefault(revision.getId(), List.of())));
        if (remaining.isEmpty()) {
            return Set.copyOf(changed);
        }

        Map<Long, RecordedLessonVideo> videoByLesson = content.videoByLesson(lessonAndRevisionIds(remaining));
        remaining = partitionChanged(remaining, changed, revision -> videoChanged(
                videoByLesson.get(revision.getRevisionOf().getId()), videoByLesson.get(revision.getId())));
        if (remaining.isEmpty()) {
            return Set.copyOf(changed);
        }

        Map<Long, List<LessonActivity>> activitiesByLesson = content.activitiesByLesson(lessonAndRevisionIds(remaining));
        remaining = partitionActivitiesChanged(remaining, changed, activitiesByLesson);
        if (remaining.isEmpty()) {
            return Set.copyOf(changed);
        }

        Map<Long, List<Quiz>> quizzesByLesson = content.quizzesByLesson(lessonAndRevisionIds(remaining));
        partitionQuizzesChanged(remaining, changed, quizzesByLesson);
        return Set.copyOf(changed);
    }

    static boolean detailsChanged(Lesson lesson, Lesson revision) {
        return !Objects.equals(lesson.getTitle(), revision.getTitle());
    }

    static boolean videoChanged(RecordedLessonVideo before, RecordedLessonVideo after) {
        if (before == null || after == null) {
            return before != after;
        }
        return !Objects.equals(after.getRevisionSourceId(), before.getId());
    }

    /**
     * Questions learners already answered stay as they are: those of a quiz with a submitted attempt, and those of a
     * working copy of such a quiz (the editor locks them, and publishing never replaces them).
     */
    boolean questionsLocked(Quiz quiz) {
        Long publishedId = quiz.getRevisionSourceId() == null ? quiz.getId() : quiz.getRevisionSourceId();
        return quizAttemptRepository.existsByQuizIdAndSubmittedTrue(publishedId);
    }

    static boolean activityMetaOrTracingChanged(LessonActivity source, LessonActivity copy) {
        if (!Objects.equals(source.getTitle(), copy.getTitle())) {
            return true;
        }
        return source instanceof TracingExercise published && copy instanceof TracingExercise revised
                && !TracingCharacters.read(published).equals(TracingCharacters.read(revised));
    }

    static Map<ActivityKey, LessonActivity> indexActivities(List<LessonActivity> activities) {
        return activities.stream().collect(Collectors.toMap(ActivityKey::of, Function.identity()));
    }

    static Map<Long, Quiz> indexQuizzes(List<Quiz> quizzes) {
        return quizzes.stream().collect(Collectors.toMap(Quiz::getId, Function.identity()));
    }

    /** The published activity a copy came from; null for an activity new in the working copy. */
    static LessonActivity sourceOf(LessonActivity copy, Map<ActivityKey, LessonActivity> sources) {
        return copy.getRevisionSourceId() == null
                ? null
                : sources.get(new ActivityKey(ClassContentSupport.typeOf(copy), copy.getRevisionSourceId()));
    }

    /** The published quiz a copy came from; null for a quiz new in the working copy. */
    static Quiz sourceOf(Quiz copy, Map<Long, Quiz> sources) {
        return copy.getRevisionSourceId() == null ? null : sources.get(copy.getRevisionSourceId());
    }

    boolean sameQuestions(Quiz first, Quiz second) {
        return sameQuestions(content.questions(first.getId()), content.questions(second.getId()));
    }

    /** Whether the published order can still identify the corresponding questions in an untouched copy. */
    boolean sameQuestions(List<QuizQuestion> first, List<QuizQuestion> second) {
        if (first.size() != second.size()) {
            return false;
        }
        List<QuizQuestion> all = Stream.concat(first.stream(), second.stream()).toList();
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(all);
        return questionContents(first, options).equals(questionContents(second, options));
    }

    private static List<Long> lessonAndRevisionIds(List<Lesson> revisions) {
        List<Long> ids = new ArrayList<>(revisions.size() * 2);
        for (Lesson revision : revisions) {
            ids.add(revision.getRevisionOf().getId());
            ids.add(revision.getId());
        }
        return ids;
    }

    private static List<Lesson> partitionChanged(
            List<Lesson> revisions, Set<Long> changed, Predicate<Lesson> isChanged) {
        List<Lesson> stillSame = new ArrayList<>(revisions.size());
        for (Lesson revision : revisions) {
            if (isChanged.test(revision)) {
                changed.add(revision.getRevisionOf().getId());
            } else {
                stillSame.add(revision);
            }
        }
        return stillSame;
    }

    /**
     * Checks in-memory activity metadata and tracing characters first, then batches flashcard cards and matching pairs
     * in at most two queries for all remaining revisions.
     */
    private List<Lesson> partitionActivitiesChanged(
            List<Lesson> revisions, Set<Long> changed, Map<Long, List<LessonActivity>> activitiesByLesson) {
        record ActivityPair(LessonActivity source, LessonActivity copy) {
        }
        List<Lesson> needingItemCheck = new ArrayList<>(revisions.size());
        Map<Long, List<ActivityPair>> itemPairsByRevision = new HashMap<>();
        Set<Long> flashcardIds = new HashSet<>();
        Set<Long> matchingGameIds = new HashSet<>();

        for (Lesson revision : revisions) {
            Map<ActivityKey, LessonActivity> sources = indexActivities(
                    activitiesByLesson.getOrDefault(revision.getRevisionOf().getId(), List.of()));
            List<ActivityPair> itemPairs = new ArrayList<>();
            boolean metadataChanged = false;
            for (LessonActivity activity : activitiesByLesson.getOrDefault(revision.getId(), List.of())) {
                LessonActivity source = sourceOf(activity, sources);
                if (source == null || activityMetaOrTracingChanged(source, activity)) {
                    metadataChanged = true;
                    break;
                }
                if (activity instanceof Flashcard) {
                    itemPairs.add(new ActivityPair(source, activity));
                    flashcardIds.add(source.getId());
                    flashcardIds.add(activity.getId());
                } else if (activity instanceof MatchingGame) {
                    itemPairs.add(new ActivityPair(source, activity));
                    matchingGameIds.add(source.getId());
                    matchingGameIds.add(activity.getId());
                }
            }
            if (metadataChanged) {
                changed.add(revision.getRevisionOf().getId());
            } else {
                needingItemCheck.add(revision);
                if (!itemPairs.isEmpty()) {
                    itemPairsByRevision.put(revision.getId(), itemPairs);
                }
            }
        }
        if (needingItemCheck.isEmpty() || (flashcardIds.isEmpty() && matchingGameIds.isEmpty())) {
            return needingItemCheck;
        }
        Map<Long, List<LessonViews.KeyPhrase>> cardsByFlashcard = content.cardViewsByFlashcard(flashcardIds);
        Map<Long, List<LessonViews.KeyPhrase>> pairsByGame = content.matchPairsByGame(matchingGameIds);
        return partitionChanged(needingItemCheck, changed, revision -> {
            for (ActivityPair pair : itemPairsByRevision.getOrDefault(revision.getId(), List.of())) {
                if (pair.copy() instanceof Flashcard) {
                    if (!cardsByFlashcard.getOrDefault(pair.source().getId(), List.of())
                            .equals(cardsByFlashcard.getOrDefault(pair.copy().getId(), List.of()))) {
                        return true;
                    }
                } else if (pair.copy() instanceof MatchingGame) {
                    if (!pairsByGame.getOrDefault(pair.source().getId(), List.of())
                            .equals(pairsByGame.getOrDefault(pair.copy().getId(), List.of()))) {
                        return true;
                    }
                }
            }
            return false;
        });
    }

    /**
     * Checks quiz metadata in memory first, then batches submitted-attempt checks, questions, and options in O(1)
     * queries across all remaining revisions.
     */
    private void partitionQuizzesChanged(
            List<Lesson> revisions, Set<Long> changed, Map<Long, List<Quiz>> quizzesByLesson) {
        record QuizPair(Quiz source, Quiz copy) {
        }
        List<Lesson> needingQuestionCheck = new ArrayList<>(revisions.size());
        Map<Long, List<QuizPair>> pairsByRevision = new HashMap<>();
        Set<Long> sourceQuizIds = new HashSet<>();

        for (Lesson revision : revisions) {
            Map<Long, Quiz> sources = indexQuizzes(
                    quizzesByLesson.getOrDefault(revision.getRevisionOf().getId(), List.of()));
            List<QuizPair> pairs = new ArrayList<>();
            boolean metadataChanged = false;
            for (Quiz quiz : quizzesByLesson.getOrDefault(revision.getId(), List.of())) {
                Quiz source = sourceOf(quiz, sources);
                if (source == null || source.isHintsAllowed() != quiz.isHintsAllowed()) {
                    metadataChanged = true;
                    break;
                }
                pairs.add(new QuizPair(source, quiz));
                sourceQuizIds.add(source.getId());
            }
            if (metadataChanged) {
                changed.add(revision.getRevisionOf().getId());
            } else if (!pairs.isEmpty()) {
                needingQuestionCheck.add(revision);
                pairsByRevision.put(revision.getId(), pairs);
            }
        }
        if (needingQuestionCheck.isEmpty()) {
            return;
        }
        Set<Long> lockedSourceIds = quizAttemptRepository.findSubmittedQuizIds(sourceQuizIds);
        Set<Long> unlockedQuizIds = new HashSet<>();
        for (List<QuizPair> pairs : pairsByRevision.values()) {
            for (QuizPair pair : pairs) {
                if (!lockedSourceIds.contains(pair.source().getId())) {
                    unlockedQuizIds.add(pair.source().getId());
                    unlockedQuizIds.add(pair.copy().getId());
                }
            }
        }
        if (unlockedQuizIds.isEmpty()) {
            return;
        }
        Map<Long, List<QuizQuestion>> questionsByQuiz = content.questionsByQuiz(unlockedQuizIds);
        List<QuizQuestion> allQuestions = questionsByQuiz.values().stream().flatMap(List::stream).toList();
        Map<Long, List<QuizOption>> optionsByQuestion = quizOptionRepository.findForQuestions(allQuestions);

        partitionChanged(needingQuestionCheck, changed, revision -> {
            for (QuizPair pair : pairsByRevision.getOrDefault(revision.getId(), List.of())) {
                if (lockedSourceIds.contains(pair.source().getId())) {
                    continue;
                }
                List<QuizQuestion> published = questionsByQuiz.getOrDefault(pair.source().getId(), List.of());
                List<QuizQuestion> revised = questionsByQuiz.getOrDefault(pair.copy().getId(), List.of());
                if (published.size() != revised.size()
                        || !questionContents(published, optionsByQuestion)
                                .equals(questionContents(revised, optionsByQuestion))) {
                    return true;
                }
            }
            return false;
        });
    }

    /** What learners see of a question and its answers: questions with equal contents are the same. */
    record OptionContent(String text, boolean correct, String feedback) {
    }

    record QuestionContent(QuizQuestionType type, String text, String answer, String explanation, String hint,
            Integer points, String audioUrl, String imageUrl, boolean aiDraft, List<OptionContent> options) {
    }

    static QuestionContent questionContent(QuizQuestion question, List<QuizOption> options) {
        return new QuestionContent(
                question.getQuestionType(), question.getQuestionText(), question.getCorrectAnswerText(),
                question.getExplanation(), question.getHint(), question.getPoints(),
                question.getAudioUrl(), question.getImageUrl(), question.isAiDraft(),
                options.stream()
                        .map(option -> new OptionContent(option.getOptionText(), option.isCorrectOption(),
                                option.getFeedback()))
                        .toList());
    }

    private static List<QuestionContent> questionContents(
            List<QuizQuestion> questions, Map<Long, List<QuizOption>> options) {
        List<QuestionContent> contents = new ArrayList<>(questions.size());
        for (QuizQuestion question : questions) {
            contents.add(questionContent(question, options.getOrDefault(question.getId(), List.of())));
        }
        return contents;
    }
}
