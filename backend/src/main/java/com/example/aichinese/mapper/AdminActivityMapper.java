package com.example.aichinese.mapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

import org.springframework.stereotype.Component;

import com.example.aichinese.dto.admin.activity.ActivityType;
import com.example.aichinese.dto.admin.activity.AdminActivitySummaryResponse;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminDragDropItemRequest;
import com.example.aichinese.dto.admin.activity.AdminDragDropItemResponse;
import com.example.aichinese.dto.admin.activity.AdminFlashcardRequest;
import com.example.aichinese.dto.admin.activity.AdminFlashcardResponse;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseRequest;
import com.example.aichinese.dto.admin.activity.AdminListeningExerciseResponse;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameItemRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameItemResponse;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameRequest;
import com.example.aichinese.dto.admin.activity.AdminMatchingGameResponse;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryActivityResponse;
import com.example.aichinese.dto.admin.activity.AdminStoryQuestionRequest;
import com.example.aichinese.dto.admin.activity.AdminStoryQuestionResponse;
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
import com.example.aichinese.entity.enums.ContentStatus;

/**
 * Translates between the platform-managed activity entities and the
 * {@code AdminXxx} DTOs used by the admin REST endpoints. Centralising the
 * conversion (including tag list ↔ comma-separated string and child-item
 * sorting) keeps {@link com.example.aichinese.service.AdminActivityService}
 * focused on business logic and audit.
 */
@Component
public class AdminActivityMapper {

    private static final Comparator<MatchingGameItem> MATCHING_GAME_ITEM_ORDER =
            Comparator.comparing(MatchingGameItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(MatchingGameItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    private static final Comparator<DragDropItem> DRAG_DROP_ITEM_ORDER =
            Comparator.comparing(DragDropItem::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(DragDropItem::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    private static final Comparator<StoryQuestion> STORY_QUESTION_ORDER =
            Comparator.comparing(StoryQuestion::getSortOrder, Comparator.nullsLast(Integer::compareTo))
                    .thenComparing(StoryQuestion::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()));

    // ---------- Tag helpers ----------

    public String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .reduce((a, b) -> a + "," + b)
                .orElse(null);
        return joined == null || joined.isBlank() ? null : joined;
    }

    public List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }

    private String creatorName(User user) {
        return user == null ? null : user.getDisplayName();
    }

    private Long creatorId(User user) {
        return user == null ? null : user.getId();
    }

    // ---------- Flashcard ----------

    public Flashcard toEntity(AdminFlashcardRequest request, User admin) {
        Flashcard entity = new Flashcard();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminFlashcardRequest request, Flashcard entity) {
        entity.setTitle(request.title());
        entity.setFrontText(request.frontText());
        entity.setBackText(request.backText());
        entity.setPinyin(request.pinyin());
        entity.setEnglishMeaning(request.englishMeaning());
        entity.setMalayMeaning(request.malayMeaning());
        entity.setImageUrl(request.imageUrl());
        entity.setAudioUrl(request.audioUrl());
        entity.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        entity.setExtraPayload(request.extraPayload());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public AdminFlashcardResponse toResponse(Flashcard entity) {
        return new AdminFlashcardResponse(
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
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public AdminActivitySummaryResponse toSummary(Flashcard entity) {
        String summaryTitle = entity.getTitle() == null || entity.getTitle().isBlank()
                ? entity.getFrontText()
                : entity.getTitle();
        return summaryOf(
                entity.getId(),
                ActivityType.FLASHCARD,
                summaryTitle,
                entity);
    }

    // ---------- MatchingGame ----------

    public MatchingGame toEntity(AdminMatchingGameRequest request, User admin) {
        MatchingGame entity = new MatchingGame();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminMatchingGameRequest request, MatchingGame entity) {
        entity.setTitle(request.title());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public MatchingGameItem toEntity(AdminMatchingGameItemRequest request, MatchingGame parent) {
        MatchingGameItem item = new MatchingGameItem();
        item.setMatchingGame(parent);
        item.setLeftText(request.leftText());
        item.setRightText(request.rightText());
        item.setLeftImageUrl(request.leftImageUrl());
        item.setRightImageUrl(request.rightImageUrl());
        item.setPinyin(request.pinyin());
        item.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        return item;
    }

    public AdminMatchingGameItemResponse toResponse(MatchingGameItem item) {
        return new AdminMatchingGameItemResponse(
                item.getId(),
                item.getLeftText(),
                item.getRightText(),
                item.getLeftImageUrl(),
                item.getRightImageUrl(),
                item.getPinyin(),
                item.getSortOrder());
    }

    public AdminMatchingGameResponse toResponse(MatchingGame entity, List<MatchingGameItem> items) {
        List<AdminMatchingGameItemResponse> itemResponses = items.stream()
                .sorted(MATCHING_GAME_ITEM_ORDER)
                .map(this::toResponse)
                .toList();
        return new AdminMatchingGameResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                itemResponses);
    }

    public AdminActivitySummaryResponse toSummary(MatchingGame entity) {
        return summaryOf(entity.getId(), ActivityType.MATCHING_GAME, entity.getTitle(), entity);
    }

    // ---------- ListeningExercise ----------

    public ListeningExercise toEntity(AdminListeningExerciseRequest request, User admin) {
        ListeningExercise entity = new ListeningExercise();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminListeningExerciseRequest request, ListeningExercise entity) {
        entity.setTitle(request.title());
        entity.setAudioUrl(request.audioUrl());
        entity.setQuestionPayload(request.questionPayload());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public AdminListeningExerciseResponse toResponse(ListeningExercise entity) {
        return new AdminListeningExerciseResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getAudioUrl(),
                entity.getQuestionPayload(),
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public AdminActivitySummaryResponse toSummary(ListeningExercise entity) {
        return summaryOf(entity.getId(), ActivityType.LISTENING_EXERCISE, entity.getTitle(), entity);
    }

    // ---------- TracingExercise ----------

    public TracingExercise toEntity(AdminTracingExerciseRequest request, User admin) {
        TracingExercise entity = new TracingExercise();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminTracingExerciseRequest request, TracingExercise entity) {
        entity.setTitle(request.title());
        entity.setWorksheetUrl(request.worksheetUrl());
        entity.setTargetCharacter(request.targetCharacter());
        entity.setPinyin(request.pinyin());
        entity.setMeaning(request.meaning());
        entity.setInstructions(request.instructions());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public AdminTracingExerciseResponse toResponse(TracingExercise entity) {
        return new AdminTracingExerciseResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getWorksheetUrl(),
                entity.getTargetCharacter(),
                entity.getPinyin(),
                entity.getMeaning(),
                entity.getInstructions(),
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    public AdminActivitySummaryResponse toSummary(TracingExercise entity) {
        return summaryOf(entity.getId(), ActivityType.TRACING_EXERCISE, entity.getTitle(), entity);
    }

    // ---------- DragDropActivity ----------

    public DragDropActivity toEntity(AdminDragDropActivityRequest request, User admin) {
        DragDropActivity entity = new DragDropActivity();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminDragDropActivityRequest request, DragDropActivity entity) {
        entity.setTitle(request.title());
        entity.setInstructions(request.instructions());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public DragDropItem toEntity(AdminDragDropItemRequest request, DragDropActivity parent) {
        DragDropItem item = new DragDropItem();
        item.setDragDropActivity(parent);
        item.setDragText(request.dragText());
        item.setTargetZone(request.targetZone());
        item.setPinyin(request.pinyin());
        item.setImageUrl(request.imageUrl());
        item.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        return item;
    }

    public AdminDragDropItemResponse toResponse(DragDropItem item) {
        return new AdminDragDropItemResponse(
                item.getId(),
                item.getDragText(),
                item.getTargetZone(),
                item.getPinyin(),
                item.getImageUrl(),
                item.getSortOrder());
    }

    public AdminDragDropActivityResponse toResponse(DragDropActivity entity, List<DragDropItem> items) {
        List<AdminDragDropItemResponse> itemResponses = items.stream()
                .sorted(DRAG_DROP_ITEM_ORDER)
                .map(this::toResponse)
                .toList();
        return new AdminDragDropActivityResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getInstructions(),
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                itemResponses);
    }

    public AdminActivitySummaryResponse toSummary(DragDropActivity entity) {
        return summaryOf(entity.getId(), ActivityType.DRAG_DROP_ACTIVITY, entity.getTitle(), entity);
    }

    // ---------- StoryActivity ----------

    public StoryActivity toEntity(AdminStoryActivityRequest request, User admin) {
        StoryActivity entity = new StoryActivity();
        entity.setCreatedByUser(admin);
        entity.setContentStatus(ContentStatus.DRAFT);
        applyTo(request, entity);
        return entity;
    }

    public void applyTo(AdminStoryActivityRequest request, StoryActivity entity) {
        entity.setTitle(request.title());
        entity.setInstructions(request.instructions());
        entity.setStoryText(request.storyText());
        entity.setPinyin(request.pinyin());
        entity.setTranslation(request.translation());
        entity.setImageUrl(request.imageUrl());
        entity.setHskLevel(request.hskLevel());
        entity.setAgeBand(request.ageBand());
        entity.setDifficulty(request.difficulty());
        entity.setTopic(request.topic());
        entity.setTags(joinTags(request.tags()));
        if (request.xpReward() != null) {
            entity.setXpReward(request.xpReward());
        }
        entity.setEstimatedSeconds(request.estimatedSeconds());
    }

    public StoryQuestion toEntity(AdminStoryQuestionRequest request, StoryActivity parent) {
        StoryQuestion question = new StoryQuestion();
        question.setStoryActivity(parent);
        question.setQuestionText(request.questionText());
        question.setOptionsPayload(request.optionsPayload());
        question.setExplanation(request.explanation());
        question.setSortOrder(request.sortOrder() != null ? request.sortOrder() : 0);
        return question;
    }

    public AdminStoryQuestionResponse toResponse(StoryQuestion question) {
        return new AdminStoryQuestionResponse(
                question.getId(),
                question.getQuestionText(),
                question.getOptionsPayload(),
                question.getExplanation(),
                question.getSortOrder());
    }

    public AdminStoryActivityResponse toResponse(StoryActivity entity, List<StoryQuestion> questions) {
        List<AdminStoryQuestionResponse> questionResponses = questions.stream()
                .sorted(STORY_QUESTION_ORDER)
                .map(this::toResponse)
                .toList();
        return new AdminStoryActivityResponse(
                entity.getId(),
                entity.getTitle(),
                entity.getInstructions(),
                entity.getStoryText(),
                entity.getPinyin(),
                entity.getTranslation(),
                entity.getImageUrl(),
                entity.getContentStatus(),
                entity.getHskLevel(),
                entity.getAgeBand(),
                entity.getDifficulty(),
                entity.getTopic(),
                splitTags(entity.getTags()),
                entity.getXpReward(),
                entity.getEstimatedSeconds(),
                creatorId(entity.getCreatedByUser()),
                creatorName(entity.getCreatedByUser()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                questionResponses);
    }

    public AdminActivitySummaryResponse toSummary(StoryActivity entity) {
        return summaryOf(entity.getId(), ActivityType.STORY_ACTIVITY, entity.getTitle(), entity);
    }

    // ---------- Common summary helpers ----------

    private AdminActivitySummaryResponse summaryOf(Long id, ActivityType type, String title, Object entity) {
        // Pulls the shared metadata via reflection-free duck typing — all six
        // activity entities expose the same getters thanks to Phase 3.
        return Stream.of(entity)
                .map(e -> new AdminActivitySummaryResponse(
                        id,
                        type,
                        title,
                        getStatus(e),
                        getHskLevel(e),
                        getAgeBand(e),
                        getDifficulty(e),
                        getTopic(e),
                        splitTags(getTags(e)),
                        getXpReward(e),
                        getEstimatedSeconds(e),
                        creatorId(getCreatedByUser(e)),
                        creatorName(getCreatedByUser(e)),
                        getCreatedAt(e),
                        getUpdatedAt(e)))
                .findFirst()
                .orElseThrow();
    }

    private ContentStatus getStatus(Object entity) {
        if (entity instanceof Flashcard e) return e.getContentStatus();
        if (entity instanceof MatchingGame e) return e.getContentStatus();
        if (entity instanceof ListeningExercise e) return e.getContentStatus();
        if (entity instanceof TracingExercise e) return e.getContentStatus();
        if (entity instanceof DragDropActivity e) return e.getContentStatus();
        if (entity instanceof StoryActivity e) return e.getContentStatus();
        return null;
    }

    private Integer getHskLevel(Object entity) {
        if (entity instanceof Flashcard e) return e.getHskLevel();
        if (entity instanceof MatchingGame e) return e.getHskLevel();
        if (entity instanceof ListeningExercise e) return e.getHskLevel();
        if (entity instanceof TracingExercise e) return e.getHskLevel();
        if (entity instanceof DragDropActivity e) return e.getHskLevel();
        if (entity instanceof StoryActivity e) return e.getHskLevel();
        return null;
    }

    private com.example.aichinese.entity.enums.AgeBand getAgeBand(Object entity) {
        if (entity instanceof Flashcard e) return e.getAgeBand();
        if (entity instanceof MatchingGame e) return e.getAgeBand();
        if (entity instanceof ListeningExercise e) return e.getAgeBand();
        if (entity instanceof TracingExercise e) return e.getAgeBand();
        if (entity instanceof DragDropActivity e) return e.getAgeBand();
        if (entity instanceof StoryActivity e) return e.getAgeBand();
        return null;
    }

    private com.example.aichinese.entity.enums.ActivityDifficulty getDifficulty(Object entity) {
        if (entity instanceof Flashcard e) return e.getDifficulty();
        if (entity instanceof MatchingGame e) return e.getDifficulty();
        if (entity instanceof ListeningExercise e) return e.getDifficulty();
        if (entity instanceof TracingExercise e) return e.getDifficulty();
        if (entity instanceof DragDropActivity e) return e.getDifficulty();
        if (entity instanceof StoryActivity e) return e.getDifficulty();
        return null;
    }

    private String getTopic(Object entity) {
        if (entity instanceof Flashcard e) return e.getTopic();
        if (entity instanceof MatchingGame e) return e.getTopic();
        if (entity instanceof ListeningExercise e) return e.getTopic();
        if (entity instanceof TracingExercise e) return e.getTopic();
        if (entity instanceof DragDropActivity e) return e.getTopic();
        if (entity instanceof StoryActivity e) return e.getTopic();
        return null;
    }

    private String getTags(Object entity) {
        if (entity instanceof Flashcard e) return e.getTags();
        if (entity instanceof MatchingGame e) return e.getTags();
        if (entity instanceof ListeningExercise e) return e.getTags();
        if (entity instanceof TracingExercise e) return e.getTags();
        if (entity instanceof DragDropActivity e) return e.getTags();
        if (entity instanceof StoryActivity e) return e.getTags();
        return null;
    }

    private Integer getXpReward(Object entity) {
        if (entity instanceof Flashcard e) return e.getXpReward();
        if (entity instanceof MatchingGame e) return e.getXpReward();
        if (entity instanceof ListeningExercise e) return e.getXpReward();
        if (entity instanceof TracingExercise e) return e.getXpReward();
        if (entity instanceof DragDropActivity e) return e.getXpReward();
        if (entity instanceof StoryActivity e) return e.getXpReward();
        return null;
    }

    private Integer getEstimatedSeconds(Object entity) {
        if (entity instanceof Flashcard e) return e.getEstimatedSeconds();
        if (entity instanceof MatchingGame e) return e.getEstimatedSeconds();
        if (entity instanceof ListeningExercise e) return e.getEstimatedSeconds();
        if (entity instanceof TracingExercise e) return e.getEstimatedSeconds();
        if (entity instanceof DragDropActivity e) return e.getEstimatedSeconds();
        if (entity instanceof StoryActivity e) return e.getEstimatedSeconds();
        return null;
    }

    private User getCreatedByUser(Object entity) {
        if (entity instanceof Flashcard e) return e.getCreatedByUser();
        if (entity instanceof MatchingGame e) return e.getCreatedByUser();
        if (entity instanceof ListeningExercise e) return e.getCreatedByUser();
        if (entity instanceof TracingExercise e) return e.getCreatedByUser();
        if (entity instanceof DragDropActivity e) return e.getCreatedByUser();
        if (entity instanceof StoryActivity e) return e.getCreatedByUser();
        return null;
    }

    private java.time.LocalDateTime getCreatedAt(Object entity) {
        if (entity instanceof Flashcard e) return e.getCreatedAt();
        if (entity instanceof MatchingGame e) return e.getCreatedAt();
        if (entity instanceof ListeningExercise e) return e.getCreatedAt();
        if (entity instanceof TracingExercise e) return e.getCreatedAt();
        if (entity instanceof DragDropActivity e) return e.getCreatedAt();
        if (entity instanceof StoryActivity e) return e.getCreatedAt();
        return null;
    }

    private java.time.LocalDateTime getUpdatedAt(Object entity) {
        if (entity instanceof Flashcard e) return e.getUpdatedAt();
        if (entity instanceof MatchingGame e) return e.getUpdatedAt();
        if (entity instanceof ListeningExercise e) return e.getUpdatedAt();
        if (entity instanceof TracingExercise e) return e.getUpdatedAt();
        if (entity instanceof DragDropActivity e) return e.getUpdatedAt();
        if (entity instanceof StoryActivity e) return e.getUpdatedAt();
        return null;
    }
}
