package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.FlashcardItem;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PublishableContent;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.PackageItemRepository.ClassUnit;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.projection.IdCountProjection;

import lombok.RequiredArgsConstructor;

/**
 * Reads the structure of classes: a class's units (its package items, in order), a unit's lessons (in order, never
 * revision copies), and a lesson's parts (key phrases, video, activities of the three tables in lesson order,
 * quizzes). Shared by the learner, admin and public class services.
 */
@Component
@RequiredArgsConstructor
public class ClassContentSupport {

    static final String CLASS_NOT_FOUND = "Class was not found.";
    static final String LESSON_NOT_FOUND = "Lesson was not found.";

    // Older rows recorded insertion order. Within a repeated type, preserve their original relative order.
    private static final Comparator<LessonActivity> ACTIVITY_ORDER = Comparator
            .comparingInt((LessonActivity activity) -> typeOf(activity).lessonPosition())
            .thenComparing(LessonActivity::getLessonPosition, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(LessonActivity::getId);

    private final CoursePackageRepository coursePackageRepository;
    private final PackageItemRepository packageItemRepository;
    private final LessonRepository lessonRepository;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final FlashcardRepository flashcardRepository;
    private final FlashcardItemRepository flashcardItemRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;

    CoursePackage requireClass(Long classId) {
        return coursePackageRepository.findById(classId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, CLASS_NOT_FOUND));
    }

    /** The class's units in order. */
    public List<LearningModule> units(CoursePackage coursePackage) {
        return unitsByClass(List.of(coursePackage)).getOrDefault(coursePackage.getId(), List.of());
    }

    public Map<Long, List<LearningModule>> unitsByClass(Collection<CoursePackage> classes) {
        if (classes.isEmpty()) {
            return Map.of();
        }
        List<Long> classIds = classes.stream().map(CoursePackage::getId).toList();
        return packageItemRepository.findUnitsInOrder(classIds).stream()
                .collect(Collectors.groupingBy(ClassUnit::classId, Collectors.mapping(ClassUnit::unit, Collectors.toList())));
    }

    /** The lessons of the units (never revision copies), grouped by unit and in unit order. */
    public Map<Long, List<Lesson>> lessonsByUnit(Collection<LearningModule> units) {
        if (units.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<Lesson>> result = new LinkedHashMap<>();
        units.forEach(unit -> result.put(unit.getId(), new ArrayList<>()));
        for (Lesson lesson : lessonRepository.findByModuleIdInAndRevisionOfIsNullOrderByUnitPositionAscCreatedAtAscIdAsc(
                units.stream().map(LearningModule::getId).toList())) {
            result.get(lesson.getModule().getId()).add(lesson);
        }
        return result;
    }

    /** The position of a new or moved lesson at the end of the unit. */
    int nextLessonPosition(LearningModule unit) {
        return Math.incrementExact(lessonRepository.lastPositionInUnit(unit.getId()));
    }

    List<CoursePackage> classesOfLesson(Lesson lesson) {
        return lesson.getModule() == null ? List.of() : packageItemRepository.findClassesOfUnit(lesson.getModule().getId());
    }

    /** New units have one owner. Editing a legacy shared unit or its lessons would also change the other class. */
    public CoursePackage requireClassOfLesson(Lesson lesson) {
        return requireOnly(classesOfLesson(lesson), LESSON_NOT_FOUND);
    }

    static <T> T requireOnly(List<T> parents, String missingMessage) {
        if (parents.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, missingMessage);
        }
        if (parents.size() > 1) {
            throw new ResponseStatusException(CONFLICT, "This unit belongs to multiple classes. Resolve its ownership before editing it.");
        }
        return parents.getFirst();
    }

    /** The working copy of a published lesson, while it has unpublished changes. */
    public Optional<Lesson> revisionOf(Lesson lesson) {
        return lessonRepository.findFirstByRevisionOfId(lesson.getId());
    }

    public List<LessonKeyPhrase> keyPhrases(Long lessonId) {
        return keyPhraseRepository.findByLessonIdOrderBySortOrderAscIdAsc(lessonId);
    }

    /** The lesson's key phrases as the learner and the admin see them. */
    public List<LessonViews.KeyPhrase> keyPhraseViews(Long lessonId) {
        return keyPhraseViewsOf(keyPhrases(lessonId));
    }

    static List<LessonViews.KeyPhrase> keyPhraseViewsOf(List<LessonKeyPhrase> phrases) {
        return phrases.stream().map(ClassContentSupport::keyPhraseView).toList();
    }

    /** Batch-loads key phrase views for multiple lessons in display order. */
    Map<Long, List<LessonViews.KeyPhrase>> keyPhraseViewsByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LessonViews.KeyPhrase>> result = new HashMap<>();
        for (LessonKeyPhrase phrase : keyPhraseRepository.findByLessonIdInOrderBySortOrderAscIdAsc(lessonIds)) {
            result.computeIfAbsent(phrase.getLesson().getId(), ignored -> new ArrayList<>())
                    .add(keyPhraseView(phrase));
        }
        return result;
    }

    private static LessonViews.KeyPhrase keyPhraseView(LessonKeyPhrase phrase) {
        return new LessonViews.KeyPhrase(phrase.getChinese(), phrase.getPinyin(), phrase.getMeaning());
    }

    /** The lesson's one short video (the first active one). */
    public Optional<RecordedLessonVideo> video(Long lessonId) {
        return videoRepository.findFirstByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lessonId);
    }

    public Map<Long, RecordedLessonVideo> videoByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecordedLessonVideo> result = new HashMap<>();
        videoRepository.findByLessonIdInAndActiveTrueOrderBySortOrderAscIdAsc(lessonIds)
                .forEach(video -> result.putIfAbsent(video.getLesson().getId(), video));
        return result;
    }

    /** Every activity of the lesson, in lesson order. */
    public List<LessonActivity> activities(Long lessonId) {
        return activitiesByLesson(List.of(lessonId)).getOrDefault(lessonId, List.of());
    }

    /** Activities of a single type for one lesson, avoiding scanning all three activity tables. */
    List<LessonActivity> activities(Long lessonId, ClassActivityType type) {
        List<LessonActivity> matching = new ArrayList<>(switch (type) {
            case FLASHCARDS -> flashcardRepository.findByLessonIdIn(List.of(lessonId));
            case TRACING -> tracingExerciseRepository.findByLessonIdIn(List.of(lessonId));
            case MATCHING -> matchingGameRepository.findByLessonIdIn(List.of(lessonId));
        });
        matching.sort(ACTIVITY_ORDER);
        return matching;
    }

    public Map<Long, List<LessonActivity>> activitiesByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        List<LessonActivity> all = new ArrayList<>();
        all.addAll(flashcardRepository.findByLessonIdIn(lessonIds));
        all.addAll(tracingExerciseRepository.findByLessonIdIn(lessonIds));
        all.addAll(matchingGameRepository.findByLessonIdIn(lessonIds));
        all.sort(ACTIVITY_ORDER);
        return all.stream().collect(Collectors.groupingBy(activity -> activity.getLesson().getId()));
    }

    public Optional<? extends LessonActivity> findActivity(ClassActivityType type, Long id) {
        return switch (type) {
            case FLASHCARDS -> flashcardRepository.findById(id);
            case TRACING -> tracingExerciseRepository.findById(id);
            case MATCHING -> matchingGameRepository.findById(id);
        };
    }

    public List<MatchingGameItem> matchingItems(Long gameId) {
        return matchingGameItemRepository.findInDisplayOrder(gameId);
    }

    /** The cards of a lesson's Flashcards activity, in order. */
    public List<FlashcardItem> flashcardItems(Long flashcardId) {
        return flashcardItemRepository.findByFlashcardIdOrderBySortOrderAscIdAsc(flashcardId);
    }

    /** The cards of a Flashcards activity as the editor, the preview and the learner see them (and a revision compares). */
    public List<LessonViews.KeyPhrase> cardViews(Long flashcardId) {
        return flashcardItems(flashcardId).stream()
                .map(card -> new LessonViews.KeyPhrase(card.getHanzi(), card.getPinyin(), card.getMeaning()))
                .toList();
    }

    /** Batch-loads card views for multiple Flashcards activities in display order. */
    Map<Long, List<LessonViews.KeyPhrase>> cardViewsByFlashcard(Collection<Long> flashcardIds) {
        if (flashcardIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LessonViews.KeyPhrase>> result = new HashMap<>();
        for (FlashcardItem card : flashcardItemRepository.findByFlashcardIdInOrderBySortOrderAscIdAsc(flashcardIds)) {
            result.computeIfAbsent(card.getFlashcard().getId(), ignored -> new ArrayList<>())
                    .add(new LessonViews.KeyPhrase(card.getHanzi(), card.getPinyin(), card.getMeaning()));
        }
        return result;
    }

    /** The pairs of a matching activity as the editor shows them (and a revision compares them). */
    List<LessonViews.KeyPhrase> matchPairs(Long gameId) {
        return matchingItems(gameId).stream()
                .map(item -> new LessonViews.KeyPhrase(item.getLeftText(), item.getPinyin(), item.getRightText()))
                .toList();
    }

    /** Batch-loads match pairs for multiple Matching activities in display order. */
    Map<Long, List<LessonViews.KeyPhrase>> matchPairsByGame(Collection<Long> gameIds) {
        if (gameIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<LessonViews.KeyPhrase>> result = new HashMap<>();
        for (MatchingGameItem item : matchingGameItemRepository.findInDisplayOrder(gameIds)) {
            result.computeIfAbsent(item.getMatchingGame().getId(), ignored -> new ArrayList<>())
                    .add(new LessonViews.KeyPhrase(item.getLeftText(), item.getPinyin(), item.getRightText()));
        }
        return result;
    }

    /** The editor/revision view of an activity's type-specific items (only the matching kind is populated). */
    record ActivityParts(
            List<LessonViews.TraceCharacter> characters,
            List<LessonViews.KeyPhrase> pairs,
            List<LessonViews.KeyPhrase> cards) {
    }

    ActivityParts activityParts(LessonActivity activity) {
        return switch (activity) {
            case TracingExercise tracing -> new ActivityParts(TracingCharacters.read(tracing), List.of(), List.of());
            case MatchingGame game -> new ActivityParts(List.of(), matchPairs(game.getId()), List.of());
            case Flashcard flashcards -> new ActivityParts(List.of(), List.of(), cardViews(flashcards.getId()));
            default -> throw new IllegalStateException("Unknown lesson activity " + activity);
        };
    }

    /** Every quiz of the lesson, oldest first. */
    public List<Quiz> quizzes(Long lessonId) {
        return quizRepository.findByLessonIdOrderByCreatedAtAscIdAsc(lessonId);
    }

    public Map<Long, List<Quiz>> quizzesByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return quizRepository.findByLessonIdInOrderByCreatedAtAscIdAsc(lessonIds).stream()
                .collect(Collectors.groupingBy(quiz -> quiz.getLesson().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    public List<QuizQuestion> questions(Long quizId) {
        return quizQuestionRepository.findInDisplayOrder(quizId);
    }

    /** Batch-loads questions in display order for multiple quizzes. */
    Map<Long, List<QuizQuestion>> questionsByQuiz(Collection<Long> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<QuizQuestion>> result = new HashMap<>();
        for (QuizQuestion question : quizQuestionRepository.findInDisplayOrder(quizIds)) {
            result.computeIfAbsent(question.getQuiz().getId(), ignored -> new ArrayList<>()).add(question);
        }
        return result;
    }

    Map<Long, Integer> questionCounts(Collection<Long> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        return quizQuestionRepository.countByQuiz(quizIds).stream()
                .collect(Collectors.toMap(IdCountProjection::getId, row -> Math.toIntExact(row.getTotal())));
    }

    /** Activities in different tables can have the same numeric ID. */
    public record ActivityKey(ClassActivityType type, Long id) {
        static ActivityKey of(LessonActivity activity) {
            return new ActivityKey(typeOf(activity), activity.getId());
        }
    }

    public static ClassActivityType typeOf(LessonActivity activity) {
        return switch (activity) {
            case Flashcard flashcard -> ClassActivityType.FLASHCARDS;
            case TracingExercise tracing -> ClassActivityType.TRACING;
            case MatchingGame game -> ClassActivityType.MATCHING;
            default -> throw new IllegalStateException("Unknown lesson activity " + activity);
        };
    }

    /** A new, empty activity of the type. */
    static LessonActivity newActivity(ClassActivityType type) {
        return switch (type) {
            case FLASHCARDS -> new Flashcard();
            case TRACING -> new TracingExercise();
            case MATCHING -> new MatchingGame();
        };
    }

    /** Activities and quizzes learners see: published, inside a published lesson. */
    public static boolean isLive(PublishableContent activityOrQuiz) {
        return activityOrQuiz.getContentStatus() == ContentStatus.PUBLISHED;
    }

    /** A lesson learners may open: published and not a working copy. */
    public static boolean isLessonLive(Lesson lesson) {
        return lesson.getContentStatus() == ContentStatus.PUBLISHED && lesson.getRevisionOf() == null;
    }
}
