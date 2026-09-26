package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.student.activity.StudentActivityCompletionResponse;
import com.example.aichinese.dto.student.activity.StudentActivitySummaryResponse;
import com.example.aichinese.dto.student.activity.StudentDragDropActivityResponse;
import com.example.aichinese.dto.student.activity.StudentDragDropItemResponse;
import com.example.aichinese.dto.student.activity.StudentFlashcardResponse;
import com.example.aichinese.dto.student.activity.StudentListeningExerciseResponse;
import com.example.aichinese.dto.student.activity.StudentMatchingGameItemResponse;
import com.example.aichinese.dto.student.activity.StudentMatchingGameResponse;
import com.example.aichinese.dto.student.activity.StudentStoryActivityResponse;
import com.example.aichinese.dto.student.activity.StudentStoryQuestionResponse;
import com.example.aichinese.dto.student.activity.StudentTracingExerciseResponse;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.DragDropItem;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.StoryActivity;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.DragDropActivityRepository;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.StoryQuestionRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningProgressService.PlatformActivityOutcome;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.criteria.Predicate;

@Service
public class StudentActivityLibraryService {

    private static final String SOURCE_FLASHCARD = "PLATFORM_FLASHCARD";
    private static final String SOURCE_MATCHING_GAME = "PLATFORM_MATCHING_GAME";
    private static final String SOURCE_LISTENING_EXERCISE = "PLATFORM_LISTENING_EXERCISE";
    private static final String SOURCE_TRACING_EXERCISE = "PLATFORM_TRACING_EXERCISE";
    private static final String SOURCE_DRAG_DROP_ACTIVITY = "PLATFORM_DRAG_DROP_ACTIVITY";
    private static final String SOURCE_STORY_ACTIVITY = "PLATFORM_STORY_ACTIVITY";
    private static final String UNTITLED_LABEL = "(untitled)";
    private static final int AGE_BAND_MAX_LENGTH = 40;
    private static final int DIFFICULTY_MAX_LENGTH = 40;
    private static final int TOPIC_MAX_LENGTH = 200;
    private static final int TAG_MAX_LENGTH = 100;
    private static final int MAX_MERGED_FEED_WINDOW = 1_000;
    private static final String ACTIVITY_PAGE_TOO_DEEP_MESSAGE =
            "Requested activity page is too deep. Please use filters or a smaller page.";
    private static final int KEYWORD_MAX_LENGTH = 200;
    private static final int TYPE_MAX_LENGTH = 60;

    private static final Comparator<LocalDateTime> NEWEST_TIMESTAMP_FIRST =
            Comparator.nullsLast(Comparator.reverseOrder());
    private static final Comparator<ActivityFeedItem> ACTIVITY_FEED_ORDER =
            Comparator.comparing(ActivityFeedItem::updatedAt, NEWEST_TIMESTAMP_FIRST)
                    .thenComparing(ActivityFeedItem::createdAt, NEWEST_TIMESTAMP_FIRST)
                    .thenComparing(item -> item.summary().type().name())
                    .thenComparing(item -> item.summary().id(), Comparator.nullsLast(Comparator.reverseOrder()));

    private final FlashcardRepository flashcardRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final DragDropActivityRepository dragDropActivityRepository;
    private final DragDropItemRepository dragDropItemRepository;
    private final StoryActivityRepository storyActivityRepository;
    private final StoryQuestionRepository storyQuestionRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final StudentLearningProgressService studentLearningProgressService;
    private final PointsTransactionRepository pointsTransactionRepository;

    public StudentActivityLibraryService(
            FlashcardRepository flashcardRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            DragDropActivityRepository dragDropActivityRepository,
            DragDropItemRepository dragDropItemRepository,
            StoryActivityRepository storyActivityRepository,
            StoryQuestionRepository storyQuestionRepository,
            StudentLearningAccessService studentLearningAccessService,
            StudentLearningProgressService studentLearningProgressService,
            PointsTransactionRepository pointsTransactionRepository) {
        this.flashcardRepository = Objects.requireNonNull(flashcardRepository, "flashcardRepository");
        this.matchingGameRepository = Objects.requireNonNull(matchingGameRepository, "matchingGameRepository");
        this.matchingGameItemRepository = Objects.requireNonNull(matchingGameItemRepository, "matchingGameItemRepository");
        this.listeningExerciseRepository = Objects.requireNonNull(listeningExerciseRepository, "listeningExerciseRepository");
        this.tracingExerciseRepository = Objects.requireNonNull(tracingExerciseRepository, "tracingExerciseRepository");
        this.dragDropActivityRepository = Objects.requireNonNull(dragDropActivityRepository, "dragDropActivityRepository");
        this.dragDropItemRepository = Objects.requireNonNull(dragDropItemRepository, "dragDropItemRepository");
        this.storyActivityRepository = Objects.requireNonNull(storyActivityRepository, "storyActivityRepository");
        this.storyQuestionRepository = Objects.requireNonNull(storyQuestionRepository, "storyQuestionRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.studentLearningProgressService = Objects.requireNonNull(studentLearningProgressService, "studentLearningProgressService");
        this.pointsTransactionRepository = Objects.requireNonNull(pointsTransactionRepository, "pointsTransactionRepository");
    }

    @Transactional(readOnly = true)
    public PageResponse<StudentActivitySummaryResponse> listLibrary(
            SecurityUserDetails currentUser,
            ActivityFilter filter,
            int page,
            int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        int perTypeLimit = mergedPageFetchLimit(safePage, safeSize);
        Pageable slice = PageRequest.of(0, perTypeLimit, Sort.by(Sort.Order.desc("updatedAt")));

        List<ActivityFeedItem> merged = new ArrayList<>();
        long totalItems = 0;
        ActivityType requestedType = filter == null ? null : filter.type();

        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.FLASHCARD, flashcardRepository, this::toFlashcardFeedItem);
        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.MATCHING_GAME, matchingGameRepository, this::toMatchingGameFeedItem);
        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.LISTENING_EXERCISE, listeningExerciseRepository, this::toListeningExerciseFeedItem);
        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.TRACING_EXERCISE, tracingExerciseRepository, this::toTracingExerciseFeedItem);
        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.DRAG_DROP_ACTIVITY, dragDropActivityRepository, this::toDragDropActivityFeedItem);
        totalItems += appendFeedItems(merged, filter, slice, requestedType,
                ActivityType.STORY_ACTIVITY, storyActivityRepository, this::toStoryActivityFeedItem);

        List<StudentActivitySummaryResponse> sorted = merged.stream()
                .sorted(ACTIVITY_FEED_ORDER)
                .map(ActivityFeedItem::summary)
                .toList();
        int fetchedTotal = sorted.size();
        int fromIndex = PaginationUtils.startIndex(safePage, safeSize, fetchedTotal);
        int toIndex = PaginationUtils.endIndex(fromIndex, safeSize, fetchedTotal);
        List<StudentActivitySummaryResponse> windowed = withCompletionStatus(student, sorted.subList(fromIndex, toIndex));
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);
        return new PageResponse<>(windowed, safePage, safeSize, totalItems, totalPages);
    }

    private static int mergedPageFetchLimit(int safePage, int safeSize) {
        long requestedWindow = ((long) safePage + 1L) * safeSize;
        if (requestedWindow > MAX_MERGED_FEED_WINDOW) {
            throw new ResponseStatusException(BAD_REQUEST, ACTIVITY_PAGE_TOO_DEEP_MESSAGE);
        }
        return (int) requestedWindow;
    }

    @Transactional(readOnly = true)
    public StudentFlashcardResponse getFlashcard(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Flashcard entity = requirePublishedFlashcard(id);
        return new StudentFlashcardResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getFrontText(),
                entity.getBackText(),
                entity.getPinyin(),
                entity.getEnglishMeaning(),
                entity.getMalayMeaning(),
                entity.getImageUrl(),
                entity.getAudioUrl(),
                entity.getSortOrder(),
                entity.getExtraPayload(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_FLASHCARD, id));
    }

    @Transactional
    public StudentActivityCompletionResponse completeFlashcard(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        Flashcard entity = requirePublishedFlashcard(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_FLASHCARD,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("flashcard", preferredTitle(entity.getTitle(), entity.getFrontText())));
        return completionResponse(entity.getId(), ActivityType.FLASHCARD, outcome);
    }

    private Flashcard requirePublishedFlashcard(Long id) {
        Flashcard entity = flashcardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Flashcard not found."));
        ensurePublished(entity.getContentStatus(), "Flashcard");
        return entity;
    }

    @Transactional(readOnly = true)
    public StudentMatchingGameResponse getMatchingGame(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        MatchingGame entity = requirePublishedMatchingGame(id);
        List<StudentMatchingGameItemResponse> items = matchingGameItemRepository
                .findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(StudentActivityLibraryService::toStudentMatchingGameItemResponse)
                .toList();
        return new StudentMatchingGameResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_MATCHING_GAME, id),
                items);
    }

    @Transactional
    public StudentActivityCompletionResponse completeMatchingGame(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        MatchingGame entity = requirePublishedMatchingGame(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_MATCHING_GAME,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("matching game", entity.getTitle()));
        return completionResponse(entity.getId(), ActivityType.MATCHING_GAME, outcome);
    }

    private MatchingGame requirePublishedMatchingGame(Long id) {
        MatchingGame entity = matchingGameRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Matching game not found."));
        ensurePublished(entity.getContentStatus(), "Matching game");
        return entity;
    }

    private static StudentMatchingGameItemResponse toStudentMatchingGameItemResponse(MatchingGameItem item) {
        return new StudentMatchingGameItemResponse(
                item.getId(),
                item.getLeftText(),
                item.getRightText(),
                item.getLeftImageUrl(),
                item.getRightImageUrl(),
                item.getPinyin(),
                item.getSortOrder());
    }

    @Transactional(readOnly = true)
    public StudentListeningExerciseResponse getListeningExercise(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        ListeningExercise entity = requirePublishedListeningExercise(id);
        return new StudentListeningExerciseResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getAudioUrl(),
                entity.getQuestionPayload(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_LISTENING_EXERCISE, id));
    }

    @Transactional
    public StudentActivityCompletionResponse completeListeningExercise(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        ListeningExercise entity = requirePublishedListeningExercise(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_LISTENING_EXERCISE,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("listening exercise", entity.getTitle()));
        return completionResponse(entity.getId(), ActivityType.LISTENING_EXERCISE, outcome);
    }

    private ListeningExercise requirePublishedListeningExercise(Long id) {
        ListeningExercise entity = listeningExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Listening exercise not found."));
        ensurePublished(entity.getContentStatus(), "Listening exercise");
        return entity;
    }

    @Transactional(readOnly = true)
    public StudentTracingExerciseResponse getTracingExercise(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        TracingExercise entity = requirePublishedTracingExercise(id);
        return new StudentTracingExerciseResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getWorksheetUrl(),
                entity.getTargetCharacter(),
                entity.getPinyin(),
                entity.getMeaning(),
                entity.getInstructions(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_TRACING_EXERCISE, id));
    }

    @Transactional
    public StudentActivityCompletionResponse completeTracingExercise(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        TracingExercise entity = requirePublishedTracingExercise(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_TRACING_EXERCISE,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("tracing exercise", entity.getTitle()));
        return completionResponse(entity.getId(), ActivityType.TRACING_EXERCISE, outcome);
    }

    private TracingExercise requirePublishedTracingExercise(Long id) {
        TracingExercise entity = tracingExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tracing exercise not found."));
        ensurePublished(entity.getContentStatus(), "Tracing exercise");
        return entity;
    }

    @Transactional(readOnly = true)
    public StudentDragDropActivityResponse getDragDropActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        DragDropActivity entity = requirePublishedDragDropActivity(id);
        List<StudentDragDropItemResponse> items = dragDropItemRepository
                .findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(StudentActivityLibraryService::toStudentDragDropItemResponse)
                .toList();
        return new StudentDragDropActivityResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getInstructions(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_DRAG_DROP_ACTIVITY, id),
                items);
    }

    @Transactional
    public StudentActivityCompletionResponse completeDragDropActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        DragDropActivity entity = requirePublishedDragDropActivity(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_DRAG_DROP_ACTIVITY,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("drag-drop activity", entity.getTitle()));
        return completionResponse(entity.getId(), ActivityType.DRAG_DROP_ACTIVITY, outcome);
    }

    private DragDropActivity requirePublishedDragDropActivity(Long id) {
        DragDropActivity entity = dragDropActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Drag-drop activity not found."));
        ensurePublished(entity.getContentStatus(), "Drag-drop activity");
        return entity;
    }

    private static StudentDragDropItemResponse toStudentDragDropItemResponse(DragDropItem item) {
        return new StudentDragDropItemResponse(
                item.getId(),
                item.getDragText(),
                item.getTargetZone(),
                item.getPinyin(),
                item.getImageUrl(),
                item.getSortOrder());
    }

    @Transactional(readOnly = true)
    public StudentStoryActivityResponse getStoryActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        StoryActivity entity = requirePublishedStoryActivity(id);
        List<StudentStoryQuestionResponse> questions = storyQuestionRepository
                .findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(StudentActivityLibraryService::toStudentStoryQuestionResponse)
                .toList();
        return new StudentStoryActivityResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getInstructions(),
                entity.getStoryText(),
                entity.getPinyin(),
                entity.getTranslation(),
                entity.getImageUrl(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                isCompleted(student, SOURCE_STORY_ACTIVITY, id),
                questions);
    }

    @Transactional
    public StudentActivityCompletionResponse completeStoryActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        StoryActivity entity = requirePublishedStoryActivity(id);
        PlatformActivityOutcome outcome = studentLearningProgressService.completePlatformActivity(
                student,
                SOURCE_STORY_ACTIVITY,
                entity.getId(),
                xpReward(entity.getXpReward()),
                completionMessage("story activity", entity.getTitle()));
        return completionResponse(entity.getId(), ActivityType.STORY_ACTIVITY, outcome);
    }

    private StoryActivity requirePublishedStoryActivity(Long id) {
        StoryActivity entity = storyActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Story activity not found."));
        ensurePublished(entity.getContentStatus(), "Story activity");
        return entity;
    }

    private static StudentStoryQuestionResponse toStudentStoryQuestionResponse(StoryQuestion question) {
        return new StudentStoryQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getOptionsPayload(),
                question.getExplanation(),
                question.getSortOrder());
    }

    private static void ensurePublished(ContentStatus status, String label) {
        if (status != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, label + " is not available.");
        }
    }

    private boolean isCompleted(StudentProfile student, String sourceType, Long sourceId) {
        return studentLearningProgressService.isPlatformActivityCompleted(student, sourceType, sourceId);
    }

    private static int xpReward(Integer xpReward) {
        return xpReward == null ? 0 : xpReward;
    }

    private static StudentActivityCompletionResponse completionResponse(
            Long id,
            ActivityType type,
            PlatformActivityOutcome outcome) {
        return new StudentActivityCompletionResponse(
                id,
                type,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private static String completionMessage(String activityLabel, String title) {
        return "Completed " + activityLabel + ": " + preferredTitle(title, null);
    }

    private static List<String> splitTags(String tags) {
        String normalized = TextUtils.trimToNull(tags);
        if (normalized == null) {
            return List.of();
        }
        return Arrays.stream(normalized.split(","))
                .map(TextUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String preferredTitle(String primary, String fallback) {
        String normalizedPrimary = TextUtils.trimToNull(primary);
        return normalizedPrimary != null
                ? normalizedPrimary
                : Optional.ofNullable(TextUtils.trimToNull(fallback)).orElse(UNTITLED_LABEL);
    }

    private <T> long appendFeedItems(
            List<ActivityFeedItem> target,
            ActivityFilter filter,
            Pageable slice,
            ActivityType requestedType,
            ActivityType candidateType,
            JpaSpecificationExecutor<T> repository,
            Function<T, ActivityFeedItem> mapper) {
        if (requestedType != null && requestedType != candidateType) {
            return 0L;
        }
        var pageResult = repository.findAll(StudentActivityLibraryService.<T>filterSpec(filter), slice);
        pageResult.getContent().forEach(entity -> target.add(mapper.apply(entity)));
        return pageResult.getTotalElements();
    }

    private List<StudentActivitySummaryResponse> withCompletionStatus(
            StudentProfile student, List<StudentActivitySummaryResponse> pageItems) {
        if (pageItems.isEmpty()) {
            return pageItems;
        }

        List<String> sourceTypes = pageItems.stream()
                .map(item -> "PLATFORM_" + item.type().name())
                .distinct()
                .toList();
        List<Long> sourceIds = pageItems.stream()
                .map(StudentActivitySummaryResponse::id)
                .distinct()
                .toList();
        Set<CompletionKey> completed = pointsTransactionRepository
                .findByStudentIdAndSourceTypeInAndSourceIdIn(student.getId(), sourceTypes, sourceIds)
                .stream()
                .map(transaction -> new CompletionKey(transaction.getSourceType(), transaction.getSourceId()))
                .collect(Collectors.toSet());

        return pageItems.stream()
                .map(item -> new StudentActivitySummaryResponse(
                        item.id(), item.type(), item.title(), item.hskLevel(), item.ageBand(),
                        item.difficulty(), item.topic(), item.tags(), item.xpReward(),
                        item.estimatedSeconds(), item.updatedAt(),
                        completed.contains(new CompletionKey("PLATFORM_" + item.type().name(), item.id()))))
                .toList();
    }

    private record CompletionKey(String sourceType, Long sourceId) {
    }

    private ActivityFeedItem toFlashcardFeedItem(Flashcard entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.FLASHCARD,
                preferredTitle(entity.getTitle(), entity.getFrontText()),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private ActivityFeedItem toMatchingGameFeedItem(MatchingGame entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.MATCHING_GAME, entity.getTitle(),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private ActivityFeedItem toListeningExerciseFeedItem(ListeningExercise entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.LISTENING_EXERCISE, entity.getTitle(),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private ActivityFeedItem toTracingExerciseFeedItem(TracingExercise entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.TRACING_EXERCISE, entity.getTitle(),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private ActivityFeedItem toDragDropActivityFeedItem(DragDropActivity entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.DRAG_DROP_ACTIVITY, entity.getTitle(),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private ActivityFeedItem toStoryActivityFeedItem(StoryActivity entity) {
        return activityFeedItem(
                entity.getId(), ActivityType.STORY_ACTIVITY, entity.getTitle(),
                entity.getHskLevel(), entity.getAgeBand(), entity.getDifficulty(), entity.getTopic(),
                entity.getTags(), entity.getXpReward(), entity.getEstimatedSeconds(),
                entity.getCreatedAt(), entity.getUpdatedAt(),
                false);
    }

    private static ActivityFeedItem activityFeedItem(
            Long id,
            ActivityType type,
            String title,
            Integer hskLevel,
            AgeBand ageBand,
            ActivityDifficulty difficulty,
            String topic,
            String tagsCsv,
            Integer xpReward,
            Integer estimatedSeconds,
            LocalDateTime createdAt,
            LocalDateTime updatedAt,
            boolean completed) {
        StudentActivitySummaryResponse summary = new StudentActivitySummaryResponse(
                id, type, preferredTitle(title, null), hskLevel, ageBand, difficulty,
                topic, splitTags(tagsCsv), xpReward, estimatedSeconds, updatedAt, completed);
        return new ActivityFeedItem(summary, createdAt, updatedAt);
    }

    private record ActivityFeedItem(
            StudentActivitySummaryResponse summary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    private static <T> Specification<T> filterSpec(ActivityFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("contentStatus"), ContentStatus.PUBLISHED));
            if (filter == null) {
                return cb.and(predicates.toArray(Predicate[]::new));
            }
            if (filter.hskLevel() != null) {
                predicates.add(cb.equal(root.get("hskLevel"), filter.hskLevel()));
            }
            if (filter.ageBand() != null) {
                predicates.add(cb.equal(root.get("ageBand"), filter.ageBand()));
            }
            if (filter.difficulty() != null) {
                predicates.add(cb.equal(root.get("difficulty"), filter.difficulty()));
            }
            if (filter.topic() != null) {
                predicates.add(cb.equal(cb.lower(root.get("topic")), lower(filter.topic())));
            }
            if (filter.tag() != null) {
                predicates.add(cb.like(cb.lower(root.get("tags")), likePattern(filter.tag())));
            }
            if (filter.keyword() != null) {
                predicates.add(cb.like(cb.lower(root.get("title")), likePattern(filter.keyword())));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    private static String lower(String value) {
        return value.toLowerCase(Locale.ROOT);
    }

    private static String likePattern(String value) {
        return "%" + lower(value) + "%";
    }

    public record ActivityFilter(
            Integer hskLevel,
            AgeBand ageBand,
            ActivityDifficulty difficulty,
            String topic,
            String tag,
            String keyword,
            ActivityType type) {

        public static ActivityFilter of(
                Optional<Integer> hskLevel,
                Optional<String> ageBand,
                Optional<String> difficulty,
                Optional<String> topic,
                Optional<String> tag,
                Optional<String> keyword,
                Optional<String> type) {
            return new ActivityFilter(
                    hskLevel.orElse(null),
                    ageBand.map(value -> requireMaxLength(value, AGE_BAND_MAX_LENGTH, "ageBand"))
                            .map(ActivityFilter::parseAgeBand)
                            .orElse(null),
                    difficulty.map(value -> requireMaxLength(value, DIFFICULTY_MAX_LENGTH, "difficulty"))
                            .map(ActivityFilter::parseDifficulty)
                            .orElse(null),
                    topic.map(value -> requireMaxLength(value, TOPIC_MAX_LENGTH, "topic")).orElse(null),
                    tag.map(value -> requireMaxLength(value, TAG_MAX_LENGTH, "tag")).orElse(null),
                    keyword.map(value -> requireMaxLength(value, KEYWORD_MAX_LENGTH, "keyword")).orElse(null),
                    type.map(value -> requireMaxLength(value, TYPE_MAX_LENGTH, "type"))
                            .map(ActivityFilter::parseType)
                            .orElse(null));
        }

        private static String requireMaxLength(String raw, int maxLength, String fieldName) {
            String normalized = TextUtils.trimToNull(raw);
            if (normalized != null && normalized.length() > maxLength) {
                throw new ResponseStatusException(BAD_REQUEST, fieldName + " is too long.");
            }
            return normalized;
        }

        private static AgeBand parseAgeBand(String raw) {
            return AgeBand.tryFrom(raw)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown ageBand: " + raw));
        }

        private static ActivityDifficulty parseDifficulty(String raw) {
            return ActivityDifficulty.tryFrom(raw)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown difficulty: " + raw));
        }

        private static ActivityType parseType(String raw) {
            return ActivityType.parseLenient(raw)
                    .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown activity type: " + raw));
        }
    }

}
