package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.ClassRequests;
import com.example.aichinese.dto.classes.LessonViews;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.FlashcardItem;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.LessonActivity;
import com.example.aichinese.entity.enums.ClassActivityType;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.service.ContentSafetyService;
import com.example.aichinese.util.TextUtils;

import jakarta.persistence.EntityManager;

import lombok.RequiredArgsConstructor;

/**
 * Authoring mutations for a lesson's three practice activity slots (Flashcards, Tracing, Matching). Callers hold the
 * enclosing class lock and supply the lesson's editable working copy.
 */
@Component
@RequiredArgsConstructor
class LessonActivityEditor {

    private final ClassContentSupport content;
    private final LessonRevisionService revisions;
    private final EntityManager entityManager;
    private final FlashcardItemRepository flashcardItemRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ContentSafetyService safety;

    static ClassActivityType parseType(String value) {
        return ClassActivityType.tryFrom(value)
                .orElseThrow(() -> new ResponseStatusException(BAD_REQUEST, "Activity type must be FLASHCARDS, TRACING or MATCHING."));
    }

    void requireSlotFree(Lesson lesson, ClassActivityType type) {
        if (!content.activities(lesson.getId(), type).isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "This lesson already has an activity of this type.");
        }
    }

    /** An activity of the working copy, or the copy of a published one the editor addressed before. */
    LessonActivity requireActivity(Lesson working, ClassActivityType type, Long activityId) {
        return content.activities(working.getId(), type).stream()
                .filter(activity -> activityId.equals(activity.getId()) || activityId.equals(activity.getRevisionSourceId()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Activity was not found."));
    }

    boolean isUnchanged(LessonActivity existing, String safeTitle, ClassRequests.Activity request) {
        if (!Objects.equals(existing.getTitle(), safeTitle)) {
            return false;
        }
        return switch (existing) {
            case Flashcard flashcards -> content.cardViews(flashcards.getId()).equals(request.cards().stream()
                    .map(card -> new LessonViews.KeyPhrase(card.chinese(), card.pinyin(), card.meaning()))
                    .toList());
            case TracingExercise tracing -> {
                TracingExercise probe = new TracingExercise();
                TracingCharacters.write(probe, request.characters());
                yield TracingCharacters.read(tracing).equals(TracingCharacters.read(probe));
            }
            case MatchingGame game -> content.matchPairs(game.getId()).equals(request.pairs().stream()
                    .map(pair -> new LessonViews.KeyPhrase(pair.chinese(), pair.pinyin(), pair.meaning()))
                    .toList());
            default -> false;
        };
    }

    String validate(ClassActivityType type, ClassRequests.Activity request) {
        String safeTitle = safety.requireSafeText(request.title(), 200, "Activity title is required.");
        switch (type) {
            case FLASHCARDS -> request.cards().forEach(card -> safety.screenText(card.chinese(), card.pinyin(), card.meaning()));
            case TRACING -> validateCharacters(request.characters());
            case MATCHING -> validatePairs(request.pairs());
        }
        return safeTitle;
    }

    void add(Lesson working, User admin, ClassActivityType type, String safeTitle, ClassRequests.Activity request) {
        LessonActivity activity = ClassContentSupport.newActivity(type);
        activity.setCreatedByUser(admin);
        activity.setContentStatus(ContentStatus.DRAFT);
        activity.setLesson(working);
        activity.setLessonPosition(type.lessonPosition());
        apply(activity, safeTitle, request, true);
    }

    void update(LessonActivity activity, String safeTitle, ClassRequests.Activity request) {
        apply(activity, safeTitle, request, false);
    }

    private void apply(LessonActivity activity, String safeTitle, ClassRequests.Activity request, boolean isNew) {
        activity.setTitle(safeTitle);
        if (activity instanceof TracingExercise tracing) {
            TracingCharacters.write(tracing, request.characters());
        }
        if (isNew) {
            entityManager.persist(activity);
        }
        switch (activity) {
            case Flashcard flashcards -> applyCards(flashcards, request.cards(), isNew);
            case TracingExercise ignored -> {
            }
            case MatchingGame game -> applyPairs(game, request.pairs(), isNew);
            default -> throw new IllegalStateException("Unknown lesson activity " + activity);
        }
    }

    private void validateCharacters(List<ClassRequests.TraceCharacter> characters) {
        if (characters.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add at least one character to trace.");
        }
        for (ClassRequests.TraceCharacter character : characters) {
            if (!TracingCharacters.isSingleCharacter(character.character())) {
                throw new ResponseStatusException(BAD_REQUEST, "Enter one character per row.");
            }
            safety.screenText(character.character(), character.pinyin(), character.meaning());
        }
    }

    /**
     * Replaces the activity's cards (A3.16: Chinese · Pinyin · Meaning). An activity without cards has its learners
     * review the lesson's key phrases.
     */
    private void applyCards(Flashcard flashcards, List<ClassRequests.Card> cards, boolean isNew) {
        if (!isNew) {
            revisions.deleteFlashcardItems(flashcards.getId());
        }
        int order = 1;
        for (ClassRequests.Card card : cards) {
            FlashcardItem item = new FlashcardItem();
            item.setFlashcard(flashcards);
            item.setHanzi(card.chinese());
            item.setPinyin(card.pinyin());
            item.setMeaning(card.meaning());
            item.setSortOrder(order++);
            flashcardItemRepository.save(item);
        }
    }

    private void validatePairs(List<ClassRequests.MatchPair> pairs) {
        if (pairs.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "A matching activity needs at least 2 pairs.");
        }
        Set<String> meanings = new HashSet<>();
        for (ClassRequests.MatchPair pair : pairs) {
            safety.screenText(pair.chinese(), pair.pinyin(), pair.meaning());
            if (!meanings.add(TextUtils.normalizeAnswerText(pair.meaning()))) {
                throw new ResponseStatusException(BAD_REQUEST, "Each meaning can only be used once.");
            }
        }
    }

    /** Replaces the game's matching pairs. */
    private void applyPairs(MatchingGame game, List<ClassRequests.MatchPair> pairs, boolean isNew) {
        if (!isNew) {
            revisions.deleteMatchingItems(game.getId());
        }
        int order = 1;
        for (ClassRequests.MatchPair pair : pairs) {
            MatchingGameItem item = new MatchingGameItem();
            item.setMatchingGame(game);
            item.setLeftText(pair.chinese());
            item.setPinyin(pair.pinyin());
            item.setRightText(pair.meaning());
            item.setSortOrder(order++);
            matchingGameItemRepository.save(item);
        }
    }
}
