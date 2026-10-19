package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

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
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.StoryActivity;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.ReviewContentType;
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
    private final ContentReviewService contentReviewService;
    private final ContentSafetyService contentSafetyService;

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
            AuditLogService auditLogService,
            ContentReviewService contentReviewService,
            ContentSafetyService contentSafetyService) {
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
        this.contentReviewService = Objects.requireNonNull(contentReviewService, "contentReviewService");
        this.contentSafetyService = Objects.requireNonNull(contentSafetyService, "contentSafetyService");
    }

    /**
     * Who performs an activity operation. Admins manage the whole library; teachers (D5) only
     * their own items ({@code created_by_user_id}), which follow the review workflow.
     */
    public record Actor(User user, boolean teacher) {

        public Actor {
            Objects.requireNonNull(user, "user must not be null");
        }

        /** Owner restriction for look-ups: the teacher's own user id, or {@code null} for admins. */
        Long ownerUserId() {
            return teacher ? user.getId() : null;
        }

        String auditPrefix() {
            return teacher ? "TEACHER_ACTIVITY_" : "ADMIN_ACTIVITY_";
        }

        String auditActor() {
            return teacher ? "Teacher" : "Admin";
        }
    }

    public Actor adminActor(SecurityUserDetails currentUser) {
        return new Actor(findActor(currentUser), false);
    }

    public Actor teacherActor(SecurityUserDetails currentUser) {
        return new Actor(findActor(currentUser), true);
    }

    // ===== Flashcards =====

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
        return getFlashcard(id, null);
    }

    @Transactional(readOnly = true)
    public AdminFlashcardResponse getFlashcard(Long id, Long ownerUserId) {
        return mapper.toResponse(findFlashcard(id, ownerUserId));
    }

    @Transactional
    public AdminFlashcardResponse createFlashcard(SecurityUserDetails currentUser, AdminFlashcardRequest request) {
        return createFlashcard(adminActor(currentUser), request);
    }

    @Transactional
    public AdminFlashcardResponse createFlashcard(Actor actor, AdminFlashcardRequest request) {
        screen(request);
        Flashcard saved = flashcardRepository.save(mapper.toEntity(request, actor.user()));
        auditCreate(actor, "Flashcard", saved.getId(), "a flashcard");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminFlashcardResponse updateFlashcard(SecurityUserDetails currentUser, Long id, AdminFlashcardRequest request) {
        return updateFlashcard(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminFlashcardResponse updateFlashcard(Actor actor, Long id, AdminFlashcardRequest request) {
        screen(request);
        Flashcard entity = findFlashcard(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.FLASHCARD, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.FLASHCARD, entity);
        Flashcard saved = flashcardRepository.save(entity);
        auditUpdate(actor, "Flashcard", saved.getId(), "a flashcard");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminFlashcardResponse updateFlashcardStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        Flashcard entity = findFlashcard(id, null);
        applyAdminStatus(admin, ReviewContentType.FLASHCARD, entity, target);
        Flashcard saved = flashcardRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "Flashcard", saved.getId(),
                "Admin set flashcard status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteFlashcard(SecurityUserDetails currentUser, Long id) {
        deleteFlashcard(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteFlashcard(Actor actor, Long id) {
        Flashcard entity = findFlashcard(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        flashcardRepository.delete(entity);
        auditDelete(actor, "Flashcard", id, "a flashcard");
    }

    @Transactional
    public AdminFlashcardResponse submitFlashcard(Actor actor, Long id) {
        Flashcard entity = findFlashcard(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.FLASHCARD, entity);
        return mapper.toResponse(flashcardRepository.save(entity));
    }

    private Flashcard findFlashcard(Long id, Long ownerUserId) {
        return flashcardRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Flashcard not found."));
    }

    // ===== Matching games =====

    @Transactional(readOnly = true)
    public PageResponse<AdminMatchingGameResponse> listMatchingGames(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<MatchingGame> result = matchingGameRepository.findAll(filterSpec(filter), pageable);
        Map<Long, List<MatchingGameItem>> itemsByGameId = childrenByParentId(
                result.getContent(),
                matchingGameItemRepository::findByMatchingGameIdInOrderBySortOrderAscCreatedAtAsc,
                item -> item.getMatchingGame().getId());
        List<AdminMatchingGameResponse> items = result.getContent().stream()
                .map(game -> mapper.toResponse(game, itemsByGameId.getOrDefault(game.getId(), List.of())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminMatchingGameResponse getMatchingGame(Long id) {
        return getMatchingGame(id, null);
    }

    @Transactional(readOnly = true)
    public AdminMatchingGameResponse getMatchingGame(Long id, Long ownerUserId) {
        MatchingGame entity = findMatchingGame(id, ownerUserId);
        return mapper.toResponse(entity, loadMatchingGameItems(id));
    }

    @Transactional
    public AdminMatchingGameResponse createMatchingGame(SecurityUserDetails currentUser, AdminMatchingGameRequest request) {
        return createMatchingGame(adminActor(currentUser), request);
    }

    @Transactional
    public AdminMatchingGameResponse createMatchingGame(Actor actor, AdminMatchingGameRequest request) {
        screen(request);
        MatchingGame saved = matchingGameRepository.save(mapper.toEntity(request, actor.user()));
        List<MatchingGameItem> items = saveMatchingGameItems(saved, request);
        auditCreate(actor, "MatchingGame", saved.getId(), "a matching game");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminMatchingGameResponse updateMatchingGame(SecurityUserDetails currentUser, Long id, AdminMatchingGameRequest request) {
        return updateMatchingGame(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminMatchingGameResponse updateMatchingGame(Actor actor, Long id, AdminMatchingGameRequest request) {
        screen(request);
        MatchingGame entity = findMatchingGame(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.MATCHING_GAME, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.MATCHING_GAME, entity);
        MatchingGame saved = matchingGameRepository.save(entity);
        matchingGameItemRepository.deleteByMatchingGameId(saved.getId());
        List<MatchingGameItem> items = saveMatchingGameItems(saved, request);
        auditUpdate(actor, "MatchingGame", saved.getId(), "a matching game");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminMatchingGameResponse updateMatchingGameStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        MatchingGame entity = findMatchingGame(id, null);
        applyAdminStatus(admin, ReviewContentType.MATCHING_GAME, entity, target);
        MatchingGame saved = matchingGameRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "MatchingGame", saved.getId(),
                "Admin set matching game status to " + target + ".");
        return mapper.toResponse(saved, loadMatchingGameItems(saved.getId()));
    }

    @Transactional
    public void deleteMatchingGame(SecurityUserDetails currentUser, Long id) {
        deleteMatchingGame(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteMatchingGame(Actor actor, Long id) {
        MatchingGame entity = findMatchingGame(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        matchingGameItemRepository.deleteByMatchingGameId(entity.getId());
        matchingGameRepository.delete(entity);
        auditDelete(actor, "MatchingGame", id, "a matching game");
    }

    @Transactional
    public AdminMatchingGameResponse submitMatchingGame(Actor actor, Long id) {
        MatchingGame entity = findMatchingGame(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.MATCHING_GAME, entity);
        MatchingGame saved = matchingGameRepository.save(entity);
        return mapper.toResponse(saved, loadMatchingGameItems(saved.getId()));
    }

    private MatchingGame findMatchingGame(Long id, Long ownerUserId) {
        return matchingGameRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Matching game not found."));
    }

    private List<MatchingGameItem> loadMatchingGameItems(Long gameId) {
        return matchingGameItemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(gameId);
    }

    private List<MatchingGameItem> saveMatchingGameItems(MatchingGame owner, AdminMatchingGameRequest request) {
        if (request.items().isEmpty()) {
            return List.of();
        }
        List<MatchingGameItem> entities = request.items().stream()
                .map(item -> mapper.toEntity(item, owner))
                .toList();
        return matchingGameItemRepository.saveAll(entities);
    }

    // ===== Listening exercises =====

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
        return getListeningExercise(id, null);
    }

    @Transactional(readOnly = true)
    public AdminListeningExerciseResponse getListeningExercise(Long id, Long ownerUserId) {
        return mapper.toResponse(findListeningExercise(id, ownerUserId));
    }

    @Transactional
    public AdminListeningExerciseResponse createListeningExercise(SecurityUserDetails currentUser, AdminListeningExerciseRequest request) {
        return createListeningExercise(adminActor(currentUser), request);
    }

    @Transactional
    public AdminListeningExerciseResponse createListeningExercise(Actor actor, AdminListeningExerciseRequest request) {
        screen(request);
        ListeningExercise saved = listeningExerciseRepository.save(mapper.toEntity(request, actor.user()));
        auditCreate(actor, "ListeningExercise", saved.getId(), "a listening exercise");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminListeningExerciseResponse updateListeningExercise(SecurityUserDetails currentUser, Long id, AdminListeningExerciseRequest request) {
        return updateListeningExercise(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminListeningExerciseResponse updateListeningExercise(Actor actor, Long id, AdminListeningExerciseRequest request) {
        screen(request);
        ListeningExercise entity = findListeningExercise(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.LISTENING_EXERCISE, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.LISTENING_EXERCISE, entity);
        ListeningExercise saved = listeningExerciseRepository.save(entity);
        auditUpdate(actor, "ListeningExercise", saved.getId(), "a listening exercise");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminListeningExerciseResponse updateListeningExerciseStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        ListeningExercise entity = findListeningExercise(id, null);
        applyAdminStatus(admin, ReviewContentType.LISTENING_EXERCISE, entity, target);
        ListeningExercise saved = listeningExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "ListeningExercise", saved.getId(),
                "Admin set listening exercise status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteListeningExercise(SecurityUserDetails currentUser, Long id) {
        deleteListeningExercise(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteListeningExercise(Actor actor, Long id) {
        ListeningExercise entity = findListeningExercise(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        listeningExerciseRepository.delete(entity);
        auditDelete(actor, "ListeningExercise", id, "a listening exercise");
    }

    @Transactional
    public AdminListeningExerciseResponse submitListeningExercise(Actor actor, Long id) {
        ListeningExercise entity = findListeningExercise(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.LISTENING_EXERCISE, entity);
        return mapper.toResponse(listeningExerciseRepository.save(entity));
    }

    private ListeningExercise findListeningExercise(Long id, Long ownerUserId) {
        return listeningExerciseRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Listening exercise not found."));
    }

    // ===== Tracing exercises =====

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
        return getTracingExercise(id, null);
    }

    @Transactional(readOnly = true)
    public AdminTracingExerciseResponse getTracingExercise(Long id, Long ownerUserId) {
        return mapper.toResponse(findTracingExercise(id, ownerUserId));
    }

    @Transactional
    public AdminTracingExerciseResponse createTracingExercise(SecurityUserDetails currentUser, AdminTracingExerciseRequest request) {
        return createTracingExercise(adminActor(currentUser), request);
    }

    @Transactional
    public AdminTracingExerciseResponse createTracingExercise(Actor actor, AdminTracingExerciseRequest request) {
        screen(request);
        TracingExercise saved = tracingExerciseRepository.save(mapper.toEntity(request, actor.user()));
        auditCreate(actor, "TracingExercise", saved.getId(), "a tracing exercise");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminTracingExerciseResponse updateTracingExercise(SecurityUserDetails currentUser, Long id, AdminTracingExerciseRequest request) {
        return updateTracingExercise(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminTracingExerciseResponse updateTracingExercise(Actor actor, Long id, AdminTracingExerciseRequest request) {
        screen(request);
        TracingExercise entity = findTracingExercise(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.TRACING_EXERCISE, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.TRACING_EXERCISE, entity);
        TracingExercise saved = tracingExerciseRepository.save(entity);
        auditUpdate(actor, "TracingExercise", saved.getId(), "a tracing exercise");
        return mapper.toResponse(saved);
    }

    @Transactional
    public AdminTracingExerciseResponse updateTracingExerciseStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        TracingExercise entity = findTracingExercise(id, null);
        applyAdminStatus(admin, ReviewContentType.TRACING_EXERCISE, entity, target);
        TracingExercise saved = tracingExerciseRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "TracingExercise", saved.getId(),
                "Admin set tracing exercise status to " + target + ".");
        return mapper.toResponse(saved);
    }

    @Transactional
    public void deleteTracingExercise(SecurityUserDetails currentUser, Long id) {
        deleteTracingExercise(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteTracingExercise(Actor actor, Long id) {
        TracingExercise entity = findTracingExercise(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        tracingExerciseRepository.delete(entity);
        auditDelete(actor, "TracingExercise", id, "a tracing exercise");
    }

    @Transactional
    public AdminTracingExerciseResponse submitTracingExercise(Actor actor, Long id) {
        TracingExercise entity = findTracingExercise(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.TRACING_EXERCISE, entity);
        return mapper.toResponse(tracingExerciseRepository.save(entity));
    }

    private TracingExercise findTracingExercise(Long id, Long ownerUserId) {
        return tracingExerciseRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Tracing exercise not found."));
    }

    // ===== Drag-and-drop activities =====

    @Transactional(readOnly = true)
    public PageResponse<AdminDragDropActivityResponse> listDragDropActivities(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<DragDropActivity> result = dragDropActivityRepository.findAll(filterSpec(filter), pageable);
        Map<Long, List<DragDropItem>> itemsByActivityId = childrenByParentId(
                result.getContent(),
                dragDropItemRepository::findByDragDropActivityIdInOrderBySortOrderAscCreatedAtAsc,
                item -> item.getDragDropActivity().getId());
        List<AdminDragDropActivityResponse> items = result.getContent().stream()
                .map(activity -> mapper.toResponse(activity, itemsByActivityId.getOrDefault(activity.getId(), List.of())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminDragDropActivityResponse getDragDropActivity(Long id) {
        return getDragDropActivity(id, null);
    }

    @Transactional(readOnly = true)
    public AdminDragDropActivityResponse getDragDropActivity(Long id, Long ownerUserId) {
        DragDropActivity entity = findDragDropActivity(id, ownerUserId);
        return mapper.toResponse(entity, loadDragDropItems(id));
    }

    @Transactional
    public AdminDragDropActivityResponse createDragDropActivity(SecurityUserDetails currentUser, AdminDragDropActivityRequest request) {
        return createDragDropActivity(adminActor(currentUser), request);
    }

    @Transactional
    public AdminDragDropActivityResponse createDragDropActivity(Actor actor, AdminDragDropActivityRequest request) {
        screen(request);
        DragDropActivity saved = dragDropActivityRepository.save(mapper.toEntity(request, actor.user()));
        List<DragDropItem> items = saveDragDropItems(saved, request);
        auditCreate(actor, "DragDropActivity", saved.getId(), "a drag-drop activity");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminDragDropActivityResponse updateDragDropActivity(SecurityUserDetails currentUser, Long id, AdminDragDropActivityRequest request) {
        return updateDragDropActivity(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminDragDropActivityResponse updateDragDropActivity(Actor actor, Long id, AdminDragDropActivityRequest request) {
        screen(request);
        DragDropActivity entity = findDragDropActivity(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.DRAG_DROP_ACTIVITY, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.DRAG_DROP_ACTIVITY, entity);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        dragDropItemRepository.deleteByDragDropActivityId(saved.getId());
        List<DragDropItem> items = saveDragDropItems(saved, request);
        auditUpdate(actor, "DragDropActivity", saved.getId(), "a drag-drop activity");
        return mapper.toResponse(saved, items);
    }

    @Transactional
    public AdminDragDropActivityResponse updateDragDropActivityStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        DragDropActivity entity = findDragDropActivity(id, null);
        applyAdminStatus(admin, ReviewContentType.DRAG_DROP_ACTIVITY, entity, target);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "DragDropActivity", saved.getId(),
                "Admin set drag-drop activity status to " + target + ".");
        return mapper.toResponse(saved, loadDragDropItems(saved.getId()));
    }

    @Transactional
    public void deleteDragDropActivity(SecurityUserDetails currentUser, Long id) {
        deleteDragDropActivity(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteDragDropActivity(Actor actor, Long id) {
        DragDropActivity entity = findDragDropActivity(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        dragDropItemRepository.deleteByDragDropActivityId(entity.getId());
        dragDropActivityRepository.delete(entity);
        auditDelete(actor, "DragDropActivity", id, "a drag-drop activity");
    }

    @Transactional
    public AdminDragDropActivityResponse submitDragDropActivity(Actor actor, Long id) {
        DragDropActivity entity = findDragDropActivity(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.DRAG_DROP_ACTIVITY, entity);
        DragDropActivity saved = dragDropActivityRepository.save(entity);
        return mapper.toResponse(saved, loadDragDropItems(saved.getId()));
    }

    private DragDropActivity findDragDropActivity(Long id, Long ownerUserId) {
        return dragDropActivityRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Drag-drop activity not found."));
    }

    private List<DragDropItem> loadDragDropItems(Long activityId) {
        return dragDropItemRepository.findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(activityId);
    }

    private List<DragDropItem> saveDragDropItems(DragDropActivity owner, AdminDragDropActivityRequest request) {
        if (request.items().isEmpty()) {
            return List.of();
        }
        List<DragDropItem> entities = request.items().stream()
                .map(item -> mapper.toEntity(item, owner))
                .toList();
        return dragDropItemRepository.saveAll(entities);
    }

    // ===== Story activities =====

    @Transactional(readOnly = true)
    public PageResponse<AdminStoryActivityResponse> listStoryActivities(ActivityFilter filter, int page, int size, String sort) {
        Pageable pageable = buildPageable(page, size, sort);
        Page<StoryActivity> result = storyActivityRepository.findAll(filterSpec(filter), pageable);
        Map<Long, List<StoryQuestion>> questionsByStoryId = childrenByParentId(
                result.getContent(),
                storyQuestionRepository::findByStoryActivityIdInOrderBySortOrderAscCreatedAtAsc,
                question -> question.getStoryActivity().getId());
        List<AdminStoryActivityResponse> items = result.getContent().stream()
                .map(story -> mapper.toResponse(story, questionsByStoryId.getOrDefault(story.getId(), List.of())))
                .toList();
        return toPageResponse(items, result);
    }

    @Transactional(readOnly = true)
    public AdminStoryActivityResponse getStoryActivity(Long id) {
        return getStoryActivity(id, null);
    }

    @Transactional(readOnly = true)
    public AdminStoryActivityResponse getStoryActivity(Long id, Long ownerUserId) {
        StoryActivity entity = findStoryActivity(id, ownerUserId);
        return mapper.toResponse(entity, loadStoryQuestions(id));
    }

    @Transactional
    public AdminStoryActivityResponse createStoryActivity(SecurityUserDetails currentUser, AdminStoryActivityRequest request) {
        return createStoryActivity(adminActor(currentUser), request);
    }

    @Transactional
    public AdminStoryActivityResponse createStoryActivity(Actor actor, AdminStoryActivityRequest request) {
        screen(request);
        StoryActivity saved = storyActivityRepository.save(mapper.toEntity(request, actor.user()));
        List<StoryQuestion> questions = saveStoryQuestions(saved, request);
        auditCreate(actor, "StoryActivity", saved.getId(), "a story activity");
        return mapper.toResponse(saved, questions);
    }

    @Transactional
    public AdminStoryActivityResponse updateStoryActivity(SecurityUserDetails currentUser, Long id, AdminStoryActivityRequest request) {
        return updateStoryActivity(adminActor(currentUser), id, request);
    }

    @Transactional
    public AdminStoryActivityResponse updateStoryActivity(Actor actor, Long id, AdminStoryActivityRequest request) {
        screen(request);
        StoryActivity entity = findStoryActivity(id, actor.ownerUserId());
        beforeEdit(actor, ReviewContentType.STORY_ACTIVITY, entity);
        mapper.applyTo(request, entity);
        afterEdit(actor, ReviewContentType.STORY_ACTIVITY, entity);
        StoryActivity saved = storyActivityRepository.save(entity);
        storyQuestionRepository.deleteByStoryActivityId(saved.getId());
        List<StoryQuestion> questions = saveStoryQuestions(saved, request);
        auditUpdate(actor, "StoryActivity", saved.getId(), "a story activity");
        return mapper.toResponse(saved, questions);
    }

    @Transactional
    public AdminStoryActivityResponse updateStoryActivityStatus(SecurityUserDetails currentUser, Long id,
            AdminActivityStatusUpdateRequest request) {
        ContentStatus target = validateTargetStatus(request);
        User admin = findActor(currentUser);
        StoryActivity entity = findStoryActivity(id, null);
        applyAdminStatus(admin, ReviewContentType.STORY_ACTIVITY, entity, target);
        StoryActivity saved = storyActivityRepository.save(entity);
        auditLogService.record(admin, "ADMIN_ACTIVITY_STATUS", "StoryActivity", saved.getId(),
                "Admin set story activity status to " + target + ".");
        return mapper.toResponse(saved, loadStoryQuestions(saved.getId()));
    }

    @Transactional
    public void deleteStoryActivity(SecurityUserDetails currentUser, Long id) {
        deleteStoryActivity(adminActor(currentUser), id);
    }

    @Transactional
    public void deleteStoryActivity(Actor actor, Long id) {
        StoryActivity entity = findStoryActivity(id, actor.ownerUserId());
        beforeDelete(actor, entity);
        storyQuestionRepository.deleteByStoryActivityId(entity.getId());
        storyActivityRepository.delete(entity);
        auditDelete(actor, "StoryActivity", id, "a story activity");
    }

    @Transactional
    public AdminStoryActivityResponse submitStoryActivity(Actor actor, Long id) {
        StoryActivity entity = findStoryActivity(id, actor.ownerUserId());
        contentReviewService.submitForReview(actor.user(), ReviewContentType.STORY_ACTIVITY, entity);
        StoryActivity saved = storyActivityRepository.save(entity);
        return mapper.toResponse(saved, loadStoryQuestions(saved.getId()));
    }

    private StoryActivity findStoryActivity(Long id, Long ownerUserId) {
        return storyActivityRepository.findById(id)
                .filter(entity -> isOwnedBy(entity, ownerUserId))
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Story activity not found."));
    }

    private List<StoryQuestion> loadStoryQuestions(Long storyId) {
        return storyQuestionRepository.findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(storyId);
    }

    private List<StoryQuestion> saveStoryQuestions(StoryActivity owner, AdminStoryActivityRequest request) {
        if (request.questions().isEmpty()) {
            return List.of();
        }
        List<StoryQuestion> entities = request.questions().stream()
                .map(question -> mapper.toEntity(question, owner))
                .toList();
        return storyQuestionRepository.saveAll(entities);
    }

    // ===== Shared workflow helpers =====

    /**
     * Loads the children of one page of parents with a single query and groups them by parent id.
     * Each group keeps the finder's order (sort order, then creation time).
     */
    private static <C> Map<Long, List<C>> childrenByParentId(
            List<? extends PracticeActivityContent> parents,
            Function<Collection<Long>, List<C>> batchFinder,
            Function<C, Long> parentIdOf) {
        if (parents.isEmpty()) {
            return Map.of();
        }
        List<Long> parentIds = parents.stream().map(PracticeActivityContent::getId).toList();
        return batchFinder.apply(parentIds).stream().collect(Collectors.groupingBy(parentIdOf));
    }

    private static boolean isOwnedBy(PracticeActivityContent entity, Long ownerUserId) {
        if (ownerUserId == null) {
            return true;
        }
        User author = entity.getCreatedByUser();
        return author != null && ownerUserId.equals(author.getId());
    }

    // ===== Content safety (same sensitive-word rule as lessons and quizzes) =====

    private void screen(AdminFlashcardRequest request) {
        contentSafetyService.screenText(request.title(), request.frontText(), request.backText(), request.pinyin(),
                request.englishMeaning(), request.malayMeaning(), request.extraPayload(), request.topic(), tags(request.tags()));
    }

    private void screen(AdminMatchingGameRequest request) {
        contentSafetyService.screenText(request.title(), request.topic(), tags(request.tags()));
        if (request.items() != null) {
            request.items().stream().filter(Objects::nonNull).forEach(item ->
                    contentSafetyService.screenText(item.leftText(), item.rightText(), item.pinyin()));
        }
    }

    private void screen(AdminListeningExerciseRequest request) {
        contentSafetyService.screenText(request.title(), request.questionPayload(), request.topic(), tags(request.tags()));
    }

    private void screen(AdminTracingExerciseRequest request) {
        contentSafetyService.screenText(request.title(), request.targetCharacter(), request.pinyin(), request.meaning(),
                request.instructions(), request.topic(), tags(request.tags()));
    }

    private void screen(AdminDragDropActivityRequest request) {
        contentSafetyService.screenText(request.title(), request.instructions(), request.topic(), tags(request.tags()));
        if (request.items() != null) {
            request.items().stream().filter(Objects::nonNull).forEach(item ->
                    contentSafetyService.screenText(item.dragText(), item.targetZone(), item.pinyin()));
        }
    }

    private void screen(AdminStoryActivityRequest request) {
        contentSafetyService.screenText(request.title(), request.instructions(), request.storyText(), request.pinyin(),
                request.translation(), request.topic(), tags(request.tags()));
        if (request.questions() != null) {
            request.questions().stream().filter(Objects::nonNull).forEach(question ->
                    contentSafetyService.screenText(question.questionText(), question.optionsPayload(), question.explanation()));
        }
    }

    private static String tags(List<String> tags) {
        return tags == null ? null : String.join(" ", tags.stream().filter(Objects::nonNull).toList());
    }

    /** Teachers may edit DRAFT, REJECTED and PUBLISHED items only (D24); admins edit any item. */
    private void beforeEdit(Actor actor, ReviewContentType type, PracticeActivityContent entity) {
        if (actor.teacher()) {
            contentReviewService.requireTeacherEditable(entity, type);
        }
    }

    /** A teacher's edit of a PUBLISHED item sends it back to SUBMITTED for re-review. */
    private void afterEdit(Actor actor, ReviewContentType type, PracticeActivityContent entity) {
        if (actor.teacher()) {
            contentReviewService.recordTeacherEdit(actor.user(), type, entity);
        }
    }

    /** Teachers can delete only drafts; published history is archived by an admin instead. */
    private static void beforeDelete(Actor actor, PracticeActivityContent entity) {
        if (actor.teacher() && entity.getContentStatus() != ContentStatus.DRAFT) {
            throw new ResponseStatusException(BAD_REQUEST, "Only draft activities can be deleted.");
        }
    }

    /** Admin library status change; publishing requires a complete activity. */
    private void applyAdminStatus(User admin, ReviewContentType type, PracticeActivityContent entity, ContentStatus target) {
        if (target == ContentStatus.PUBLISHED) {
            contentReviewService.requireComplete(type, entity);
            entity.setRejectionReason(null);
        }
        entity.setContentStatus(target);
        contentReviewService.notifyOwnerOfAdminStatusChange(admin, type, entity);
    }

    private void auditCreate(Actor actor, String targetType, Long id, String label) {
        auditLogService.record(actor.user(), actor.auditPrefix() + "CREATE", targetType, id,
                actor.auditActor() + " created " + label + (actor.teacher() ? " draft." : " in the platform library."));
    }

    private void auditUpdate(Actor actor, String targetType, Long id, String label) {
        auditLogService.record(actor.user(), actor.auditPrefix() + "UPDATE", targetType, id,
                actor.auditActor() + " updated " + label + ".");
    }

    private void auditDelete(Actor actor, String targetType, Long id, String label) {
        auditLogService.record(actor.user(), actor.auditPrefix() + "DELETE", targetType, id,
                actor.auditActor() + " deleted " + label + ".");
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
            case "title", "createdAt", "updatedAt", "hskLevel",
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
            ActivityDifficulty difficulty,
            String topic,
            String tag,
            Long createdByUserId,
            String keyword,
            ActivityType type) {

        public static ActivityFilter of(
                Optional<String> status,
                Optional<Integer> hskLevel,
                Optional<String> difficulty,
                Optional<String> topic,
                Optional<String> tag,
                Optional<Long> createdByUserId,
                Optional<String> keyword,
                Optional<String> type) {
            return new ActivityFilter(
                    status.map(value -> requireMaxLength(value, 40, "status")).map(ActivityFilter::parseStatus).orElse(null),
                    hskLevel.orElse(null),
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
