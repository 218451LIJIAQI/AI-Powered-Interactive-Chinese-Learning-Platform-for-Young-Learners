package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.admin.activity.AdminActivityStatusUpdateRequest;
import com.example.aichinese.dto.admin.activity.AdminActivitySummaryResponse;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminFlashcardResponse;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseResponse;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameResponse;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminTracingExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminTracingExerciseResponse;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.DragDropItem;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.StoryActivity;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.mapper.AdminActivityMapper;
import com.example.aichinese.repository.DragDropActivityRepository;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.StoryQuestionRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.PaginationUtils;

import jakarta.persistence.criteria.Predicate;

@Service
public class AdminActivityService {

    private static final List<ContentStatus> ALLOWED_TARGET_STATUSES = List.of(
            ContentStatus.DRAFT,
            ContentStatus.PUBLISHED,
            ContentStatus.ARCHIVED);
    private static final int MERGED_FEED_BATCH_SIZE = 100;

    private final FlashcardRepository flashcardRepository;
    private final MatchingGameRepository matchingGameRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ListeningExerciseRepository listeningExerciseRepository;
    private final TracingExerciseRepository tracingExerciseRepository;
    private final DragDropActivityRepository dragDropActivityRepository;
    private final DragDropItemRepository dragDropItemRepository;
    private final StoryActivityRepository storyActivityRepository;
    private final StoryQuestionRepository storyQuestionRepository;
    private final UserRepository userRepository;
    private final AdminActivityMapper mapper;
    private final AuditLogService auditLogService;

    public AdminActivityService(
            FlashcardRepository flashcardRepository,
            MatchingGameRepository matchingGameRepository,
            MatchingGameItemRepository matchingGameItemRepository,
            ListeningExerciseRepository listeningExerciseRepository,
            TracingExerciseRepository tracingExerciseRepository,
            DragDropActivityRepository dragDropActivityRepository,
            DragDropItemRepository dragDropItemRepository,
            StoryActivityRepository storyActivityRepository,
            StoryQuestionRepository storyQuestionRepository,
            UserRepository userRepository,
            AdminActivityMapper mapper,
            AuditLogService auditLogService) {
        this.flashcardRepository = Objects.requireNonNull(flashcardRepository, "flashcardRepository");
        this.matchingGameRepository = Objects.requireNonNull(matchingGameRepository, "matchingGameRepository");
        this.matchingGameItemRepository = Objects.requireNonNull(matchingGameItemRepository, "matchingGameItemRepository");
        this.listeningExerciseRepository = Objects.requireNonNull(listeningExerciseRepository, "listeningExerciseRepository");
        this.tracingExerciseRepository = Objects.requireNonNull(tracingExerciseRepository, "tracingExerciseRepository");
        this.dragDropActivityRepository = Objects.requireNonNull(dragDropActivityRepository, "dragDropActivityRepository");
        this.dragDropItemRepository = Objects.requireNonNull(dragDropItemRepository, "dragDropItemRepository");
        this.storyActivityRepository = Objects.requireNonNull(storyActivityRepository, "storyActivityRepository");
        this.storyQuestionRepository = Objects.requireNonNull(storyQuestionRepository, "storyQuestionRepository");
        this.userRepository = Objects.requireNonNull(userRepository, "userRepository");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        this.auditLogService = Objects.requireNonNull(auditLogService, "auditLogService");
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminFlashcardResponse> listFlashcards(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<Flashcard> result = flashcardRepository.findAll(filterSpec(filter), pageable);
        List<AdminFlashcardResponse> items = result.getContent().stream()
                .map(mapper::toResponse)
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminFlashcardResponse getFlashcard(Long id) {
        return mapper.toResponse(findFlashcard(id));
    }

    @Transactional
    public AdminFlashcardResponse createFlashcard(SecurityUserDetails currentUser, AdminFlashcardRequest request) {
        User admin = findActor(currentUser);
        Flashcard entity = mapper.toEntity(request, admin);
        Flashcard saved = flashcardRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "Flashcard", saved.getId(),
                "Admin created a flashcard in the platform library.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminFlashcardResponse updateFlashcard(SecurityUserDetails currentUser, Long id, AdminFlashcardRequest request) {
        User admin = findActor(currentUser);
        Flashcard entity = findFlashcard(id);
        mapper.applyTo(request, entity);
        Flashcard saved = flashcardRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "Flashcard", saved.getId(),
                "Admin updated a flashcard.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminFlashcardResponse updateFlashcardStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        Flashcard entity = findFlashcard(id);
        entity.setContentStatus(target);
        Flashcard saved = flashcardRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "Flashcard", saved.getId(),
                "Admin set flashcard status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteFlashcard(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        Flashcard entity = findFlashcard(id);
        flashcardRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "Flashcard", id,
                "Admin deleted a flashcard.");
    }

    private Flashcard findFlashcard(Long id) {
        return flashcardRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Flashcard not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMatchingGameResponse> listMatchingGames(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<MatchingGame> result = matchingGameRepository.findAll(filterSpec(filter), pageable);
        List<AdminMatchingGameResponse> items = result.getContent().stream()
                .map(game -> mapper.toResponse(game, loadMatchingGameItems(game.getId())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminMatchingGameResponse getMatchingGame(Long id) {
        MatchingGame entity = findMatchingGame(id);
        return mapper.toResponse(entity, loadMatchingGameItems(id));
    }

    @Transactional
    public AdminMatchingGameResponse createMatchingGame(SecurityUserDetails currentUser, AdminMatchingGameRequest request) {
        User admin = findActor(currentUser);
        MatchingGame entity = mapper.toEntity(request, admin);
        MatchingGame saved = matchingGameRepository.save(entity);
        List<MatchingGameItem> items = saveMatchingGameItems(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "MatchingGame", saved.getId(),
                "Admin created a matching game in the platform library.");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminMatchingGameResponse updateMatchingGame(SecurityUserDetails currentUser, Long id, AdminMatchingGameRequest request) {
        User admin = findActor(currentUser);
        MatchingGame entity = findMatchingGame(id);
        mapper.applyTo(request, entity);
        MatchingGame saved = matchingGameRepository.save(entity);
        matchingGameItemRepository.deleteByMatchingGameId(saved.getId());
        List<MatchingGameItem> items = saveMatchingGameItems(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "MatchingGame", saved.getId(),
                "Admin updated a matching game.");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminMatchingGameResponse updateMatchingGameStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        MatchingGame entity = findMatchingGame(id);
        entity.setContentStatus(target);
        MatchingGame saved = matchingGameRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "MatchingGame", saved.getId(),
                "Admin set matching game status to " + target + ".");
        return mapper.toResponse(saved, loadMatchingGameItems(saved.getId()));
    }

    @Transactional
    public void deleteMatchingGame(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        MatchingGame entity = findMatchingGame(id);
        matchingGameItemRepository.deleteByMatchingGameId(entity.getId());
        matchingGameRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "MatchingGame", id,
                "Admin deleted a matching game.");
    }

    private MatchingGame findMatchingGame(Long id) {
        return matchingGameRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Matching game not found."));
    }

    private List<MatchingGameItem> loadMatchingGameItems(Long gameId) {
        return matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(gameId);
    }

    private List<MatchingGameItem> saveMatchingGameItems(MatchingGame parent, AdminMatchingGameRequest request) {
        if (request.items().isEmpty()) {
            return List.of();
        }
        List<MatchingGameItem> entities = request.items().stream()
                .map(item -> mapper.toEntity(item, parent))
                .toList();
        return matchingGameItemRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminListeningExerciseResponse> listListeningExercises(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<ListeningExercise> result = listeningExerciseRepository.findAll(filterSpec(filter), pageable);
        List<AdminListeningExerciseResponse> items = result.getContent().stream()
                .map(mapper::toResponse)
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminListeningExerciseResponse getListeningExercise(Long id) {
        return mapper.toResponse(findListeningExercise(id));
    }

    @Transactional
    public AdminListeningExerciseResponse createListeningExercise(SecurityUserDetails currentUser, AdminListeningExerciseRequest request) {
        User admin = findActor(currentUser);
        ListeningExercise entity = mapper.toEntity(request, admin);
        ListeningExercise saved = listeningExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "ListeningExercise", saved.getId(),
                "Admin created a listening exercise.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminListeningExerciseResponse updateListeningExercise(SecurityUserDetails currentUser, Long id, AdminListeningExerciseRequest request) {
        User admin = findActor(currentUser);
        ListeningExercise entity = findListeningExercise(id);
        mapper.applyTo(request, entity);
        ListeningExercise saved = listeningExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "ListeningExercise", saved.getId(),
                "Admin updated a listening exercise.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminListeningExerciseResponse updateListeningExerciseStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        ListeningExercise entity = findListeningExercise(id);
        entity.setContentStatus(target);
        ListeningExercise saved = listeningExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "ListeningExercise", saved.getId(),
                "Admin set listening exercise status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteListeningExercise(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        ListeningExercise entity = findListeningExercise(id);
        listeningExerciseRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "ListeningExercise", id,
                "Admin deleted a listening exercise.");
    }

    private ListeningExercise findListeningExercise(Long id) {
        return listeningExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Listening exercise not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminTracingExerciseResponse> listTracingExercises(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<TracingExercise> result = tracingExerciseRepository.findAll(filterSpec(filter), pageable);
        List<AdminTracingExerciseResponse> items = result.getContent().stream()
                .map(mapper::toResponse)
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminTracingExerciseResponse getTracingExercise(Long id) {
        return mapper.toResponse(findTracingExercise(id));
    }

    @Transactional
    public AdminTracingExerciseResponse createTracingExercise(SecurityUserDetails currentUser, AdminTracingExerciseRequest request) {
        User admin = findActor(currentUser);
        TracingExercise entity = mapper.toEntity(request, admin);
        TracingExercise saved = tracingExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "TracingExercise", saved.getId(),
                "Admin created a tracing exercise.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminTracingExerciseResponse updateTracingExercise(SecurityUserDetails currentUser, Long id, AdminTracingExerciseRequest request) {
        User admin = findActor(currentUser);
        TracingExercise entity = findTracingExercise(id);
        mapper.applyTo(request, entity);
        TracingExercise saved = tracingExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "TracingExercise", saved.getId(),
                "Admin updated a tracing exercise.");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminTracingExerciseResponse updateTracingExerciseStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        TracingExercise entity = findTracingExercise(id);
        entity.setContentStatus(target);
        TracingExercise saved = tracingExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "TracingExercise", saved.getId(),
                "Admin set tracing exercise status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteTracingExercise(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        TracingExercise entity = findTracingExercise(id);
        tracingExerciseRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "TracingExercise", id,
                "Admin deleted a tracing exercise.");
    }

    private TracingExercise findTracingExercise(Long id) {
        return tracingExerciseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tracing exercise not found."));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminDragDropActivityResponse> listDragDropActivities(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<DragDropActivity> result = dragDropActivityRepository.findAll(filterSpec(filter), pageable);
        List<AdminDragDropActivityResponse> items = result.getContent().stream()
                .map(activity -> mapper.toResponse(activity, loadDragDropItems(activity.getId())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminDragDropActivityResponse getDragDropActivity(Long id) {
        DragDropActivity entity = findDragDropActivity(id);
        return mapper.toResponse(entity, loadDragDropItems(id));
    }

    @Transactional
    public AdminDragDropActivityResponse createDragDropActivity(SecurityUserDetails currentUser, AdminDragDropActivityRequest request) {
        User admin = findActor(currentUser);
        DragDropActivity entity = mapper.toEntity(request, admin);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        List<DragDropItem> items = saveDragDropItems(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "DragDropActivity", saved.getId(),
                "Admin created a drag-drop activity.");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminDragDropActivityResponse updateDragDropActivity(SecurityUserDetails currentUser, Long id, AdminDragDropActivityRequest request) {
        User admin = findActor(currentUser);
        DragDropActivity entity = findDragDropActivity(id);
        mapper.applyTo(request, entity);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        dragDropItemRepository.deleteByDragDropActivityId(saved.getId());
        List<DragDropItem> items = saveDragDropItems(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "DragDropActivity", saved.getId(),
                "Admin updated a drag-drop activity.");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminDragDropActivityResponse updateDragDropActivityStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        DragDropActivity entity = findDragDropActivity(id);
        entity.setContentStatus(target);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "DragDropActivity", saved.getId(),
                "Admin set drag-drop activity status to " + target + ".");
        return mapper.toResponse(saved, loadDragDropItems(saved.getId()));
    }

    @Transactional
    public void deleteDragDropActivity(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        DragDropActivity entity = findDragDropActivity(id);
        dragDropItemRepository.deleteByDragDropActivityId(entity.getId());
        dragDropActivityRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "DragDropActivity", id,
                "Admin deleted a drag-drop activity.");
    }

    private DragDropActivity findDragDropActivity(Long id) {
        return dragDropActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Drag-drop activity not found."));
    }

    private List<DragDropItem> loadDragDropItems(Long activityId) {
        return dragDropItemRepository.findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(activityId);
    }

    private List<DragDropItem> saveDragDropItems(DragDropActivity parent, AdminDragDropActivityRequest request) {
        if (request.items().isEmpty()) {
            return List.of();
        }
        List<DragDropItem> entities = request.items().stream()
                .map(item -> mapper.toEntity(item, parent))
                .toList();
        return dragDropItemRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStoryActivityResponse> listStoryActivities(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<StoryActivity> result = storyActivityRepository.findAll(filterSpec(filter), pageable);
        List<AdminStoryActivityResponse> items = result.getContent().stream()
                .map(story -> mapper.toResponse(story, loadStoryQuestions(story.getId())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminStoryActivityResponse getStoryActivity(Long id) {
        StoryActivity entity = findStoryActivity(id);
        return mapper.toResponse(entity, loadStoryQuestions(id));
    }

    @Transactional
    public AdminStoryActivityResponse createStoryActivity(SecurityUserDetails currentUser, AdminStoryActivityRequest request) {
        User admin = findActor(currentUser);
        StoryActivity entity = mapper.toEntity(request, admin);
        StoryActivity saved = storyActivityRepository.save(entity);
        List<StoryQuestion> questions = saveStoryQuestions(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_CREATE", "StoryActivity", saved.getId(),
                "Admin created a story activity.");
        return mapper.toResponse(saved, questions);
    }

    @Transactional
    public AdminStoryActivityResponse updateStoryActivity(SecurityUserDetails currentUser, Long id, AdminStoryActivityRequest request) {
        User admin = findActor(currentUser);
        StoryActivity entity = findStoryActivity(id);
        mapper.applyTo(request, entity);
        StoryActivity saved = storyActivityRepository.save(entity);
        storyQuestionRepository.deleteByStoryActivityId(saved.getId());
        List<StoryQuestion> questions = saveStoryQuestions(saved, request);
        auditLogService.record(admin, "ADMIN_ACTIVITY_UPDATE", "StoryActivity", saved.getId(),
                "Admin updated a story activity.");
        return mapper.toResponse(saved, questions);
    }

    @Transactional
    public AdminStoryActivityResponse updateStoryActivityStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        StoryActivity entity = findStoryActivity(id);
        entity.setContentStatus(target);
        StoryActivity saved = storyActivityRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "StoryActivity", saved.getId(),
                "Admin set story activity status to " + target + ".");
        return mapper.toResponse(saved, loadStoryQuestions(saved.getId()));
    }

    @Transactional
    public void deleteStoryActivity(SecurityUserDetails currentUser, Long id) {
        User admin = findActor(currentUser);
        StoryActivity entity = findStoryActivity(id);
        storyQuestionRepository.deleteByStoryActivityId(entity.getId());
        storyActivityRepository.delete(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_DELETE", "StoryActivity", id,
                "Admin deleted a story activity.");
    }

    private StoryActivity findStoryActivity(Long id) {
        return storyActivityRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Story activity not found."));
    }

    private List<StoryQuestion> loadStoryQuestions(Long storyId) {
        return storyQuestionRepository.findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(storyId);
    }

    private List<StoryQuestion> saveStoryQuestions(StoryActivity parent, AdminStoryActivityRequest request) {
        if (request.questions().isEmpty()) {
            return List.of();
        }
        List<StoryQuestion> entities = request.questions().stream()
                .map(question -> mapper.toEntity(question, parent))
                .toList();
        return storyQuestionRepository.saveAll(entities);
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActivitySummaryResponse> listAllActivities(
            ActivityFilter filter,
            int page,
            int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        Sort sort = Sort.by(Sort.Order.desc("updatedAt"), Sort.Order.desc("id"));
        List<ActivityCursor<?>> cursors = new ArrayList<>();
        long totalItems = 0L;
        if (includesActivityType(filter, ActivityType.FLASHCARD)) {
            ActivityCursor<Flashcard> cursor = new ActivityCursor<>(
                    pageable -> flashcardRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }
        if (includesActivityType(filter, ActivityType.MATCHING_GAME)) {
            ActivityCursor<MatchingGame> cursor = new ActivityCursor<>(
                    pageable -> matchingGameRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }
        if (includesActivityType(filter, ActivityType.LISTENING_EXERCISE)) {
            ActivityCursor<ListeningExercise> cursor = new ActivityCursor<>(
                    pageable -> listeningExerciseRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }
        if (includesActivityType(filter, ActivityType.TRACING_EXERCISE)) {
            ActivityCursor<TracingExercise> cursor = new ActivityCursor<>(
                    pageable -> tracingExerciseRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }
        if (includesActivityType(filter, ActivityType.DRAG_DROP_ACTIVITY)) {
            ActivityCursor<DragDropActivity> cursor = new ActivityCursor<>(
                    pageable -> dragDropActivityRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }
        if (includesActivityType(filter, ActivityType.STORY_ACTIVITY)) {
            ActivityCursor<StoryActivity> cursor = new ActivityCursor<>(
                    pageable -> storyActivityRepository.findAll(filterSpec(filter), pageable), mapper::toSummary, sort);
            cursors.add(cursor);
            totalItems += cursor.totalItems();
        }

        List<AdminActivitySummaryResponse> items = new ArrayList<>(safeSize);
        long firstIndex = (long) safePage * safeSize;
        if (firstIndex < totalItems) {
            Comparator<AdminActivitySummaryResponse> newestFirst = Comparator
                    .comparing(AdminActivitySummaryResponse::updatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(AdminActivitySummaryResponse::id, Comparator.reverseOrder());
            long seen = 0L;
            while (seen < firstIndex + safeSize) {
                ActivityCursor<?> next = null;
                AdminActivitySummaryResponse nextItem = null;
                for (ActivityCursor<?> cursor : cursors) {
                    AdminActivitySummaryResponse candidate = cursor.peek();
                    if (candidate != null && (nextItem == null || newestFirst.compare(candidate, nextItem) < 0)) {
                        next = cursor;
                        nextItem = candidate;
                    }
                }
                if (next == null) {
                    break;
                }
                next.advance();
                if (seen++ >= firstIndex) {
                    items.add(nextItem);
                }
            }
        }
        int totalPages = PaginationUtils.totalPages(totalItems, safeSize);
        return new PageResponse<>(items, safePage, safeSize, totalItems, totalPages);
    }

    private static final class ActivityCursor<T> {
        private final Function<Pageable, Page<T>> fetch;
        private final Function<T, AdminActivitySummaryResponse> map;
        private final Sort sort;
        private List<T> batch;
        private int page;
        private int index;
        private long totalItems;
        private AdminActivitySummaryResponse pending;

        private ActivityCursor(Function<Pageable, Page<T>> fetch, Function<T, AdminActivitySummaryResponse> map, Sort sort) {
            this.fetch = fetch;
            this.map = map;
            this.sort = sort;
            loadPage();
        }

        private long totalItems() {
            return totalItems;
        }

        private AdminActivitySummaryResponse peek() {
            if (index >= batch.size()) {
                if ((long) page * MERGED_FEED_BATCH_SIZE >= totalItems) {
                    return null;
                }
                loadPage();
            }
            if (index >= batch.size()) {
                return null;
            }
            if (pending == null) {
                pending = map.apply(batch.get(index));
            }
            return pending;
        }

        private void advance() {
            index++;
            pending = null;
        }

        private void loadPage() {
            Page<T> result = fetch.apply(PageRequest.of(page++, MERGED_FEED_BATCH_SIZE, sort));
            totalItems = result.getTotalElements();
            batch = result.getContent();
            index = 0;
            pending = null;
        }
    }

    private User findActor(SecurityUserDetails currentUser) {
        return userRepository.findById(currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Admin user could not be found."));
    }

    private ContentStatus validateTargetStatus(AdminActivityStatusUpdateRequest request) {
        ContentStatus target = request.status();
        if (target == null || !ALLOWED_TARGET_STATUSES.contains(target)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Status must be one of DRAFT, PUBLISHED, ARCHIVED.");
        }
        return target;
    }

    private Pageable buildPageable(int page, int size, String sort) {
        Sort sortSpec = resolveSort(sort);
        return PageRequest.of(PaginationUtils.sanitizePage(page), PaginationUtils.sanitizeSize(size), sortSpec);
    }

    private Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.desc("updatedAt"));
        }
        String[] parts = sort.split(",");
        String field = parts[0].strip();
        Sort.Direction direction = parts.length > 1 && parts[1].strip().equalsIgnoreCase("asc")
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;
        String safeField = switch (field) {
            case "title", "createdAt", "updatedAt", "hskLevel", "ageBand",
                    "difficulty", "contentStatus", "xpReward" -> field;
            default -> "updatedAt";
        };
        return Sort.by(direction, safeField);
    }

    private <T, R> PageResponse<R> toPageResponse(List<R> items, Page<T> page) {
        return new PageResponse<>(items, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }

    private static boolean includesActivityType(ActivityFilter filter, ActivityType type) {
        return filter == null || filter.type() == null || filter.type() == type;
    }

    private <T> Specification<T> filterSpec(ActivityFilter filter) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (filter == null) {
                return cb.conjunction();
            }
            if (filter.status() != null) {
                predicates.add(cb.equal(root.get("contentStatus"), filter.status()));
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
                        filter.topic().strip().toLowerCase(Locale.ROOT)));
            }
            if (filter.tag() != null && !filter.tag().isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("tags")),
                        "%" + filter.tag().strip().toLowerCase(Locale.ROOT) + "%"));
            }
            if (filter.createdByUserId() != null) {
                predicates.add(cb.equal(root.get("createdByUser").get("id"), filter.createdByUserId()));
            }
            if (filter.keyword() != null && !filter.keyword().isBlank()) {
                String pattern = "%" + filter.keyword().strip().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.like(cb.lower(root.get("title")), pattern));
            }
            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    public record ActivityFilter(
            ContentStatus status,
            Integer hskLevel,
            AgeBand ageBand,
            ActivityDifficulty difficulty,
            String topic,
            String tag,
            Long createdByUserId,
            String keyword,
            ActivityType type) {

        public static ActivityFilter of(
                Optional<String> status,
                Optional<Integer> hskLevel,
                Optional<String> ageBand,
                Optional<String> difficulty,
                Optional<String> topic,
                Optional<String> tag,
                Optional<Long> createdByUserId,
                Optional<String> keyword,
                Optional<String> type) {
            return new ActivityFilter(
                    status.map(value -> requireMaxLength(value, 40, "status")).map(ActivityFilter::parseStatus).orElse(null),
                    hskLevel.orElse(null),
                    ageBand.map(value -> requireMaxLength(value, 40, "ageBand")).map(ActivityFilter::parseAgeBand).orElse(null),
                    difficulty.map(value -> requireMaxLength(value, 40, "difficulty")).map(ActivityFilter::parseDifficulty).orElse(null),
                    topic.map(value -> requireMaxLength(value, 200, "topic")).orElse(null),
                    tag.map(value -> requireMaxLength(value, 100, "tag")).orElse(null),
                    createdByUserId.orElse(null),
                    keyword.map(value -> requireMaxLength(value, 200, "keyword")).orElse(null),
                    type.map(value -> requireMaxLength(value, 60, "type")).map(ActivityFilter::parseType).orElse(null));
        }

        private static String requireMaxLength(String raw, int maxLength, String fieldName) {
            String trimmed = raw.strip();
            if (trimmed.length() > maxLength) {
                throw new ResponseStatusException(BAD_REQUEST, fieldName + " is too long.");
            }
            return trimmed;
        }

        private static ContentStatus parseStatus(String raw) {
            try {
                return ContentStatus.fromValue(raw);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown status: " + raw);
            }
        }

        private static AgeBand parseAgeBand(String raw) {
            try {
                return AgeBand.fromValue(raw);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown ageBand: " + raw);
            }
        }

        private static ActivityDifficulty parseDifficulty(String raw) {
            try {
                return ActivityDifficulty.fromValue(raw);
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown difficulty: " + raw);
            }
        }

        private static ActivityType parseType(String raw) {
            try {
                return ActivityType.parseLenient(raw)
                        .orElseThrow(() -> new IllegalArgumentException("Unknown activity type: " + raw));
            } catch (IllegalArgumentException ex) {
                throw new ResponseStatusException(BAD_REQUEST, "Unknown activity type: " + raw);
            }
        }
    }
}
