package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.admin.activity.AdminActivitySummaryResponse;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminTracingExerciseRequest;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AdminActivityService.Actor;
import com.example.aichinese.service.AdminActivityService.ActivityFilter;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Teacher-authored practice activities (D5). Approved teachers create, list, edit, delete drafts
 * of and submit their OWN flashcards, matching games, listening, tracing, drag-and-drop and story
 * activities. All persistence reuses {@link AdminActivityService} with a teacher {@link Actor},
 * which restricts every look-up to {@code created_by_user_id} and applies the review workflow.
 */
@Service
public class TeacherActivityService {

    /** URL slug -> activity type, as used by {@code /api/v1/teachers/activities/{slug}}. */
    private static final Map<String, ActivityType> SLUGS = Map.of(
            "flashcards", ActivityType.FLASHCARD,
            "matching-games", ActivityType.MATCHING_GAME,
            "listening-exercises", ActivityType.LISTENING_EXERCISE,
            "tracing-exercises", ActivityType.TRACING_EXERCISE,
            "drag-drop-activities", ActivityType.DRAG_DROP_ACTIVITY,
            "story-activities", ActivityType.STORY_ACTIVITY);

    private final AdminActivityService adminActivityService;
    private final TeacherContentAccessService teacherContentAccessService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TeacherActivityService(
            AdminActivityService adminActivityService,
            TeacherContentAccessService teacherContentAccessService,
            ObjectMapper objectMapper,
            Validator validator) {
        this.adminActivityService = Objects.requireNonNull(adminActivityService, "adminActivityService");
        this.teacherContentAccessService = Objects.requireNonNull(teacherContentAccessService, "teacherContentAccessService");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.validator = Objects.requireNonNull(validator, "validator");
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminActivitySummaryResponse> listOwn(
            SecurityUserDetails currentUser, String status, String type, String keyword, int page, int size) {
        Actor actor = teacher(currentUser);
        ActivityType activityType = type == null || type.isBlank()
                ? null
                : ActivityType.parseLenient(type)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown activity type: " + type));
        return adminActivityService.listAllActivities(ownFilter(actor, status, keyword, activityType), page, size);
    }

    @Transactional(readOnly = true)
    public PageResponse<?> listOwnOfType(
            SecurityUserDetails currentUser, String slug, String status, int page, int size, String sort) {
        Actor actor = teacher(currentUser);
        ActivityType type = parseSlug(slug);
        ActivityFilter filter = ownFilter(actor, status, null, type);
        return switch (type) {
            case FLASHCARD -> adminActivityService.listFlashcards(filter, page, size, sort);
            case MATCHING_GAME -> adminActivityService.listMatchingGames(filter, page, size, sort);
            case LISTENING_EXERCISE -> adminActivityService.listListeningExercises(filter, page, size, sort);
            case TRACING_EXERCISE -> adminActivityService.listTracingExercises(filter, page, size, sort);
            case DRAG_DROP_ACTIVITY -> adminActivityService.listDragDropActivities(filter, page, size, sort);
            case STORY_ACTIVITY -> adminActivityService.listStoryActivities(filter, page, size, sort);
        };
    }

    @Transactional(readOnly = true)
    public Object get(SecurityUserDetails currentUser, String slug, Long id) {
        Long owner = teacher(currentUser).user().getId();
        return switch (parseSlug(slug)) {
            case FLASHCARD -> adminActivityService.getFlashcard(id, owner);
            case MATCHING_GAME -> adminActivityService.getMatchingGame(id, owner);
            case LISTENING_EXERCISE -> adminActivityService.getListeningExercise(id, owner);
            case TRACING_EXERCISE -> adminActivityService.getTracingExercise(id, owner);
            case DRAG_DROP_ACTIVITY -> adminActivityService.getDragDropActivity(id, owner);
            case STORY_ACTIVITY -> adminActivityService.getStoryActivity(id, owner);
        };
    }

    @Transactional
    public Object create(SecurityUserDetails currentUser, String slug, JsonNode body) {
        ActivityType type = parseSlug(slug);
        Actor actor = teacher(currentUser);
        return switch (type) {
            case FLASHCARD -> adminActivityService.createFlashcard(actor, bind(body, AdminFlashcardRequest.class));
            case MATCHING_GAME -> adminActivityService.createMatchingGame(actor, bind(body, AdminMatchingGameRequest.class));
            case LISTENING_EXERCISE -> adminActivityService.createListeningExercise(actor, bind(body, AdminListeningExerciseRequest.class));
            case TRACING_EXERCISE -> adminActivityService.createTracingExercise(actor, bind(body, AdminTracingExerciseRequest.class));
            case DRAG_DROP_ACTIVITY -> adminActivityService.createDragDropActivity(actor, bind(body, AdminDragDropActivityRequest.class));
            case STORY_ACTIVITY -> adminActivityService.createStoryActivity(actor, bind(body, AdminStoryActivityRequest.class));
        };
    }

    @Transactional
    public Object update(SecurityUserDetails currentUser, String slug, Long id, JsonNode body) {
        ActivityType type = parseSlug(slug);
        Actor actor = teacher(currentUser);
        return switch (type) {
            case FLASHCARD -> adminActivityService.updateFlashcard(actor, id, bind(body, AdminFlashcardRequest.class));
            case MATCHING_GAME -> adminActivityService.updateMatchingGame(actor, id, bind(body, AdminMatchingGameRequest.class));
            case LISTENING_EXERCISE -> adminActivityService.updateListeningExercise(actor, id, bind(body, AdminListeningExerciseRequest.class));
            case TRACING_EXERCISE -> adminActivityService.updateTracingExercise(actor, id, bind(body, AdminTracingExerciseRequest.class));
            case DRAG_DROP_ACTIVITY -> adminActivityService.updateDragDropActivity(actor, id, bind(body, AdminDragDropActivityRequest.class));
            case STORY_ACTIVITY -> adminActivityService.updateStoryActivity(actor, id, bind(body, AdminStoryActivityRequest.class));
        };
    }

    @Transactional
    public void deleteDraft(SecurityUserDetails currentUser, String slug, Long id) {
        ActivityType type = parseSlug(slug);
        Actor actor = teacher(currentUser);
        switch (type) {
            case FLASHCARD -> adminActivityService.deleteFlashcard(actor, id);
            case MATCHING_GAME -> adminActivityService.deleteMatchingGame(actor, id);
            case LISTENING_EXERCISE -> adminActivityService.deleteListeningExercise(actor, id);
            case TRACING_EXERCISE -> adminActivityService.deleteTracingExercise(actor, id);
            case DRAG_DROP_ACTIVITY -> adminActivityService.deleteDragDropActivity(actor, id);
            case STORY_ACTIVITY -> adminActivityService.deleteStoryActivity(actor, id);
        }
    }

    @Transactional
    public Object submit(SecurityUserDetails currentUser, String slug, Long id) {
        ActivityType type = parseSlug(slug);
        Actor actor = teacher(currentUser);
        return switch (type) {
            case FLASHCARD -> adminActivityService.submitFlashcard(actor, id);
            case MATCHING_GAME -> adminActivityService.submitMatchingGame(actor, id);
            case LISTENING_EXERCISE -> adminActivityService.submitListeningExercise(actor, id);
            case TRACING_EXERCISE -> adminActivityService.submitTracingExercise(actor, id);
            case DRAG_DROP_ACTIVITY -> adminActivityService.submitDragDropActivity(actor, id);
            case STORY_ACTIVITY -> adminActivityService.submitStoryActivity(actor, id);
        };
    }

    private Actor teacher(SecurityUserDetails currentUser) {
        teacherContentAccessService.requireApprovedTeacher(currentUser);
        return adminActivityService.teacherActor(currentUser);
    }

    private static ActivityFilter ownFilter(Actor actor, String status, String keyword, ActivityType type) {
        ContentStatus statusFilter = status == null || status.isBlank()
                ? null
                : ContentStatus.tryFrom(status)
                        .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Unknown status: " + status));
        String keywordFilter = keyword == null || keyword.isBlank() ? null : keyword.strip();
        return new ActivityFilter(statusFilter, null, null, null, null, actor.user().getId(), keywordFilter, type);
    }

    private static ActivityType parseSlug(String slug) {
        ActivityType type = slug == null ? null : SLUGS.get(slug.strip().toLowerCase(Locale.ROOT));
        if (type == null) {
            throw new ResponseStatusException(NOT_FOUND, "Activity type was not found.");
        }
        return type;
    }

    /** Binds and validates a JSON body against the activity request DTO (same rules as the admin API). */
    private <T> T bind(JsonNode body, Class<T> requestType) {
        if (body == null || body.isNull() || !body.isObject()) {
            throw new ResponseStatusException(BAD_REQUEST, "Activity request body is required.");
        }
        T request;
        try {
            request = objectMapper.treeToValue(body, requestType);
        } catch (JsonProcessingException | IllegalArgumentException exception) {
            throw new ResponseStatusException(BAD_REQUEST, "Activity request body is invalid.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                    .sorted(Comparator.comparing(violation -> violation.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .findFirst()
                    .orElse("Activity request is invalid.");
            throw new ResponseStatusException(BAD_REQUEST, message);
        }
        return request;
    }
}