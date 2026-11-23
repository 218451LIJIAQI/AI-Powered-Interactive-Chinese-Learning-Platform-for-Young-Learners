package com.example.aichinese.service.classes;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.FlashcardItem;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.classes.ClassContentSupport.ActivityKey;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;

/**
 * Unpublished changes of a published lesson: learners keep the published lesson while an admin edits a working copy (a
 * lesson row with {@code revision_of_lesson_id}). The copy holds its own key phrases, video, activities and quizzes;
 * each copied part remembers the published part it came from ({@code revision_source_id}). Publishing applies the copy
 * to the published lesson part by part, so learners keep their completions, video progress and quiz attempts, then
 * deletes the copy. The questions of a quiz with a submitted attempt are never replaced (the editor locks them, and
 * publishing takes a write lock on the quiz row to serialize with in-flight learner submits); changed questions of any
 * other quiz replace the published ones, and answers saved in attempts still open go with them. A working copy only
 * adds or changes activities and quizzes, it never removes one.
 */
@Service
@RequiredArgsConstructor
public class LessonRevisionService {

    private final ClassContentSupport content;
    private final LessonRevisionDiff diff;
    private final LessonRepository lessonRepository;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final EntityManager entityManager;
    private final FlashcardItemRepository flashcardItemRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;
    private final QuizAttemptAnswerRepository quizAttemptAnswerRepository;
    private final FileStorageService fileStorage;

    /** Creates the first working copy of a published lesson under the editor's class lock. */
    @Transactional
    Lesson createRevision(Lesson lesson) {
        Lesson revision = new Lesson();
        revision.setRevisionOf(lesson);
        revision.setModule(lesson.getModule());
        revision.setUnitPosition(lesson.getUnitPosition());
        copyDetails(lesson, revision);
        revision.setContentStatus(ContentStatus.DRAFT);
        Lesson saved = lessonRepository.save(revision);

        for (LessonKeyPhrase phrase : content.keyPhrases(lesson.getId())) {
            keyPhraseRepository.save(copyPhrase(phrase, saved));
        }
        for (RecordedLessonVideo video : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lesson.getId())) {
            copyVideo(video, saved);
        }
        for (LessonActivity activity : content.activities(lesson.getId())) {
            copyActivityToRevision(activity, saved);
        }
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            copyQuizToRevision(quiz, saved);
        }
        return saved;
    }

    /** The copy carries the questions, the hint setting and the name. */
    private void copyQuizToRevision(Quiz quiz, Lesson revision) {
        Quiz copy = new Quiz();
        copy.setLesson(revision);
        copy.setTitle(quiz.getTitle());
        copy.setHintsAllowed(quiz.isHintsAllowed());
        copy.setRevisionSource(quiz);
        copy.setContentStatus(ContentStatus.DRAFT);
        Quiz saved = quizRepository.save(copy);
        List<QuizQuestion> questions = content.questions(quiz.getId());
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(questions);
        for (QuizQuestion question : questions) {
            copyQuestion(question, saved, options.getOrDefault(question.getId(), List.of()));
        }
    }

    private void copyQuestion(QuizQuestion question, Quiz quiz, List<QuizOption> options) {
        QuizQuestion copy = new QuizQuestion();
        copy.setQuiz(quiz);
        copy.setQuestionType(question.getQuestionType());
        copy.setQuestionText(question.getQuestionText());
        copy.setCorrectAnswerText(question.getCorrectAnswerText());
        copy.setExplanation(question.getExplanation());
        copy.setHint(question.getHint());
        copy.setAudioUrl(question.getAudioUrl());
        copy.setImageUrl(question.getImageUrl());
        copy.setPoints(question.getPoints());
        copy.setSortOrder(question.getSortOrder());
        copy.setAiDraft(question.isAiDraft());
        QuizQuestion saved = quizQuestionRepository.save(copy);
        for (QuizOption option : options) {
            QuizOption optionCopy = new QuizOption();
            optionCopy.setQuestion(saved);
            optionCopy.setOptionText(option.getOptionText());
            optionCopy.setCorrectOption(option.isCorrectOption());
            optionCopy.setFeedback(option.getFeedback());
            optionCopy.setSortOrder(option.getSortOrder());
            quizOptionRepository.save(optionCopy);
        }
    }

    /** A draft copy of {@code activity} (with its matching pairs or flashcards) in the working copy. */
    private void copyActivityToRevision(LessonActivity activity, Lesson revision) {
        LessonActivity copy = ClassContentSupport.newActivity(ClassContentSupport.typeOf(activity));
        copyActivity(activity, copy);
        copy.setLesson(revision);
        copy.setLessonPosition(activity.getLessonPosition());
        copy.setRevisionSourceId(activity.getId());
        copy.setContentStatus(ContentStatus.DRAFT);
        entityManager.persist(copy);
        if (activity instanceof MatchingGame game) {
            copyMatchingItems(game, (MatchingGame) copy);
        }
        if (activity instanceof Flashcard flashcards) {
            copyFlashcardItems(flashcards, (Flashcard) copy);
        }
    }

    /** A lesson published for the first time: it and its parts go live. */
    @Transactional
    public void publishNewLesson(Lesson lesson) {
        lesson.setContentStatus(ContentStatus.PUBLISHED);
        for (LessonActivity activity : content.activities(lesson.getId())) {
            activity.setContentStatus(ContentStatus.PUBLISHED);
        }
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            quiz.setContentStatus(ContentStatus.PUBLISHED);
        }
    }

    /**
     * Publishes the unpublished changes of a lesson: applies its working copy to it, then deletes the copy.
     * Returns {@code true} when at least one learner-visible change was applied.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public boolean applyRevision(Lesson revision) {
        Lesson lesson = revision.getRevisionOf();
        boolean changed = LessonRevisionDiff.detailsChanged(lesson, revision);
        copyDetails(revision, lesson);

        List<LessonKeyPhrase> revisionPhrases = content.keyPhrases(revision.getId());
        if (!content.keyPhraseViews(lesson.getId()).equals(ClassContentSupport.keyPhraseViewsOf(revisionPhrases))) {
            changed = true;
            deleteKeyPhrases(lesson.getId());
            for (LessonKeyPhrase phrase : revisionPhrases) {
                phrase.setLesson(lesson);
            }
            keyPhraseRepository.flush();
        }

        changed |= applyVideos(revision, lesson);
        changed |= applyActivities(revision, lesson);
        changed |= applyQuizzes(revision, lesson);
        quizQuestionRepository.flush();
        if (changed) {
            lesson.setUpdatedAt(LocalDateTime.now());
        }

        deleteLessonTree(revision);
        return changed;
    }

    /** A copy still points at the published video it came from; a new video replaces that one. */
    private boolean applyVideos(Lesson revision, Lesson lesson) {
        boolean changed = diff.videoChanged(lesson, revision);
        Set<Long> kept = new HashSet<>();
        for (RecordedLessonVideo copy : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(revision.getId())) {
            if (copy.getRevisionSourceId() != null) {
                kept.add(copy.getRevisionSourceId());
            } else {
                copy.setLesson(lesson);
                kept.add(copy.getId());
            }
        }
        for (RecordedLessonVideo video : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lesson.getId())) {
            if (!kept.contains(video.getId())) {
                video.setActive(false);
            }
        }
        return changed;
    }

    private boolean applyActivities(Lesson revision, Lesson lesson) {
        Map<ActivityKey, LessonActivity> sources = LessonRevisionDiff.indexActivities(content.activities(lesson.getId()));
        boolean changed = false;
        for (LessonActivity copy : content.activities(revision.getId())) {
            LessonActivity source = LessonRevisionDiff.sourceOf(copy, sources);
            if (source != null) {
                boolean metaOrTracingChanged = LessonRevisionDiff.activityMetaOrTracingChanged(source, copy);
                copyActivity(copy, source);
                boolean itemsChanged = false;
                if (copy instanceof MatchingGame game
                        && !content.matchPairs(source.getId()).equals(content.matchPairs(game.getId()))) {
                    deleteMatchingItems(source.getId());
                    copyMatchingItems(game, (MatchingGame) source);
                    itemsChanged = true;
                }
                if (copy instanceof Flashcard flashcards
                        && !content.cardViews(source.getId()).equals(content.cardViews(flashcards.getId()))) {
                    deleteFlashcardItems(source.getId());
                    copyFlashcardItems(flashcards, (Flashcard) source);
                    itemsChanged = true;
                }
                changed |= metaOrTracingChanged || itemsChanged;
            } else {
                copy.setLesson(lesson);
                copy.setContentStatus(ContentStatus.PUBLISHED);
                changed = true;
            }
        }
        return changed;
    }

    private boolean applyQuizzes(Lesson revision, Lesson lesson) {
        Map<Long, Quiz> sources = LessonRevisionDiff.indexQuizzes(content.quizzes(lesson.getId()));
        boolean changed = false;
        for (Quiz copy : content.quizzes(revision.getId())) {
            Quiz source = LessonRevisionDiff.sourceOf(copy, sources);
            if (source == null) {
                copy.setLesson(lesson);
                copy.setContentStatus(ContentStatus.PUBLISHED);
                changed = true;
                continue;
            }
            if (!questionsLocked(source) && !diff.sameQuestions(source, copy)) {
                // Serialize question replacement with in-flight learner submits (which take PESSIMISTIC_READ on the quiz).
                entityManager.lock(source, LockModeType.PESSIMISTIC_WRITE);
                if (!questionsLocked(source)) {
                    replaceQuestions(source, copy);
                    changed = true;
                }
            }
            // The hint setting is not a question: it is applied to a quiz learners have taken too.
            if (source.isHintsAllowed() != copy.isHintsAllowed()) {
                source.setHintsAllowed(copy.isHintsAllowed());
                changed = true;
            }
        }
        return changed;
    }

    /**
     * Questions learners already answered stay as they are: those of a quiz with a submitted attempt, and those of a
     * working copy of such a quiz (the editor locks them, and publishing never replaces them).
     */
    boolean questionsLocked(Quiz quiz) {
        Long publishedId = quiz.getRevisionSourceId() == null ? quiz.getId() : quiz.getRevisionSourceId();
        return quizAttemptRepository.existsByQuizIdAndSubmittedTrue(publishedId);
    }

    /**
     * Replaces the questions of a quiz without a submitted attempt while holding {@link LockModeType#PESSIMISTIC_WRITE}
     * on {@code source}; any answers saved in attempts still open go with the old questions.
     */
    private void replaceQuestions(Quiz source, Quiz copy) {
        quizAttemptAnswerRepository.deleteUnsubmittedByQuizId(source.getId());
        quizAttemptAnswerRepository.flush();
        deleteQuestions(source.getId());
        for (QuizQuestion question : content.questions(copy.getId())) {
            question.setQuiz(source);
        }
    }

    /**
     * Deletes a lesson that learners never saw (a working copy, or a draft of a deleted class) with every part of it.
     * Quizzes and activities that were moved to another lesson are no longer found here.
     */
    @Transactional
    public void deleteLessonTree(Lesson lesson) {
        deleteKeyPhrases(lesson.getId());
        removeDraftVideos(lesson);
        for (LessonActivity activity : content.activities(lesson.getId())) {
            deleteActivity(activity);
        }
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            deleteQuestions(quiz.getId());
            quizRepository.delete(quiz);
        }
        lessonRepository.flush();
        lessonRepository.delete(lesson);
    }

    /** Edits affect an unpublished draft or working copy. Published video rows retain their learner progress. */
    void removeDraftVideos(Lesson draft) {
        List<RecordedLessonVideo> videos = videoRepository.findByLessonId(draft.getId());
        List<UploadedFile> files = videos.stream().map(RecordedLessonVideo::getUploadedFile)
                .filter(Objects::nonNull).distinct().toList();
        videoRepository.deleteAll(videos);
        files.forEach(fileStorage::deleteUnusedRecordedVideo);
    }

    private void deleteQuestions(Long quizId) {
        List<QuizQuestion> questions = content.questions(quizId);
        deleteOptions(questions);
        quizQuestionRepository.deleteAll(questions);
        quizQuestionRepository.flush();
    }

    /** Deletes a question with its options. */
    void deleteQuestion(QuizQuestion question) {
        deleteOptions(List.of(question));
        quizQuestionRepository.delete(question);
    }

    void deleteOptions(List<QuizQuestion> questions) {
        if (questions.isEmpty()) {
            return;
        }
        quizOptionRepository.deleteAll(quizOptionRepository.findInDisplayOrder(
                questions.stream().map(QuizQuestion::getId).toList()));
        // Delete the options before the questions or before inserting their replacement options.
        quizOptionRepository.flush();
    }

    void deleteKeyPhrases(Long lessonId) {
        keyPhraseRepository.deleteAll(content.keyPhrases(lessonId));
        keyPhraseRepository.flush();
    }

    void deleteMatchingItems(Long gameId) {
        matchingGameItemRepository.deleteAll(content.matchingItems(gameId));
        matchingGameItemRepository.flush();
    }

    void deleteFlashcardItems(Long flashcardId) {
        flashcardItemRepository.deleteAll(content.flashcardItems(flashcardId));
        flashcardItemRepository.flush();
    }

    /** Whether a working copy differs from its published lesson in anything learners would see. */
    @Transactional(readOnly = true)
    public boolean hasChanges(Lesson revision) {
        return diff.hasChanges(revision);
    }

    /** Published lesson ids whose working copy differs from the live lesson. */
    @Transactional(readOnly = true)
    public Set<Long> changedLessonIds(Collection<Lesson> revisions) {
        return diff.changedLessonIds(revisions);
    }

    /** Whether the published order can still identify the corresponding questions in an untouched copy. */
    boolean sameQuestions(List<QuizQuestion> first, List<QuizQuestion> second) {
        return diff.sameQuestions(first, second);
    }

    private static void copyDetails(Lesson from, Lesson to) {
        to.setTitle(from.getTitle());
        to.setSummary(from.getSummary());
        to.setEstimatedDurationMinutes(from.getEstimatedDurationMinutes());
    }

    private static LessonKeyPhrase copyPhrase(LessonKeyPhrase phrase, Lesson lesson) {
        LessonKeyPhrase copy = new LessonKeyPhrase();
        copy.setLesson(lesson);
        copy.setChinese(phrase.getChinese());
        copy.setPinyin(phrase.getPinyin());
        copy.setMeaning(phrase.getMeaning());
        copy.setSortOrder(phrase.getSortOrder());
        return copy;
    }

    private void copyVideo(RecordedLessonVideo video, Lesson revision) {
        RecordedLessonVideo copy = new RecordedLessonVideo();
        copy.setLesson(revision);
        copy.setRevisionSource(video);
        copy.setUploadedFile(video.getUploadedFile());
        copy.setSourceType(video.getSourceType());
        copy.setTitle(video.getTitle());
        copy.setExternalVideoUrl(video.getExternalVideoUrl());
        copy.setDurationSeconds(video.getDurationSeconds());
        copy.setSortOrder(video.getSortOrder());
        copy.setActive(true);
        videoRepository.save(copy);
    }

    /** Copies the content of an activity (not its id, lesson, position, status or revision link). */
    private static void copyActivity(LessonActivity from, LessonActivity to) {
        to.setTitle(from.getTitle());
        to.setCreatedByUser(from.getCreatedByUser());
        to.setLearningStage(from.getLearningStage());
        if (from instanceof TracingExercise tracing && to instanceof TracingExercise copy) {
            copy.setTargetCharacter(tracing.getTargetCharacter());
            copy.setPinyin(tracing.getPinyin());
            copy.setMeaning(tracing.getMeaning());
        }
    }

    private void copyMatchingItems(MatchingGame from, MatchingGame to) {
        for (MatchingGameItem item : content.matchingItems(from.getId())) {
            MatchingGameItem copy = new MatchingGameItem();
            copy.setMatchingGame(to);
            copy.setLeftText(item.getLeftText());
            copy.setRightText(item.getRightText());
            copy.setPinyin(item.getPinyin());
            copy.setSortOrder(item.getSortOrder());
            matchingGameItemRepository.save(copy);
        }
    }

    private void copyFlashcardItems(Flashcard from, Flashcard to) {
        for (FlashcardItem item : content.flashcardItems(from.getId())) {
            FlashcardItem copy = new FlashcardItem();
            copy.setFlashcard(to);
            copy.setHanzi(item.getHanzi());
            copy.setPinyin(item.getPinyin());
            copy.setMeaning(item.getMeaning());
            copy.setSortOrder(item.getSortOrder());
            flashcardItemRepository.save(copy);
        }
    }

    private void deleteActivity(LessonActivity activity) {
        if (activity instanceof Flashcard) {
            deleteFlashcardItems(activity.getId());
        } else if (activity instanceof MatchingGame) {
            deleteMatchingItems(activity.getId());
        }
        entityManager.remove(activity);
    }
}
