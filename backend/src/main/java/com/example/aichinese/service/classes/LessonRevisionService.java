package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.TeacherClassViews.Change;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizOption;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.QuizAttemptRepository;
import com.example.aichinese.repository.QuizOptionRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TracingExerciseRepository;

/**
 * Lesson revisions: a published lesson stays live for students while the teacher edits a working copy (a lesson row
 * with {@code revision_of_lesson_id}). The copy holds its own key phrases, video, activities and quizzes; each copied
 * part remembers the published part it came from ({@code revision_source_id}). Approval applies the copy to the
 * published lesson part by part, so students keep their completions, video progress and quiz attempts, then deletes
 * the copy. Quiz questions that students already answered are never replaced (the editor locks them).
 */
@Service
public class LessonRevisionService {

    static final String IN_REVIEW = "This lesson is in review. You can edit it again after the admin's decision.";
    private static final String RESTORE_FIRST = "Restore the lesson before you edit it.";

    private final ClassContentSupport content;
    private final LessonRepository lessonRepository;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final FlashcardRepository flashcardRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizOptionRepository quizOptionRepository;
    private final QuizAttemptRepository quizAttemptRepository;

    public LessonRevisionService(
            ClassContentSupport content,
            LessonRepository lessonRepository,
            LessonKeyPhraseRepository keyPhraseRepository,
            RecordedLessonVideoRepository videoRepository,
            FlashcardRepository flashcardRepository,
            TracingExerciseRepository tracingExerciseRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository,
            QuizOptionRepository quizOptionRepository,
            QuizAttemptRepository quizAttemptRepository) {
        this.content = content;
        this.lessonRepository = lessonRepository;
        this.keyPhraseRepository = keyPhraseRepository;
        this.videoRepository = videoRepository;
        this.flashcardRepository = flashcardRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
        this.quizOptionRepository = quizOptionRepository;
        this.quizAttemptRepository = quizAttemptRepository;
    }

    /** The lesson the teacher edits: the lesson itself until it is first published, then its revision (created on the first change). */
    @Transactional
    public Lesson workingCopy(Lesson lesson) {
        ContentStatus status = lesson.getContentStatus();
        if (status == ContentStatus.DRAFT || status == ContentStatus.REJECTED) {
            return lesson;
        }
        if (status == ContentStatus.SUBMITTED) {
            throw new ResponseStatusException(CONFLICT, IN_REVIEW);
        }
        if (status != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(CONFLICT, RESTORE_FIRST);
        }
        Lesson revision = content.revisionOf(lesson).orElse(null);
        if (revision != null) {
            if (revision.getContentStatus() == ContentStatus.SUBMITTED) {
                throw new ResponseStatusException(CONFLICT, IN_REVIEW);
            }
            return revision;
        }
        return createRevision(lesson);
    }

    /** The copy the editor shows: the revision when there is one, else the lesson. Never creates anything. */
    @Transactional(readOnly = true)
    public Lesson displayed(Lesson lesson) {
        return content.revisionOf(lesson).orElse(lesson);
    }

    private Lesson createRevision(Lesson lesson) {
        Lesson revision = new Lesson();
        revision.setRevisionOf(lesson);
        revision.setTeacher(lesson.getTeacher());
        revision.setModule(lesson.getModule());
        revision.setLevel(lesson.getLevel());
        revision.setCategory(lesson.getCategory());
        revision.setLearningType(lesson.getLearningType());
        revision.setUnitPosition(lesson.getUnitPosition());
        copyDetails(lesson, revision);
        revision.setContentStatus(ContentStatus.DRAFT);
        Lesson saved = lessonRepository.save(revision);

        for (LessonKeyPhrase phrase : content.keyPhrases(lesson.getId())) {
            keyPhraseRepository.save(copyPhrase(phrase, saved));
        }
        for (RecordedLessonVideo video : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lesson.getId())) {
            RecordedLessonVideo copy = new RecordedLessonVideo();
            copyVideo(video, copy);
            copy.setLesson(saved);
            copy.setRevisionSourceId(video.getId());
            videoRepository.save(copy);
        }
        for (LessonActivity activity : content.activities(lesson.getId())) {
            if (ClassContentSupport.isActive(activity)) {
                LessonActivity copy = duplicateActivity(activity, saved, activity.getLessonPosition());
                copy.setRevisionSourceId(activity.getId());
                saveActivity(copy);
            }
        }
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            if (ClassContentSupport.isActive(quiz)) {
                Quiz copy = copyQuiz(quiz, saved);
                copy.setRevisionSourceId(quiz.getId());
                quizRepository.save(copy);
            }
        }
        return saved;
    }

    /** A new draft quiz on {@code lesson} with the settings and questions of {@code quiz} (also used by Duplicate). */
    @Transactional
    public Quiz copyQuiz(Quiz quiz, Lesson lesson) {
        Quiz copy = new Quiz();
        copy.setLesson(lesson);
        copy.setTeacher(quiz.getTeacher());
        copy.setTitle(quiz.getTitle());
        copy.setDescription(quiz.getDescription());
        copy.setLearningStage(quiz.getLearningStage());
        copy.setTimeLimitMinutes(quiz.getTimeLimitMinutes());
        copy.setTotalPoints(quiz.getTotalPoints());
        copy.setContentStatus(ContentStatus.DRAFT);
        Quiz saved = quizRepository.save(copy);
        List<QuizQuestion> questions = content.questions(quiz.getId());
        Map<Long, List<QuizOption>> options = quizOptionRepository.findForQuestions(questions);
        for (QuizQuestion question : questions) {
            copyQuestion(question, saved, options.getOrDefault(question.getId(), List.of()));
        }
        return saved;
    }

    /** A copy of the question (with its options) at the end of {@code quiz}'s questions. */
    @Transactional
    public QuizQuestion copyQuestion(QuizQuestion question, Quiz quiz, List<QuizOption> options) {
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
        QuizQuestion saved = quizQuestionRepository.save(copy);
        for (QuizOption option : options) {
            QuizOption optionCopy = new QuizOption();
            optionCopy.setQuestion(saved);
            optionCopy.setOptionText(option.getOptionText());
            optionCopy.setCorrectOption(option.isCorrectOption());
            optionCopy.setSortOrder(option.getSortOrder());
            quizOptionRepository.save(optionCopy);
        }
        return saved;
    }

    /** A saved draft copy of {@code activity} (with its matching pairs) at {@code position} of {@code lesson}. */
    public LessonActivity duplicateActivity(LessonActivity activity, Lesson lesson, Integer position) {
        LessonActivity copy = newActivityLike(activity);
        copyActivity(activity, copy);
        copy.setLesson(lesson);
        copy.setLessonPosition(position);
        ((PracticeActivityContent) copy).setContentStatus(ContentStatus.DRAFT);
        LessonActivity saved = saveActivity(copy);
        if (activity instanceof MatchingGame game) {
            copyMatchingItems(game, (MatchingGame) saved);
        }
        return saved;
    }

    // ----- approval and rejection -----

    /** A lesson approved for the first time: it and its parts go live. */
    @Transactional
    public void publishNewLesson(Lesson lesson) {
        lesson.setContentStatus(ContentStatus.PUBLISHED);
        lesson.setRejectionReason(null);
        lessonRepository.save(lesson);
        for (LessonActivity activity : content.activities(lesson.getId())) {
            if (ClassContentSupport.isActive(activity)) {
                ((PracticeActivityContent) activity).setContentStatus(ContentStatus.PUBLISHED);
                ((PracticeActivityContent) activity).setRejectionReason(null);
                saveActivity(activity);
            }
        }
        for (Quiz quiz : content.quizzes(lesson.getId())) {
            if (ClassContentSupport.isActive(quiz)) {
                quiz.setContentStatus(ContentStatus.PUBLISHED);
                quiz.setRejectionReason(null);
                quizRepository.save(quiz);
            }
        }
    }

    /** Applies an approved revision to its published lesson, then deletes the revision. */
    @Transactional
    public Lesson applyRevision(Lesson revision) {
        Lesson lesson = revision.getRevisionOf();
        copyDetails(revision, lesson);
        lesson.setRejectionReason(null);
        lessonRepository.save(lesson);

        deleteKeyPhrases(lesson.getId());
        for (LessonKeyPhrase phrase : content.keyPhrases(revision.getId())) {
            phrase.setLesson(lesson);
            keyPhraseRepository.save(phrase);
        }
        keyPhraseRepository.flush();

        applyVideos(revision, lesson);
        applyActivities(revision, lesson);
        applyQuizzes(revision, lesson);
        quizQuestionRepository.flush();

        deleteLessonTree(revision);
        return lesson;
    }

    private void applyVideos(Lesson revision, Lesson lesson) {
        Map<Long, RecordedLessonVideo> live = videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lesson.getId())
                .stream().collect(Collectors.toMap(RecordedLessonVideo::getId, video -> video));
        Set<Long> kept = new HashSet<>();
        for (RecordedLessonVideo copy : videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(revision.getId())) {
            RecordedLessonVideo source = copy.getRevisionSourceId() == null ? null : live.get(copy.getRevisionSourceId());
            if (source != null) {
                copyVideo(copy, source);
                source.setRevisionSourceId(null);
                videoRepository.save(source);
                kept.add(source.getId());
            } else {
                copy.setLesson(lesson);
                copy.setRevisionSourceId(null);
                videoRepository.save(copy);
                kept.add(copy.getId());
            }
        }
        live.values().stream().filter(video -> !kept.contains(video.getId())).forEach(video -> {
            video.setActive(false);
            videoRepository.save(video);
        });
    }

    private void applyActivities(Lesson revision, Lesson lesson) {
        Map<Long, LessonActivity> live = new HashMap<>();
        content.activities(lesson.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .forEach(activity -> live.put(key(activity), activity));
        Set<Long> kept = new HashSet<>();
        for (LessonActivity copy : content.activities(revision.getId())) {
            if (!ClassContentSupport.isActive(copy)) {
                continue;
            }
            LessonActivity source = copy.getRevisionSourceId() == null
                    ? null
                    : live.get(key(ClassContentSupport.typeOf(copy).ordinal(), copy.getRevisionSourceId()));
            if (source != null) {
                copyActivity(copy, source);
                source.setLessonPosition(copy.getLessonPosition());
                saveActivity(source);
                if (copy instanceof MatchingGame game) {
                    deleteMatchingItems(source.getId());
                    copyMatchingItems(game, (MatchingGame) source);
                }
                kept.add(key(source));
            } else {
                copy.setLesson(lesson);
                copy.setRevisionSourceId(null);
                ((PracticeActivityContent) copy).setContentStatus(ContentStatus.PUBLISHED);
                saveActivity(copy);
                kept.add(key(copy));
            }
        }
        live.forEach((activityKey, activity) -> {
            if (!kept.contains(activityKey)) {
                ((PracticeActivityContent) activity).setContentStatus(ContentStatus.ARCHIVED);
                saveActivity(activity);
            }
        });
    }

    private void applyQuizzes(Lesson revision, Lesson lesson) {
        Map<Long, Quiz> live = content.quizzes(lesson.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .collect(Collectors.toMap(Quiz::getId, quiz -> quiz));
        Set<Long> kept = new HashSet<>();
        for (Quiz copy : content.quizzes(revision.getId())) {
            if (!ClassContentSupport.isActive(copy)) {
                continue;
            }
            Quiz source = copy.getRevisionSourceId() == null ? null : live.get(copy.getRevisionSourceId());
            if (source != null) {
                source.setTitle(copy.getTitle());
                source.setDescription(copy.getDescription());
                source.setLearningStage(copy.getLearningStage());
                source.setTimeLimitMinutes(copy.getTimeLimitMinutes());
                if (!quizAttemptRepository.existsByQuizIdAndSubmittedTrue(source.getId())) {
                    deleteQuestions(source.getId());
                    for (QuizQuestion question : content.questions(copy.getId())) {
                        question.setQuiz(source);
                        quizQuestionRepository.save(question);
                    }
                    source.setTotalPoints(copy.getTotalPoints());
                }
                quizRepository.save(source);
                kept.add(source.getId());
            } else {
                copy.setLesson(lesson);
                copy.setRevisionSourceId(null);
                copy.setContentStatus(ContentStatus.PUBLISHED);
                quizRepository.save(copy);
                kept.add(copy.getId());
            }
        }
        live.values().stream().filter(quiz -> !kept.contains(quiz.getId())).forEach(quiz -> {
            quiz.setStatusBeforeArchive(quiz.getContentStatus());
            quiz.setContentStatus(ContentStatus.ARCHIVED);
            quizRepository.save(quiz);
        });
    }

    /**
     * Deletes a lesson that students never saw (a revision copy or a draft of a deleted class) with every part of
     * it. Quizzes and activities that were moved to another lesson are no longer found here.
     */
    @Transactional
    public void deleteLessonTree(Lesson lesson) {
        deleteKeyPhrases(lesson.getId());
        videoRepository.deleteAll(videoRepository.findByLessonId(lesson.getId()));
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

    void deleteQuestions(Long quizId) {
        for (QuizQuestion question : content.questions(quizId)) {
            deleteQuestion(question);
        }
        quizQuestionRepository.flush();
    }

    /** Deletes a question with its options. */
    void deleteQuestion(QuizQuestion question) {
        deleteOptions(question);
        quizQuestionRepository.delete(question);
    }

    void deleteOptions(QuizQuestion question) {
        quizOptionRepository.deleteAll(quizOptionRepository.findForQuestions(List.of(question))
                .getOrDefault(question.getId(), List.of()));
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

    // ----- what changed -----

    /** The changes a revision makes to its published lesson, for the admin review and the class history. */
    @Transactional(readOnly = true)
    public List<Change> changes(Lesson revision) {
        Lesson lesson = revision.getRevisionOf();
        List<Change> changes = new ArrayList<>();
        if (!Objects.equals(lesson.getTitle(), revision.getTitle())
                || !Objects.equals(lesson.getSummary(), revision.getSummary())
                || !Objects.equals(lesson.getEstimatedDurationMinutes(), revision.getEstimatedDurationMinutes())) {
            changes.add(new Change("DETAILS", null, null, null));
        }
        phraseChanges(content.keyPhrases(lesson.getId()), content.keyPhrases(revision.getId()), changes);
        videoChanges(lesson, revision, changes);
        activityChanges(lesson, revision, changes);
        quizChanges(lesson, revision, changes);
        return changes;
    }

    private static void phraseChanges(List<LessonKeyPhrase> before, List<LessonKeyPhrase> after, List<Change> changes) {
        Map<String, LessonKeyPhrase> beforeByChinese = new HashMap<>();
        before.forEach(phrase -> beforeByChinese.put(phrase.getChinese(), phrase));
        Map<String, LessonKeyPhrase> afterByChinese = new HashMap<>();
        after.forEach(phrase -> afterByChinese.put(phrase.getChinese(), phrase));
        int added = (int) after.stream().filter(phrase -> !beforeByChinese.containsKey(phrase.getChinese())).count();
        int removed = (int) before.stream().filter(phrase -> !afterByChinese.containsKey(phrase.getChinese())).count();
        int changed = (int) after.stream().filter(phrase -> {
            LessonKeyPhrase old = beforeByChinese.get(phrase.getChinese());
            return old != null && (!Objects.equals(old.getPinyin(), phrase.getPinyin())
                    || !Objects.equals(old.getMeaning(), phrase.getMeaning()));
        }).count();
        if (added > 0) {
            changes.add(new Change("KEY_PHRASES_ADDED", null, added, after.size()));
        }
        if (removed > 0) {
            changes.add(new Change("KEY_PHRASES_REMOVED", null, removed, after.size()));
        }
        if (changed > 0) {
            changes.add(new Change("KEY_PHRASES_CHANGED", null, changed, after.size()));
        }
    }

    private void videoChanges(Lesson lesson, Lesson revision, List<Change> changes) {
        RecordedLessonVideo before = content.video(lesson.getId()).orElse(null);
        RecordedLessonVideo after = content.video(revision.getId()).orElse(null);
        if (before == null && after != null) {
            changes.add(new Change("VIDEO_ADDED", after.getTitle(), null, null));
        } else if (before != null && after == null) {
            changes.add(new Change("VIDEO_REMOVED", before.getTitle(), null, null));
        } else if (before != null && !Objects.equals(after.getRevisionSourceId(), before.getId())) {
            changes.add(new Change("VIDEO_REPLACED", after.getTitle(), null, null));
        } else if (before != null && (!Objects.equals(before.getTitle(), after.getTitle())
                || !Objects.equals(before.getExternalVideoUrl(), after.getExternalVideoUrl())
                || !Objects.equals(before.getUploadedFile(), after.getUploadedFile()))) {
            changes.add(new Change("VIDEO_CHANGED", after.getTitle(), null, null));
        }
    }

    private void activityChanges(Lesson lesson, Lesson revision, List<Change> changes) {
        Map<Long, LessonActivity> before = new HashMap<>();
        content.activities(lesson.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .forEach(activity -> before.put(key(activity), activity));
        Set<Long> kept = new HashSet<>();
        for (LessonActivity activity : content.activities(revision.getId())) {
            if (!ClassContentSupport.isActive(activity)) {
                continue;
            }
            LessonActivity source = activity.getRevisionSourceId() == null
                    ? null
                    : before.get(key(ClassContentSupport.typeOf(activity).ordinal(), activity.getRevisionSourceId()));
            if (source == null) {
                changes.add(new Change("ACTIVITY_ADDED", activity.getTitle(), null, null));
                continue;
            }
            kept.add(key(source));
            if (!activityFingerprint(source).equals(activityFingerprint(activity))) {
                changes.add(new Change("ACTIVITY_CHANGED", activity.getTitle(), null, null));
            }
        }
        before.forEach((activityKey, activity) -> {
            if (!kept.contains(activityKey)) {
                changes.add(new Change("ACTIVITY_REMOVED", activity.getTitle(), null, null));
            }
        });
    }

    private void quizChanges(Lesson lesson, Lesson revision, List<Change> changes) {
        Map<Long, Quiz> before = content.quizzes(lesson.getId()).stream()
                .filter(ClassContentSupport::isActive)
                .collect(Collectors.toMap(Quiz::getId, quiz -> quiz));
        Set<Long> kept = new HashSet<>();
        for (Quiz quiz : content.quizzes(revision.getId())) {
            if (!ClassContentSupport.isActive(quiz)) {
                continue;
            }
            List<QuizQuestion> questions = content.questions(quiz.getId());
            Quiz source = quiz.getRevisionSourceId() == null ? null : before.get(quiz.getRevisionSourceId());
            if (source == null) {
                changes.add(new Change("QUIZ_ADDED", quiz.getTitle(), questions.size(), questions.size()));
                continue;
            }
            kept.add(source.getId());
            if (!Objects.equals(source.getTitle(), quiz.getTitle())
                    || !Objects.equals(source.getLearningStage(), quiz.getLearningStage())
                    || !Objects.equals(source.getTimeLimitMinutes(), quiz.getTimeLimitMinutes())) {
                changes.add(new Change("QUIZ_SETTINGS", quiz.getTitle(), null, null));
            }
            List<String> beforeQuestions = content.questions(source.getId()).stream().map(this::questionFingerprint).toList();
            List<String> afterQuestions = questions.stream().map(this::questionFingerprint).toList();
            int added = (int) afterQuestions.stream().filter(fingerprint -> !beforeQuestions.contains(fingerprint)).count();
            int removed = (int) beforeQuestions.stream().filter(fingerprint -> !afterQuestions.contains(fingerprint)).count();
            if (added > 0 && added > removed) {
                changes.add(new Change("QUESTIONS_ADDED", quiz.getTitle(), added - removed, questions.size()));
            }
            if (removed > added) {
                changes.add(new Change("QUESTIONS_REMOVED", quiz.getTitle(), removed - added, questions.size()));
            }
            int edited = Math.min(added, removed);
            if (edited > 0) {
                changes.add(new Change("QUESTIONS_CHANGED", quiz.getTitle(), edited, questions.size()));
            }
        }
        before.values().stream()
                .filter(quiz -> !kept.contains(quiz.getId()))
                .forEach(quiz -> changes.add(new Change("QUIZ_REMOVED", quiz.getTitle(), null, null)));
    }

    public static boolean touchesQuiz(List<Change> changes) {
        return changes.stream().anyMatch(change -> change.kind().startsWith("QUIZ") || change.kind().startsWith("QUESTIONS"));
    }

    private String questionFingerprint(QuizQuestion question) {
        String options = quizOptionRepository.findForQuestions(List.of(question))
                .getOrDefault(question.getId(), List.of()).stream()
                .map(option -> option.getOptionText() + (option.isCorrectOption() ? "*" : ""))
                .collect(Collectors.joining("|"));
        return String.join("\u0001", question.getQuestionType(), question.getQuestionText(),
                String.valueOf(question.getCorrectAnswerText()), String.valueOf(question.getExplanation()),
                String.valueOf(question.getHint()), String.valueOf(question.getPoints()), options);
    }

    private String activityFingerprint(LessonActivity activity) {
        StringBuilder builder = new StringBuilder(activity.getTitle()).append('\u0001').append(stageOf(activity));
        if (activity instanceof TracingExercise tracing) {
            builder.append(tracing.getTargetCharacter()).append(tracing.getPinyin()).append(tracing.getMeaning());
        }
        if (activity instanceof MatchingGame game) {
            content.matchingItems(game.getId()).forEach(item -> builder.append(item.getLeftText())
                    .append('=').append(item.getRightText()).append('/').append(item.getPinyin()).append(';'));
        }
        return builder.toString();
    }

    private static String stageOf(LessonActivity activity) {
        var stage = ((PracticeActivityContent) activity).getLearningStage();
        return stage == null ? null : stage.name();
    }

    // ----- copying -----

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

    private static void copyVideo(RecordedLessonVideo from, RecordedLessonVideo to) {
        to.setCoursePackage(from.getCoursePackage());
        to.setUploadedFile(from.getUploadedFile());
        to.setSourceType(from.getSourceType());
        to.setTitle(from.getTitle());
        to.setDescription(from.getDescription());
        to.setExternalVideoUrl(from.getExternalVideoUrl());
        to.setDurationSeconds(from.getDurationSeconds());
        to.setSortOrder(from.getSortOrder());
        to.setActive(from.isActive());
    }

    private static LessonActivity newActivityLike(LessonActivity activity) {
        if (activity instanceof Flashcard) {
            return new Flashcard();
        }
        if (activity instanceof TracingExercise) {
            return new TracingExercise();
        }
        return new MatchingGame();
    }

    /** Copies the content of an activity (not its id, lesson, position, status or revision link). */
    private static void copyActivity(LessonActivity from, LessonActivity to) {
        PracticeActivityContent source = (PracticeActivityContent) from;
        PracticeActivityContent target = (PracticeActivityContent) to;
        target.setTitle(source.getTitle());
        target.setCreatedByUser(source.getCreatedByUser());
        target.setLearningStage(source.getLearningStage());
        target.setHskLevel(source.getHskLevel());
        target.setDifficulty(source.getDifficulty());
        target.setTopic(source.getTopic());
        target.setTags(source.getTags());
        target.setEstimatedSeconds(source.getEstimatedSeconds());
        target.setXpReward(source.getXpReward());
        if (from instanceof Flashcard card && to instanceof Flashcard copy) {
            copy.setFrontText(card.getFrontText());
            copy.setBackText(card.getBackText());
            copy.setPinyin(card.getPinyin());
            copy.setEnglishMeaning(card.getEnglishMeaning());
            copy.setSortOrder(card.getSortOrder());
        }
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

    LessonActivity saveActivity(LessonActivity activity) {
        if (activity instanceof Flashcard card) {
            return flashcardRepository.save(card);
        }
        if (activity instanceof TracingExercise tracing) {
            return tracingExerciseRepository.save(tracing);
        }
        return matchingGameRepository.save((MatchingGame) activity);
    }

    void deleteActivity(LessonActivity activity) {
        if (activity instanceof Flashcard card) {
            flashcardRepository.delete(card);
        } else if (activity instanceof TracingExercise tracing) {
            tracingExerciseRepository.delete(tracing);
        } else {
            deleteMatchingItems(activity.getId());
            matchingGameRepository.delete((MatchingGame) activity);
        }
    }

    /** Activities of the three types share one id space per type only, so keys combine the type and the id. */
    private static long key(LessonActivity activity) {
        return key(ClassContentSupport.typeOf(activity).ordinal(), activity.getId());
    }

    private static long key(int typeOrdinal, Long id) {
        return id * 4 + typeOrdinal;
    }
}
