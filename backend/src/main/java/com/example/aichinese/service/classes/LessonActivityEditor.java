package com.example.aichinese.service.classes;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
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
import com.example.aichinese.entity.enums.EnumLookups;
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
    private final EntityManager entityManager;
    private final FlashcardItemRepository flashcardItemRepository;
    private final MatchingGameItemRepository matchingGameItemRepository;
    private final ContentSafetyService safety;

    static ClassActivityType parseType(String value) {
        return EnumLookups.tryFrom(ClassActivityType.class, value)
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

    record ValidatedActivity(
            String title,
            List<ClassRequests.Card> cards,
            List<ClassRequests.TraceCharacter> characters,
            List<ClassRequests.MatchPair> pairs) {
    }

    boolean isUnchanged(LessonActivity existing, ValidatedActivity validated) {
        if (!Objects.equals(existing.getTitle(), validated.title())) {
            return false;
        }
        return switch (existing) {
            case Flashcard flashcards -> content.cardViews(flashcards.getId()).equals(validated.cards().stream()
                    .map(card -> new LessonViews.KeyPhrase(card.chinese(), card.pinyin(), card.meaning()))
                    .toList());
            case TracingExercise tracing ->
                    TracingCharacters.read(tracing).equals(TracingCharacters.normalize(validated.characters()));
            case MatchingGame game -> content.matchPairs(game.getId()).equals(validated.pairs().stream()
                    .map(pair -> new LessonViews.KeyPhrase(pair.chinese(), pair.pinyin(), pair.meaning()))
                    .toList());
            default -> false;
        };
    }

    ValidatedActivity validate(ClassActivityType type, ClassRequests.Activity request) {
        String safeTitle = safety.requireSafeText(request.title(), 200, "Activity title is required.");
        return switch (type) {
            case FLASHCARDS -> new ValidatedActivity(safeTitle, validateCards(request.cards()), List.of(), List.of());
            case TRACING -> new ValidatedActivity(safeTitle, List.of(), validateCharacters(request.characters()), List.of());
            case MATCHING -> new ValidatedActivity(safeTitle, List.of(), List.of(), validatePairs(request.pairs()));
        };
    }

    void add(Lesson working, User admin, ClassActivityType type, ValidatedActivity validated) {
        LessonActivity activity = ClassContentSupport.newActivity(type);
        activity.setCreatedByUser(admin);
        activity.setContentStatus(ContentStatus.DRAFT);
        activity.setLesson(working);
        activity.setLessonPosition(type.lessonPosition());
        apply(activity, validated, true);
    }

    void update(LessonActivity activity, ValidatedActivity validated) {
        apply(activity, validated, false);
    }

    private void apply(LessonActivity activity, ValidatedActivity validated, boolean isNew) {
        activity.setTitle(validated.title());
        if (activity instanceof TracingExercise tracing) {
            TracingCharacters.write(tracing, validated.characters());
        }
        if (isNew) {
            entityManager.persist(activity);
        }
        switch (activity) {
            case Flashcard flashcards -> applyCards(flashcards, validated.cards(), isNew);
            case TracingExercise ignored -> {
            }
            case MatchingGame game -> applyPairs(game, validated.pairs(), isNew);
            default -> throw new IllegalStateException("Unknown lesson activity " + activity);
        }
    }

    private List<ClassRequests.Card> validateCards(List<ClassRequests.Card> cards) {
        List<ClassRequests.Card> safe = new ArrayList<>(cards.size());
        for (ClassRequests.Card card : cards) {
            String chinese = safety.requireSafeText(card.chinese(), 100, "Chinese is required for every card.");
            String pinyin = safety.requireSafeText(card.pinyin(), 200, "Pinyin is required for every card.");
            String meaning = safety.requireSafeText(card.meaning(), 200, "Meaning is required for every card.");
            safe.add(new ClassRequests.Card(chinese, pinyin, meaning));
        }
        return List.copyOf(safe);
    }

    private List<ClassRequests.TraceCharacter> validateCharacters(List<ClassRequests.TraceCharacter> characters) {
        if (characters.isEmpty()) {
            throw new ResponseStatusException(BAD_REQUEST, "Add at least one character to trace.");
        }
        List<ClassRequests.TraceCharacter> safe = new ArrayList<>(characters.size());
        for (ClassRequests.TraceCharacter character : characters) {
            String cleanChar = safety.requireSafeText(character.character(), 4, "Enter the character to trace.");
            if (!TracingCharacters.isSingleCharacter(cleanChar)) {
                throw new ResponseStatusException(BAD_REQUEST, "Enter one character per row.");
            }
            String pinyin = safety.requireSafeText(character.pinyin(), 40, "Pinyin is required for every character.");
            String meaning = safety.optionalSafeText(character.meaning(), 80);
            safe.add(new ClassRequests.TraceCharacter(cleanChar, pinyin, meaning));
        }
        return List.copyOf(safe);
    }

    /**
     * Replaces the activity's cards (A3.16: Chinese · Pinyin · Meaning). An activity without cards has its learners
     * review the lesson's key phrases.
     */
    private void applyCards(Flashcard flashcards, List<ClassRequests.Card> cards, boolean isNew) {
        if (!isNew) {
            flashcardItemRepository.deleteAll(content.flashcardItems(flashcards.getId()));
            flashcardItemRepository.flush();
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

    private List<ClassRequests.MatchPair> validatePairs(List<ClassRequests.MatchPair> pairs) {
        if (pairs.size() < 2) {
            throw new ResponseStatusException(BAD_REQUEST, "A matching activity needs at least 2 pairs.");
        }
        Set<String> words = new HashSet<>();
        Set<String> meanings = new HashSet<>();
        List<ClassRequests.MatchPair> safe = new ArrayList<>(pairs.size());
        for (ClassRequests.MatchPair pair : pairs) {
            String chinese = safety.requireSafeText(pair.chinese(), 100, "Chinese is required for every pair.");
            String pinyin = safety.requireSafeText(pair.pinyin(), 200, "Pinyin is required for every pair.");
            String meaning = safety.requireSafeText(pair.meaning(), 300, "Meaning is required for every pair.");
            if (!words.add(TextUtils.normalizeAnswerText(chinese))) {
                throw new ResponseStatusException(BAD_REQUEST, "Each Chinese word can only be used once.");
            }
            if (!meanings.add(TextUtils.normalizeAnswerText(meaning))) {
                throw new ResponseStatusException(BAD_REQUEST, "Each meaning can only be used once.");
            }
            safe.add(new ClassRequests.MatchPair(chinese, pinyin, meaning));
        }
        return List.copyOf(safe);
    }

    /** Replaces the game's matching pairs. */
    private void applyPairs(MatchingGame game, List<ClassRequests.MatchPair> pairs, boolean isNew) {
        if (!isNew) {
            matchingGameItemRepository.deleteAll(content.matchingItems(game.getId()));
            matchingGameItemRepository.flush();
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
