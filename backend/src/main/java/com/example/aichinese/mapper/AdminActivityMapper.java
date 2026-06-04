package com.example.aichinese.mapper;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

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
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.AgeBand;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.util.TextUtils;

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

    private String joinTags(List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return null;
        }
        String joined = tags.stream()
                .filter(Objects::nonNull)
                .map(AdminActivityMapper::normalizeDelimitedToken)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.joining(","));
        return joined.isEmpty() ? null : joined;
    }

    private List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return List.of();
        }
        return Arrays.stream(tags.split(","))
                .map(TextUtils::trimToNull)
                .filter(Objects::nonNull)
                .toList();
    }

    private static String normalizeDelimitedToken(String value) {
        String normalized = TextUtils.collapseWhitespaceToNull(value);
        if (normalized == null) {
            return null;
        }
        return TextUtils.trimToNull(normalized.replace(",", ""));
    }

    private static String creatorName(User user) {
        return user == null ? null : user.getDisplayName();
    }

    private static Long creatorId(User user) {
        return user == null ? null : user.getId();
    }

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
        entity.setSortOrder(Objects.requireNonNullElse(request.sortOrder(), 0));
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
        String title = entity.getTitle();
        String summaryTitle = title == null || title.isBlank() ? entity.getFrontText() : title;
        return buildSummary(
                entity.getId(), ActivityType.FLASHCARD, summaryTitle,
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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
        item.setSortOrder(Objects.requireNonNullElse(request.sortOrder(), 0));
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
        List<MatchingGameItem> safeItems = items == null ? List.of() : items;
        List<AdminMatchingGameItemResponse> itemResponses = safeItems.stream()
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
        return buildSummary(
                entity.getId(), ActivityType.MATCHING_GAME, entity.getTitle(),
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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
        return buildSummary(
                entity.getId(), ActivityType.LISTENING_EXERCISE, entity.getTitle(),
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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
        return buildSummary(
                entity.getId(), ActivityType.TRACING_EXERCISE, entity.getTitle(),
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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
        item.setSortOrder(Objects.requireNonNullElse(request.sortOrder(), 0));
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
        List<DragDropItem> safeItems = items == null ? List.of() : items;
        List<AdminDragDropItemResponse> itemResponses = safeItems.stream()
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
        return buildSummary(
                entity.getId(), ActivityType.DRAG_DROP_ACTIVITY, entity.getTitle(),
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

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
        question.setSortOrder(Objects.requireNonNullElse(request.sortOrder(), 0));
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
        List<StoryQuestion> safeQuestions = questions == null ? List.of() : questions;
        List<AdminStoryQuestionResponse> questionResponses = safeQuestions.stream()
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
        return buildSummary(
                entity.getId(), ActivityType.STORY_ACTIVITY, entity.getTitle(),
                entity.getContentStatus(), entity.getHskLevel(), entity.getAgeBand(),
                entity.getDifficulty(), entity.getTopic(), entity.getTags(),
                entity.getXpReward(), entity.getEstimatedSeconds(), entity.getCreatedByUser(),
                entity.getCreatedAt(), entity.getUpdatedAt());
    }

    private AdminActivitySummaryResponse buildSummary(
            Long id,
            ActivityType type,
            String title,
            ContentStatus status,
            Integer hskLevel,
            AgeBand ageBand,
            ActivityDifficulty difficulty,
            String topic,
            String tagsCsv,
            Integer xpReward,
            Integer estimatedSeconds,
            User createdBy,
            LocalDateTime createdAt,
            LocalDateTime updatedAt) {
        return new AdminActivitySummaryResponse(
                id,
                type,
                title,
                status,
                hskLevel,
                ageBand,
                difficulty,
                topic,
                splitTags(tagsCsv),
                xpReward,
                estimatedSeconds,
                creatorId(createdBy),
                creatorName(createdBy),
                createdAt,
                updatedAt);
    }
}
