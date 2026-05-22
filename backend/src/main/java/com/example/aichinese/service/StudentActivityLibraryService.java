package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
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
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.StoryQuestionRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningProgressService.PlatformActivityOutcome;
import com.example.aichinese.util.PaginationUtils;

import jakarta.persistence.criteria.Predicate;

/**
 * Read-only library access and completion bookkeeping for the six
 * platform-managed activity types from the student's point of view.
 *
 * <ul>
 *   <li>Listing and detail endpoints expose only activities in
 *       {@link ContentStatus#PUBLISHED} state — drafts and archived rows are
 *       filtered out at the database level.</li>
 *   <li>Completion endpoints delegate XP bookkeeping to
 *       {@link StudentLearningProgressService#completePlatformActivity}, so
 *       the streak / dedupe logic stays in one place.</li>
 *   <li>Audit fields (admin creator id, timestamps) are intentionally NOT
 *       exposed to students.</li>
 * </ul>
 */
@Service
public class StudentActivityLibraryService {

    static final String SOURCE_FLASHCARD = "PLATFORM_FLASHCARD";
    static final String SOURCE_MATCHING_GAME = "PLATFORM_MATCHING_GAME";
    static final String SOURCE_LISTENING_EXERCISE = "PLATFORM_LISTENING_EXERCISE";
    static final String SOURCE_TRACING_EXERCISE = "PLATFORM_TRACING_EXERCISE";
    static final String SOURCE_DRAG_DROP_ACTIVITY = "PLATFORM_DRAG_DROP_ACTIVITY";
    static final String SOURCE_STORY_ACTIVITY = "PLATFORM_STORY_ACTIVITY";

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
            StudentLearningProgressService studentLearningProgressService) {
        this.flashcardRepository = flashcardRepository;
        this.matchingGameRepository = matchingGameRepository;
        this.matchingGameItemRepository = matchingGameItemRepository;
        this.listeningExerciseRepository = listeningExerciseRepository;
        this.tracingExerciseRepository = tracingExerciseRepository;
        this.dragDropActivityRepository = dragDropActivityRepository;
        this.dragDropItemRepository = dragDropItemRepository;
        this.storyActivityRepository = storyActivityRepository;
        this.storyQuestionRepository = storyQuestionRepository;
        this.studentLearningAccessService = studentLearningAccessService;
        this.studentLearningProgressService = studentLearningProgressService;
    }

    // ===================================================================
    // Library feed (cross-type)
    // ===================================================================

    @Transactional(readOnly = true)
    public PageResponse<StudentActivitySummaryResponse> listLibrary(
            SecurityUserDetails currentUser,
            ActivityFilter filter,
            int page,
            int size) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        long requestedWindow = ((long) safePage + 1L) * safeSize;
        if (requestedWindow > Integer.MAX_VALUE) {
            throw new ResponseStatusException(BAD_REQUEST, "Requested activity page is too large.");
        }
        int perTypeLimit = (int) requestedWindow;
        Pageable slice = PageRequest.of(0, perTypeLimit, Sort.by(Sort.Order.desc("updatedAt")));

        List<ActivityFeedItem> merged = new ArrayList<>();
        long totalItems = 0;
        ActivityType requestedType = filter == null ? null : filter.type();

        if (requestedType == null || requestedType == ActivityType.FLASHCARD) {
            var pageResult = flashcardRepository.findAll(this.<Flashcard>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.FLASHCARD,
                            preferredTitle(e.getTitle(), e.getFrontText()),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_FLASHCARD, e.getId()))));
        }
        if (requestedType == null || requestedType == ActivityType.MATCHING_GAME) {
            var pageResult = matchingGameRepository.findAll(this.<MatchingGame>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.MATCHING_GAME,
                            e.getTitle(),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_MATCHING_GAME, e.getId()))));
        }
        if (requestedType == null || requestedType == ActivityType.LISTENING_EXERCISE) {
            var pageResult = listeningExerciseRepository.findAll(this.<ListeningExercise>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.LISTENING_EXERCISE,
                            e.getTitle(),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_LISTENING_EXERCISE, e.getId()))));
        }
        if (requestedType == null || requestedType == ActivityType.TRACING_EXERCISE) {
            var pageResult = tracingExerciseRepository.findAll(this.<TracingExercise>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.TRACING_EXERCISE,
                            e.getTitle(),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_TRACING_EXERCISE, e.getId()))));
        }
        if (requestedType == null || requestedType == ActivityType.DRAG_DROP_ACTIVITY) {
            var pageResult = dragDropActivityRepository.findAll(this.<DragDropActivity>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.DRAG_DROP_ACTIVITY,
                            e.getTitle(),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_DRAG_DROP_ACTIVITY, e.getId()))));
        }
        if (requestedType == null || requestedType == ActivityType.STORY_ACTIVITY) {
            var pageResult = storyActivityRepository.findAll(this.<StoryActivity>filterSpec(filter), slice);
            totalItems += pageResult.getTotalElements();
            pageResult.getContent()
                    .forEach(e -> merged.add(activityFeedItem(e.getId(), ActivityType.STORY_ACTIVITY,
                            e.getTitle(),
                            e.getHskLevel(), e.getAgeBand(), e.getDifficulty(), e.getTopic(),
                            e.getTags(), e.getXpReward(), e.getEstimatedSeconds(),
                            e.getCreatedAt(), e.getUpdatedAt(),
                            isCompleted(student, SOURCE_STORY_ACTIVITY, e.getId()))));
        }

        List<StudentActivitySummaryResponse> sorted = merged.stream()
                .sorted(ACTIVITY_FEED_ORDER)
                .map(ActivityFeedItem::summary)
                .toList();
        int fetchedTotal = sorted.size();
        int fromIndex = PaginationUtils.startIndex(safePage, safeSize, fetchedTotal);
        int toIndex = PaginationUtils.endIndex(fromIndex, safeSize, fetchedTotal);
        List<StudentActivitySummaryResponse> windowed = sorted.subList(fromIndex, toIndex);
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);
        return new PageResponse<>(windowed, safePage, safeSize, totalItems, totalPages);
    }

    // ===================================================================
    // Detail / completion: Flashcard
    // ===================================================================

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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed flashcard: " + flashcardLabel(entity));
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.FLASHCARD,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private Flashcard requirePublishedFlashcard(Long id) {
        Flashcard entity = flashcardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Flashcard not found."));
        ensurePublished(entity.getContentStatus(), "Flashcard");
        return entity;
    }

    private String flashcardLabel(Flashcard entity) {
        if (entity.getTitle() != null && !entity.getTitle().isBlank()) {
            return entity.getTitle();
        }
        return entity.getFrontText() == null ? "(untitled)" : entity.getFrontText();
    }

    // ===================================================================
    // Detail / completion: MatchingGame
    // ===================================================================

    @Transactional(readOnly = true)
    public StudentMatchingGameResponse getMatchingGame(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        MatchingGame entity = requirePublishedMatchingGame(id);
        List<StudentMatchingGameItemResponse> items = matchingGameItemRepository
                .findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(this::toStudentMatchingGameItemResponse)
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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed matching game: " + entity.getTitle());
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.MATCHING_GAME,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private MatchingGame requirePublishedMatchingGame(Long id) {
        MatchingGame entity = matchingGameRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Matching game not found."));
        ensurePublished(entity.getContentStatus(), "Matching game");
        return entity;
    }

    private StudentMatchingGameItemResponse toStudentMatchingGameItemResponse(MatchingGameItem item) {
        return new StudentMatchingGameItemResponse(
                item.getId(),
                item.getLeftText(),
                item.getRightText(),
                item.getLeftImageUrl(),
                item.getRightImageUrl(),
                item.getPinyin(),
                item.getSortOrder());
    }

    // ===================================================================
    // Detail / completion: ListeningExercise
    // ===================================================================

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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed listening exercise: " + entity.getTitle());
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.LISTENING_EXERCISE,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private ListeningExercise requirePublishedListeningExercise(Long id) {
        ListeningExercise entity = listeningExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Listening exercise not found."));
        ensurePublished(entity.getContentStatus(), "Listening exercise");
        return entity;
    }

    // ===================================================================
    // Detail / completion: TracingExercise
    // ===================================================================

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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed tracing exercise: " + entity.getTitle());
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.TRACING_EXERCISE,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private TracingExercise requirePublishedTracingExercise(Long id) {
        TracingExercise entity = tracingExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tracing exercise not found."));
        ensurePublished(entity.getContentStatus(), "Tracing exercise");
        return entity;
    }

    // ===================================================================
    // Detail / completion: DragDropActivity
    // ===================================================================

    @Transactional(readOnly = true)
    public StudentDragDropActivityResponse getDragDropActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        DragDropActivity entity = requirePublishedDragDropActivity(id);
        List<StudentDragDropItemResponse> items = dragDropItemRepository
                .findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(this::toStudentDragDropItemResponse)
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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed drag-drop activity: " + entity.getTitle());
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.DRAG_DROP_ACTIVITY,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private DragDropActivity requirePublishedDragDropActivity(Long id) {
        DragDropActivity entity = dragDropActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Drag-drop activity not found."));
        ensurePublished(entity.getContentStatus(), "Drag-drop activity");
        return entity;
    }

    private StudentDragDropItemResponse toStudentDragDropItemResponse(DragDropItem item) {
        return new StudentDragDropItemResponse(
                item.getId(),
                item.getDragText(),
                item.getTargetZone(),
                item.getPinyin(),
                item.getImageUrl(),
                item.getSortOrder());
    }

    // ===================================================================
    // Detail / completion: StoryActivity
    // ===================================================================

    @Transactional(readOnly = true)
    public StudentStoryActivityResponse getStoryActivity(SecurityUserDetails currentUser, Long id) {
        StudentProfile student = studentLearningAccessService.requireStudent(currentUser);
        StoryActivity entity = requirePublishedStoryActivity(id);
        List<StudentStoryQuestionResponse> questions = storyQuestionRepository
                .findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(id).stream()
                .map(this::toStudentStoryQuestionResponse)
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
                Optional.ofNullable(entity.getXpReward()).orElse(0),
                "Completed story activity: " + entity.getTitle());
        return new StudentActivityCompletionResponse(
                entity.getId(),
                ActivityType.STORY_ACTIVITY,
                outcome.earnedPoints(),
                outcome.totalPoints(),
                outcome.firstCompletion());
    }

    private StoryActivity requirePublishedStoryActivity(Long id) {
        StoryActivity entity = storyActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Story activity not found."));
        ensurePublished(entity.getContentStatus(), "Story activity");
        return entity;
    }

    private StudentStoryQuestionResponse toStudentStoryQuestionResponse(StoryQuestion question) {
        return new StudentStoryQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getOptionsPayload(),
                question.getExplanation(),
                question.getSortOrder());
    }

    // ===================================================================
    // Helpers
    // ===================================================================

    private void ensurePublished(ContentStatus status, String label) {
        // Students must never see drafts or archived rows. Returning 404
        // (instead of 403) keeps draft-row existence opaque to the client.
        if (status != ContentStatus.PUBLISHED) {
            throw new ResponseStatusException(NOT_FOUND, label + " is not available.");
        }
    }

    private boolean isCompleted(StudentProfile student, String sourceType, Long sourceId) {
        return studentLearningProgressService.isPlatformActivityCompleted(student, sourceType, sourceId);
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String preferredTitle(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback == null ? "(untitled)" : fallback;
    }

    private ActivityFeedItem activityFeedItem(
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
                id, type, title, hskLevel, ageBand, difficulty,
                topic, splitTags(tagsCsv), xpReward, estimatedSeconds, updatedAt, completed);
        return new ActivityFeedItem(summary, createdAt, updatedAt);
    }

    private record ActivityFeedItem(
            StudentActivitySummaryResponse summary,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
    }

    /**
     * Spec that always forces {@code contentStatus = PUBLISHED} so students
     * never see drafts or archived rows, then composes the optional user
     * filter on top.
     */
    private <T> Specification<T> filterSpec(ActivityFilter filter) {
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
            if (filter.topic() != null && !filter.topic().isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("topic")),
                        filter.topic().trim().toLowerCase(Locale.ROOT)));
            }
            if (filter.tag() != null && !filter.tag().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("tags")),
                        "%" + filter.tag().trim().toLowerCase(Locale.ROOT) + "%"));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /**
     * Student-facing filter, narrower than the admin one: students may not
     * filter by {@code status} (only PUBLISHED is ever returned) or by
     * {@code createdByUserId} (creator identity is internal).
     */
    public record ActivityFilter(
            Integer hskLevel,
            AgeBand ageBand,
            ActivityDifficulty difficulty,
            String topic,
            String tag,
            String keyword,
            ActivityType type) {

        public static ActivityFilter empty() {
            return new ActivityFilter(null, null, null, null, null, null, null);
        }

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
                    ageBand.map(value -> requireMaxLength(value, 40, "ageBand")).map(ActivityFilter::parseAgeBand).orElse(null),
                    difficulty.map(value -> requireMaxLength(value, 40, "difficulty")).map(ActivityFilter::parseDifficulty).orElse(null),
                    topic.map(value -> requireMaxLength(value, 200, "topic")).orElse(null),
                    tag.map(value -> requireMaxLength(value, 100, "tag")).orElse(null),
                    keyword.map(value -> requireMaxLength(value, 200, "keyword")).orElse(null),
                    type.map(value -> requireMaxLength(value, 60, "type")).map(ActivityFilter::parseType).orElse(null));
        }

        private static String requireMaxLength(String raw, int maxLength, String fieldName) {
            String trimmed = raw.trim();
            if (trimmed.length() > maxLength) {
                throw new ResponseStatusException(BAD_REQUEST, fieldName + " is too long.");
            }
            return trimmed;
        }

        private static AgeBand parseAgeBand(String raw) {
            try {
                return AgeBand.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown ageBand: " + raw);
            }
        }

        private static ActivityDifficulty parseDifficulty(String raw) {
            try {
                return ActivityDifficulty.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown difficulty: " + raw);
            }
        }

        private static ActivityType parseType(String raw) {
            try {
                return ActivityType.valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown activity type: " + raw);
            }
        }
    }

}
