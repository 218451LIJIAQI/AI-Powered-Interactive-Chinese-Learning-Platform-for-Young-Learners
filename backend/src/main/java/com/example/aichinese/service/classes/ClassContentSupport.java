package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.LearningModule;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LessonKeyPhrase;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.PackageItem;
import com.example.aichinese.entity.Quiz;
import com.example.aichinese.entity.QuizQuestion;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PackageItemType;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LearningModuleRepository;
import com.example.aichinese.repository.LessonKeyPhraseRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PackageItemRepository;
import com.example.aichinese.repository.QuizQuestionRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TracingExerciseRepository;

/**
 * Reads the structure of classes: a class's units (package items of type MODULE, in order), a unit's lessons (in
 * order, never revision copies), and a lesson's parts (key phrases, video, activities of the three tables in lesson
 * order, quizzes). Shared by the student, teacher, admin and public class services.
 */
@Component
public class ClassContentSupport {

    static final String CLASS_NOT_FOUND = "Class was not found.";
    static final String LESSON_NOT_FOUND = "Lesson was not found.";

    private final PackageItemRepository packageItemRepository;
    private final LearningModuleRepository learningModuleRepository;
    private final LessonRepository lessonRepository;
    private final LessonKeyPhraseRepository keyPhraseRepository;
    private final RecordedLessonVideoRepository videoRepository;
    private final FlashcardRepository flashcardRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;

    public ClassContentSupport(
            PackageItemRepository packageItemRepository,
            LearningModuleRepository learningModuleRepository,
            LessonRepository lessonRepository,
            LessonKeyPhraseRepository keyPhraseRepository,
            RecordedLessonVideoRepository videoRepository,
            FlashcardRepository flashcardRepository,
            TracingExerciseRepository tracingExerciseRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            QuizRepository quizRepository,
            QuizQuestionRepository quizQuestionRepository) {
        this.packageItemRepository = packageItemRepository;
        this.learningModuleRepository = learningModuleRepository;
        this.lessonRepository = lessonRepository;
        this.keyPhraseRepository = keyPhraseRepository;
        this.videoRepository = videoRepository;
        this.flashcardRepository = flashcardRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.quizRepository = quizRepository;
        this.quizQuestionRepository = quizQuestionRepository;
    }

    // ----- structure -----

    /** The class's units in order; archived units only when asked for. */
    public List<LearningModule> units(CoursePackage coursePackage, boolean includeArchived) {
        return unitsByClass(List.of(coursePackage), includeArchived).getOrDefault(coursePackage.getId(), List.of());
    }

    public Map<Long, List<LearningModule>> unitsByClass(Collection<CoursePackage> classes, boolean includeArchived) {
        if (classes.isEmpty()) {
            return Map.of();
        }
        List<PackageItem> items = packageItemRepository.findByCoursePackageIdInAndItemTypeOrderBySortOrderAscIdAsc(
                classes.stream().map(CoursePackage::getId).toList(), PackageItemType.MODULE);
        Map<Long, LearningModule> modules = learningModuleRepository
                .findAllById(items.stream().map(PackageItem::getItemId).toList()).stream()
                .collect(Collectors.toMap(LearningModule::getId, Function.identity()));
        Map<Long, List<LearningModule>> result = new HashMap<>();
        for (PackageItem item : items) {
            LearningModule unit = modules.get(item.getItemId());
            if (unit != null && (includeArchived || unit.getContentStatus() != ContentStatus.ARCHIVED)) {
                result.computeIfAbsent(item.getCoursePackage().getId(), ignored -> new ArrayList<>()).add(unit);
            }
        }
        return result;
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

    /** Every lesson of the class in reading order (unit order, then lesson order). */
    public List<Lesson> lessonsInOrder(CoursePackage coursePackage, boolean includeArchivedUnits) {
        return lessonsByUnit(units(coursePackage, includeArchivedUnits)).values().stream()
                .flatMap(List::stream)
                .toList();
    }

    /** The class a unit belongs to (units are only created inside a class). */
    public Optional<CoursePackage> classOfUnit(Long unitId) {
        return packageItemRepository.findByItemTypeAndItemId(PackageItemType.MODULE, unitId).stream()
                .map(PackageItem::getCoursePackage)
                .findFirst();
    }

    public CoursePackage requireClassOfLesson(Lesson lesson) {
        if (lesson.getModule() == null) {
            throw new ResponseStatusException(NOT_FOUND, LESSON_NOT_FOUND);
        }
        return classOfUnit(lesson.getModule().getId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, LESSON_NOT_FOUND));
    }

    /** The working copy of a published lesson, when the teacher has started a revision. */
    public Optional<Lesson> revisionOf(Lesson lesson) {
        return lessonRepository.findFirstByRevisionOfId(lesson.getId());
    }

    /** {@link #revisionOf} for many lessons at once, keyed by the lesson id (lessons without a revision are left out). */
    public Map<Long, Lesson> revisionsByLesson(Collection<Lesson> lessons) {
        if (lessons.isEmpty()) {
            return Map.of();
        }
        return lessonRepository.findByRevisionOfIdIn(lessons.stream().map(Lesson::getId).toList()).stream()
                .collect(Collectors.toMap(revision -> revision.getRevisionOf().getId(), revision -> revision,
                        (first, second) -> first));
    }

    // ----- lesson parts -----

    public List<LessonKeyPhrase> keyPhrases(Long lessonId) {
        return keyPhraseRepository.findByLessonIdOrderBySortOrderAscIdAsc(lessonId);
    }

    /** The lesson's one short video (the first active one). */
    public Optional<RecordedLessonVideo> video(Long lessonId) {
        return videoRepository.findByLessonIdAndActiveTrueOrderBySortOrderAscIdAsc(lessonId).stream().findFirst();
    }

    public Map<Long, RecordedLessonVideo> videoByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, RecordedLessonVideo> result = new HashMap<>();
        videoRepository.findByLessonIdInAndActiveTrue(lessonIds).stream()
                .sorted(Comparator.comparing(RecordedLessonVideo::getSortOrder).thenComparing(RecordedLessonVideo::getId))
                .forEach(video -> result.putIfAbsent(video.getLesson().getId(), video));
        return result;
    }

    /** Every activity of the lesson (all statuses), in lesson order. */
    public List<LessonActivity> activities(Long lessonId) {
        List<LessonActivity> activities = new ArrayList<>();
        activities.addAll(flashcardRepository.findByLessonIdOrderByLessonPositionAscIdAsc(lessonId));
        activities.addAll(tracingExerciseRepository.findByLessonIdOrderByLessonPositionAscIdAsc(lessonId));
        activities.addAll(matchingGameRepository.findByLessonIdOrderByLessonPositionAscIdAsc(lessonId));
        activities.sort(ACTIVITY_ORDER);
        return activities;
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
        return matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(gameId);
    }

    /** Every quiz of the lesson (all statuses), oldest first. */
    public List<Quiz> quizzes(Long lessonId) {
        return quizRepository.findByLessonIdOrderByCreatedAtAscIdAsc(lessonId);
    }

    public Map<Long, List<Quiz>> quizzesByLesson(Collection<Long> lessonIds) {
        if (lessonIds.isEmpty()) {
            return Map.of();
        }
        return quizRepository.findByLessonIdIn(lessonIds).stream()
                .sorted(Comparator.comparing(Quiz::getCreatedAt).thenComparing(Quiz::getId))
                .collect(Collectors.groupingBy(quiz -> quiz.getLesson().getId(), LinkedHashMap::new, Collectors.toList()));
    }

    public List<QuizQuestion> questions(Long quizId) {
        return quizQuestionRepository.findByQuizIdOrderBySortOrderAscCreatedAtAsc(quizId);
    }

    public Map<Long, List<QuizQuestion>> questionsByQuiz(Collection<Long> quizIds) {
        if (quizIds.isEmpty()) {
            return Map.of();
        }
        return quizQuestionRepository.findByQuizIdInOrderBySortOrderAscIdAsc(quizIds).stream()
                .collect(Collectors.groupingBy(question -> question.getQuiz().getId()));
    }

    // ----- shared rules -----

    private static final Comparator<LessonActivity> ACTIVITY_ORDER = Comparator
            .comparing((LessonActivity activity) -> Objects.requireNonNullElse(activity.getLessonPosition(), Integer.MAX_VALUE))
            .thenComparing(LessonActivity::getId);

    public static ClassActivityType typeOf(LessonActivity activity) {
        if (activity instanceof Flashcard) {
            return ClassActivityType.FLASHCARDS;
        }
        if (activity instanceof TracingExercise) {
            return ClassActivityType.TRACING;
        }
        if (activity instanceof MatchingGame) {
            return ClassActivityType.MATCHING;
        }
        throw new IllegalStateException("Unknown lesson activity " + activity);
    }

    private static ContentStatus statusOf(LessonActivity activity) {
        return ((PracticeActivityContent) activity).getContentStatus();
    }

    /** Activities and quizzes students see: published, inside a published lesson. */
    public static boolean isLive(LessonActivity activity) {
        return statusOf(activity) == ContentStatus.PUBLISHED;
    }

    public static boolean isLive(Quiz quiz) {
        return quiz.getContentStatus() == ContentStatus.PUBLISHED;
    }

    /** Parts the teacher still works with (everything except archived). */
    public static boolean isActive(LessonActivity activity) {
        return statusOf(activity) != ContentStatus.ARCHIVED;
    }

    public static boolean isActive(Quiz quiz) {
        return quiz.getContentStatus() != ContentStatus.ARCHIVED;
    }

    /** A lesson students may open: published, not a revision copy, in an active unit. */
    public static boolean isLessonLive(Lesson lesson) {
        return lesson.getContentStatus() == ContentStatus.PUBLISHED
                && lesson.getRevisionOf() == null
                && lesson.getModule() != null
                && lesson.getModule().getContentStatus() != ContentStatus.ARCHIVED;
    }

    /** The later of two times; null only when both are null. */
    static LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (second == null) {
            return first;
        }
        return first == null || second.isAfter(first) ? second : first;
    }
}
