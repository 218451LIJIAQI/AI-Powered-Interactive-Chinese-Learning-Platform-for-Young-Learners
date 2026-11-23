package com.example.aichinese.service.learn;

import static com.example.aichinese.util.TextUtils.isBlankOrSpaces;
import static com.example.aichinese.util.TextUtils.isSpace;
import static com.example.aichinese.util.TextUtils.trimToNull;
import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.DragDropItem;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.FlashcardItem;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.TypingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.service.RewardRules;

import jakarta.persistence.EntityManager;

/**
 * The Practice library's six set types on top of their activity tables. A practice set is platform content: an
 * activity created by an admin (or seeded, with no author). The activities of class lessons belong to their lessons
 * and are never practice sets. For each type this class reads a set's items with their answer keys, and writes an
 * admin's request back to the entity and its child rows.
 */
@Component
public class PracticeCatalog {

    static final String ITEM_REQUIRED = "Add at least one item to this set.";
    /** Max distractor meanings included alongside the right meaning in Mistakes review choice questions. */
    private static final int CHOICE_DECOYS = 3;
    private static final int MAX_TRACING_CHARACTERS = 10;
    private static final int MAX_STROKES_PER_CHARACTER = 60;
    private static final int MAX_TOTAL_STROKES = MAX_TRACING_CHARACTERS * MAX_STROKES_PER_CHARACTER;

    private final EntityManager entityManager;
    private final Map<PracticeType, TypeSupport<?>> types;

    public PracticeCatalog(
            EntityManager entityManager,
            FlashcardItemRepository flashcardItemRepository,
            DragDropItemRepository dragDropItemRepository,
            MatchingGameItemRepository matchingItemRepository) {
        this.entityManager = entityManager;
        types = Map.of(
                PracticeType.FLASHCARDS, new Flashcards(flashcardItemRepository),
                PracticeType.LISTENING, new Listening(),
                PracticeType.TRACING, new Tracing(),
                PracticeType.DRAG_DROP, new DragDrop(dragDropItemRepository),
                PracticeType.MATCHING, new Matching(matchingItemRepository),
                PracticeType.TYPING, new Typing());
    }

    /**
     * A set's content with its answer keys. A tracing set has its strokes, all together and, when known, per character
     * ({@code characterStrokes}, else null).
     */
    public record SetContent(List<Item> items, Integer strokeCount, List<Integer> characterStrokes) {
        List<PracticeItemData> editableItems() {
            return items.stream().map(Item::data).toList();
        }
    }

    private static boolean hasWordAndMeaning(PracticeItemData item) {
        return !isBlankOrSpaces(item.hanzi()) && !LearnAnswers.normalize(item.meaning()).isEmpty();
    }

    /** {@code gradable} is null for items that are not graded (flashcards and tracing). */
    public record Item(String key, PracticeItemData data, GradableItem gradable) {
    }

    // Reads

    public SetContent content(PracticeType type, PracticeActivityContent set) {
        return types.get(type).contentOf(set);
    }

    /** Older published sets can still be played if their material supports the activity. */
    public boolean isPlayable(PracticeType type, SetContent content) {
        return !content.items().isEmpty() && types.get(type).playable(content);
    }

    /**
     * A flashcard as the Mistakes review asks it once the learner marked it "Still learning": what the word means,
     * among up to 3 other meanings of its set (in a stable mixed order), with its Chinese read aloud.
     */
    public static GradableItem flashcardQuestion(SetContent content, Item card) {
        String meaning = card.data().meaning();
        List<ChoiceOption> options = meaningChoices(
                meaning, content.items().stream().map(other -> other.data().meaning()).toList(), card.key().hashCode());
        return new GradableItem(AnswerKind.CHOICE, LearnQuestionType.MEANING, null, card.data().hanzi(),
                card.data().pinyin(), meaning, card.data().hanzi(), options, List.of(), meaning, null);
    }

    /** Right meaning plus up to {@link #CHOICE_DECOYS} distinct other meanings, in a stable mixed order. */
    private static List<ChoiceOption> meaningChoices(String answer, List<String> pool, long seed) {
        Set<String> seen = new HashSet<>(List.of(LearnAnswers.normalize(answer)));
        List<String> others = pool.stream()
                .filter(other -> other != null
                        && !LearnAnswers.normalize(other).isEmpty()
                        && seen.add(LearnAnswers.normalize(other)))
                .limit(CHOICE_DECOYS)
                .toList();
        List<String> meanings = new ArrayList<>(others);
        meanings.add(answer);
        return shuffled(meanings, seed).stream()
                .map(text -> new ChoiceOption(text, null))
                .toList();
    }

    // Writes

    public PracticeActivityContent create(PracticeType type, PracticeSetRequest request, User author) {
        PracticeActivityContent set = types.get(type).factory.get();
        set.setCreatedByUser(author);
        set.setContentStatus(ContentStatus.DRAFT);
        update(type, set, request);
        return set;
    }

    void update(PracticeType type, PracticeActivityContent set, PracticeSetRequest request) {
        TypeSupport<?> support = types.get(type);
        support.validate(request);
        set.setTitle(request.title());
        set.setLearningStage(request.stage());
        set.setHskLevel(request.hskLevel());
        set.setXpReward(RewardRules.practiceSetXp(type, request.hskLevel(), request.items().size()));
        set.setUpdatedAt(LocalDateTime.now());
        support.write(set, request, entityManager);
    }

    /** Publishing an existing draft checks the same editable items as saving it. */
    void validateForPublication(PracticeType type, PracticeActivityContent set, SetContent content) {
        if (isBlankOrSpaces(set.getTitle())) {
            throw new ResponseStatusException(BAD_REQUEST, "Title is required.");
        }
        var saved = new PracticeSetRequest(set.getTitle(), set.getLearningStage(), set.getHskLevel(),
                content.editableItems(), content.strokeCount(), content.characterStrokes());
        types.get(type).validateForPublication(set, saved);
    }

    private static String textOf(JsonNode node, String field) {
        return node.hasNonNull(field) ? trimToNull(node.get(field).asText()) : null;
    }

    private static List<ChoiceOption> cleanOptions(List<ChoiceOption> options) {
        return options.stream()
                .map(option -> new ChoiceOption(trimToNull(option.text()), trimToNull(option.pinyin())))
                .toList();
    }

    /**
     * Validates a set's choice questions: each needs 2+ answers with text and one of them ticked, and no two answers
     * that read the same. Answers are graded by their text as {@link LearnAnswers#matches} reads it, so a second
     * "Hello" (or "hello!") would be graded right as well, and the hint and the editor would take the first one for the
     * ticked answer.
     */
    private static void validateChoices(List<PracticeItemData> questions) {
        if (!questions.stream().allMatch(PracticeCatalog::choicesComplete)) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Every question needs at least 2 answers with the right one ticked.");
        }
        if (!questions.stream().allMatch(PracticeCatalog::answersDiffer)) {
            throw new ResponseStatusException(BAD_REQUEST, "The answers of a question must all be different.");
        }
    }

    private static boolean choicesComplete(PracticeItemData item) {
        List<ChoiceOption> options = item.options();
        return options.size() >= 2
                && options.stream().noneMatch(option -> LearnAnswers.normalize(option.text()).isEmpty())
                && item.correctIndex() != null
                && item.correctIndex() >= 0
                && item.correctIndex() < options.size();
    }

    private static boolean answersDiffer(PracticeItemData item) {
        Set<String> seen = new HashSet<>();
        return item.options().stream().allMatch(option -> seen.add(LearnAnswers.normalize(option.text())));
    }

    /** Space-separated words without empty or repeated entries (as {@link LearnAnswers#matches} compares them). */
    private static List<String> distinctTiles(String spaceSeparated) {
        Set<String> seen = new HashSet<>();
        return LearnAnswers.tiles(spaceSeparated).stream()
                .filter(tile -> {
                    String normalized = LearnAnswers.normalize(tile);
                    return !normalized.isEmpty() && seen.add(normalized);
                })
                .toList();
    }

    // Type support

    /** One set type on top of its activity table and child rows. */
    private abstract static class TypeSupport<E extends PracticeActivityContent> {

        private final Class<E> entityClass;
        private final Supplier<E> factory;

        TypeSupport(Class<E> entityClass, Supplier<E> factory) {
            this.entityClass = entityClass;
            this.factory = factory;
        }

        abstract void validate(PracticeSetRequest request);

        abstract boolean playable(SetContent content);

        void validateForPublication(PracticeActivityContent set, PracticeSetRequest saved) {
            validate(saved);
        }

        abstract List<Item> items(E entity);

        /** Type fields of the entity; its child rows are written in {@link #writeChildren}. */
        void applyFields(E entity, PracticeSetRequest request) {
        }

        /** The items as child rows of the saved set, by position ({@link ChildRows}). */
        void writeChildren(E saved, PracticeSetRequest request) {
        }

        /** The set's items; only a tracing set has strokes. */
        SetContent contentOf(PracticeActivityContent set) {
            return new SetContent(items(entityClass.cast(set)), null, null);
        }

        void write(PracticeActivityContent set, PracticeSetRequest request, EntityManager entityManager) {
            E entity = entityClass.cast(set);
            applyFields(entity, request);
            entityManager.persist(entity);
            writeChildren(entity, request);
        }
    }

    private static final class Flashcards extends TypeSupport<Flashcard> {

        private final FlashcardItemRepository itemRepository;

        Flashcards(FlashcardItemRepository itemRepository) {
            super(Flashcard.class, Flashcard::new);
            this.itemRepository = itemRepository;
        }

        @Override
        void validate(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, ITEM_REQUIRED);
            }
            if (!request.items().stream().allMatch(PracticeCatalog::hasWordAndMeaning)) {
                throw new ResponseStatusException(BAD_REQUEST, "Every card needs Chinese and a meaning.");
            }
            long distinctMeanings = request.items().stream()
                    .map(card -> LearnAnswers.normalize(card.meaning()))
                    .distinct()
                    .count();
            if (distinctMeanings < 2) {
                throw new ResponseStatusException(BAD_REQUEST, "Add at least 2 cards with different meanings.");
            }
        }

        @Override
        boolean playable(SetContent content) {
            return content.items().stream().allMatch(item -> hasWordAndMeaning(item.data()));
        }

        @Override
        List<Item> items(Flashcard entity) {
            return itemRepository.findByFlashcardIdOrderBySortOrderAscIdAsc(entity.getId()).stream()
                    .map(card -> new Item(String.valueOf(card.getId()), new PracticeItemData(card.getHanzi(),
                            card.getPinyin(), card.getMeaning(), null, null, card.getExample(),
                            card.getExampleMeaning(), List.of(), null, null, null), null))
                    .toList();
        }

        @Override
        void writeChildren(Flashcard saved, PracticeSetRequest request) {
            ChildRows.writeByPosition(itemRepository.findByFlashcardIdOrderBySortOrderAscIdAsc(saved.getId()),
                    request.items(), FlashcardItem::new, itemRepository, (card, position, data) -> {
                        card.setFlashcard(saved);
                        card.setSortOrder(position);
                        card.setHanzi(trimToNull(data.hanzi()));
                        card.setPinyin(trimToNull(data.pinyin()));
                        card.setMeaning(trimToNull(data.meaning()));
                        card.setExample(trimToNull(data.example()));
                        card.setExampleMeaning(trimToNull(data.exampleMeaning()));
                    });
        }
    }

    /**
     * Listening questions live in the exercise's JSON payload:
     * {@code {"questions":[{"question":"...","audioText":"一杯茶","options":[{"text":"一杯茶","pinyin":"yì bēi chá"}],
     * "answer":"一杯茶","explanation":"..."}]}}. The Chinese is read aloud by speech synthesis.
     */
    private static final class Listening extends TypeSupport<ListeningExercise> {

        Listening() {
            super(ListeningExercise.class, ListeningExercise::new);
        }

        @Override
        void validate(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, ITEM_REQUIRED);
            }
            if (request.items().stream().anyMatch(question -> isBlankOrSpaces(question.audioText()))) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Every listening question needs the Chinese that is read aloud.");
            }
            validateChoices(request.items());
        }

        @Override
        boolean playable(SetContent content) {
            return content.items().stream().allMatch(item ->
                    !isBlankOrSpaces(item.data().audioText()) && item.data().correctIndex() != null);
        }

        @Override
        List<Item> items(ListeningExercise entity) {
            List<Item> items = new ArrayList<>();
            int index = 0;
            for (JsonNode node : LearnAnswers.read(entity.getQuestionPayload()).path("questions")) {
                List<ChoiceOption> options = LearnAnswers.optionsOf(node.path("options"));
                String answer = textOf(node, "answer");
                int found = LearnAnswers.indexOf(options, answer);
                String prompt = textOf(node, "question");
                String audio = textOf(node, "audioText");
                String explanation = textOf(node, "explanation");
                PracticeItemData data = new PracticeItemData(null, null, null, prompt, audio, null, null, options,
                        found < 0 ? null : found, explanation, null);
                GradableItem gradable = new GradableItem(AnswerKind.CHOICE, null, prompt, null, null, null, audio,
                        options, List.of(), answer, explanation);
                items.add(new Item(String.valueOf(index++), data, gradable));
            }
            return List.copyOf(items);
        }

        @Override
        void applyFields(ListeningExercise entity, PracticeSetRequest request) {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            ArrayNode questions = root.putArray("questions");
            for (PracticeItemData data : request.items()) {
                List<ChoiceOption> options = cleanOptions(data.options());
                ObjectNode question = questions.addObject();
                question.put("question", trimToNull(data.prompt()));
                question.put("audioText", trimToNull(data.audioText()));
                question.set("options", LearnAnswers.optionsNode(options));
                question.put("answer", options.get(data.correctIndex()).text());
                if (!isBlankOrSpaces(data.explanation())) {
                    question.put("explanation", trimToNull(data.explanation()));
                }
            }
            entity.setQuestionPayload(root.toString());
        }
    }

    private static final class Tracing extends TypeSupport<TracingExercise> {

        Tracing() {
            super(TracingExercise.class, TracingExercise::new);
        }

        @Override
        void validate(PracticeSetRequest request) {
            if (request.items().isEmpty() || isBlankOrSpaces(request.items().getFirst().hanzi())) {
                throw new ResponseStatusException(BAD_REQUEST, "Add the characters to write.");
            }
            if (request.items().size() != 1) {
                throw new ResponseStatusException(BAD_REQUEST, "Put the characters of a tracing set in one row.");
            }
            int characters = characterCount(request.items().getFirst().hanzi());
            if (characters > MAX_TRACING_CHARACTERS) {
                throw new ResponseStatusException(BAD_REQUEST, "A tracing set has at most 10 characters.");
            }
            if (request.strokeCount() == null || request.strokeCount() <= 0) {
                throw new ResponseStatusException(BAD_REQUEST, "Enter how many strokes the characters have.");
            }
            if (request.strokeCount() > MAX_TOTAL_STROKES) {
                throw new ResponseStatusException(BAD_REQUEST, "Total strokes must be between 1 and 600.");
            }
            if (characters == 1 && request.strokeCount() > MAX_STROKES_PER_CHARACTER) {
                throw new ResponseStatusException(BAD_REQUEST, "Strokes must be between 1 and 60.");
            }
            if (characters > 1
                    && (request.strokeCount() < characters || request.strokeCount() > characters * MAX_STROKES_PER_CHARACTER)) {
                throw new ResponseStatusException(BAD_REQUEST, "Each character needs 1 to 60 strokes.");
            }
            List<Integer> perCharacter = request.characterStrokes();
            if (!perCharacter.isEmpty()) {
                if (perCharacter.stream().anyMatch(strokes -> strokes != null && strokes > MAX_STROKES_PER_CHARACTER)) {
                    throw new ResponseStatusException(BAD_REQUEST, "Strokes must be between 1 and 60.");
                }
                if (perCharacter.size() != characters
                        || perCharacter.stream().anyMatch(strokes -> strokes == null || strokes <= 0)
                        || perCharacter.stream().mapToInt(Integer::intValue).sum() != request.strokeCount()) {
                    throw new ResponseStatusException(BAD_REQUEST,
                            "Give the strokes of each character, adding up to the total.");
                }
            }
        }

        @Override
        boolean playable(SetContent content) {
            return !isBlankOrSpaces(content.items().getFirst().data().hanzi())
                    && content.strokeCount() != null && content.strokeCount() > 0;
        }

        @Override
        List<Item> items(TracingExercise entity) {
            return List.of(new Item("0", new PracticeItemData(entity.getTargetCharacter(), entity.getPinyin(),
                    entity.getMeaning(), null, null, null, null, List.of(), null, null, null), null));
        }

        @Override
        SetContent contentOf(PracticeActivityContent set) {
            TracingExercise entity = (TracingExercise) set;
            return new SetContent(items(entity), entity.getStrokeCount(), characterStrokes(entity));
        }

        @Override
        void validateForPublication(PracticeActivityContent set, PracticeSetRequest saved) {
            validate(saved);
            TracingExercise entity = (TracingExercise) set;
            if (!isBlankOrSpaces(entity.getCharacterStrokes()) && parseCharacterStrokes(entity) == null) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Give the strokes of each character, adding up to the total.");
            }
        }

        /** As stored, or a single character's {@code strokeCount}; null when a word's set does not say or is malformed. */
        private static List<Integer> characterStrokes(TracingExercise entity) {
            List<Integer> parsed = parseCharacterStrokes(entity);
            if (parsed != null) {
                return parsed;
            }
            boolean single = entity.getTargetCharacter() != null && characterCount(entity.getTargetCharacter()) == 1;
            return single && entity.getStrokeCount() != null
                    && entity.getStrokeCount() > 0 && entity.getStrokeCount() <= MAX_STROKES_PER_CHARACTER
                    ? List.of(entity.getStrokeCount()) : null;
        }

        private static List<Integer> parseCharacterStrokes(TracingExercise entity) {
            String stored = trimToNull(entity.getCharacterStrokes());
            if (stored == null || entity.getTargetCharacter() == null
                    || entity.getStrokeCount() == null || entity.getStrokeCount() <= 0
                    || entity.getStrokeCount() > MAX_TOTAL_STROKES) {
                return null;
            }
            int expectedChars = characterCount(entity.getTargetCharacter());
            try {
                List<Integer> parsed = Arrays.stream(stored.split(",", -1))
                        .map(String::strip)
                        .map(Integer::valueOf)
                        .toList();
                if (parsed.size() == expectedChars
                        && parsed.stream().allMatch(strokes -> strokes > 0 && strokes <= MAX_STROKES_PER_CHARACTER)
                        && parsed.stream().mapToInt(Integer::intValue).sum() == entity.getStrokeCount()) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // Corrupted legacy CSV falls back to the single-character rule or null on reads and is blocked on publish.
            }
            return null;
        }

        @Override
        void applyFields(TracingExercise entity, PracticeSetRequest request) {
            PracticeItemData data = request.items().getFirst();
            entity.setTargetCharacter(trimToNull(data.hanzi()));
            entity.setPinyin(trimToNull(data.pinyin()));
            entity.setMeaning(trimToNull(data.meaning()));
            entity.setStrokeCount(request.strokeCount());
            entity.setCharacterStrokes(request.characterStrokes().isEmpty()
                    ? null
                    : String.join(",", request.characterStrokes().stream().map(String::valueOf).toList()));
        }

        /** The characters to write (spaces between them are not ones). */
        private static int characterCount(String text) {
            return (int) text.codePoints().filter(codePoint -> !isSpace(codePoint)).count();
        }
    }

    private static final class DragDrop extends TypeSupport<DragDropActivity> {

        private final DragDropItemRepository itemRepository;

        DragDrop(DragDropItemRepository itemRepository) {
            super(DragDropActivity.class, DragDropActivity::new);
            this.itemRepository = itemRepository;
        }

        @Override
        void validate(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, ITEM_REQUIRED);
            }
            boolean complete = request.items().stream()
                    .allMatch(sentence -> hasSentenceAndMeaning(sentence) && LearnAnswers.tiles(sentence.hanzi()).size() >= 2);
            if (!complete) {
                throw new ResponseStatusException(BAD_REQUEST, "Every sentence needs at least 2 tiles and a meaning.");
            }
        }

        @Override
        boolean playable(SetContent content) {
            return content.items().stream().allMatch(item -> hasSentenceAndMeaning(item.data()));
        }

        private static boolean hasSentenceAndMeaning(PracticeItemData sentence) {
            return !LearnAnswers.normalize(sentence.hanzi()).isEmpty() && !isBlankOrSpaces(sentence.meaning());
        }

        @Override
        List<Item> items(DragDropActivity entity) {
            return itemRepository.findInDisplayOrder(entity.getId()).stream()
                    .map(sentence -> {
                        List<String> tiles = new ArrayList<>(LearnAnswers.tiles(sentence.getDragText()));
                        tiles.addAll(LearnAnswers.tiles(sentence.getDistractors()));
                        PracticeItemData data = new PracticeItemData(sentence.getDragText(), sentence.getPinyin(),
                                sentence.getTargetZone(), null, null, null, null, List.of(), null, null,
                                sentence.getDistractors());
                        GradableItem gradable = new GradableItem(AnswerKind.BUILD, LearnQuestionType.TRANSLATE_BUILD,
                                null, LearnAnswers.joinTiles(sentence.getDragText()), sentence.getPinyin(),
                                sentence.getTargetZone(), null, List.of(), shuffled(tiles, sentence.getId()),
                                sentence.getDragText(), null);
                        return new Item(String.valueOf(sentence.getId()), data, gradable);
                    })
                    .toList();
        }

        @Override
        void writeChildren(DragDropActivity saved, PracticeSetRequest request) {
            ChildRows.writeByPosition(itemRepository.findInDisplayOrder(saved.getId()),
                    request.items(), DragDropItem::new, itemRepository, (sentence, position, data) -> {
                        sentence.setDragDropActivity(saved);
                        sentence.setSortOrder(position);
                        sentence.setDragText(String.join(" ", LearnAnswers.tiles(data.hanzi())));
                        sentence.setTargetZone(trimToNull(data.meaning()));
                        sentence.setPinyin(trimToNull(data.pinyin()));
                        List<String> distractors = LearnAnswers.tiles(data.distractors());
                        sentence.setDistractors(distractors.isEmpty() ? null : String.join(" ", distractors));
                    });
        }
    }

    private static final class Matching extends TypeSupport<MatchingGame> {

        private final MatchingGameItemRepository itemRepository;

        Matching(MatchingGameItemRepository itemRepository) {
            super(MatchingGame.class, MatchingGame::new);
            this.itemRepository = itemRepository;
        }

        @Override
        void validate(PracticeSetRequest request) {
            List<PracticeItemData> pairs = request.items();
            if (pairs.size() < 2) {
                throw new ResponseStatusException(BAD_REQUEST, "Add at least 2 pairs.");
            }
            if (!allPairsComplete(pairs)) {
                throw new ResponseStatusException(BAD_REQUEST, "Every pair needs Chinese and a meaning.");
            }
            if (!distinctNormalized(pairs, PracticeItemData::hanzi)) {
                throw new ResponseStatusException(BAD_REQUEST, "Each pair needs different Chinese.");
            }
            if (!distinctNormalized(pairs, PracticeItemData::meaning)) {
                throw new ResponseStatusException(BAD_REQUEST, "Each pair needs a different meaning.");
            }
        }

        @Override
        boolean playable(SetContent content) {
            List<PracticeItemData> pairs = content.editableItems();
            return pairs.size() >= 2
                    && allPairsComplete(pairs)
                    && distinctNormalized(pairs, PracticeItemData::hanzi)
                    && distinctNormalized(pairs, PracticeItemData::meaning);
        }

        private static boolean allPairsComplete(List<PracticeItemData> pairs) {
            return pairs.stream().noneMatch(pair -> LearnAnswers.normalize(pair.hanzi()).isEmpty()
                    || LearnAnswers.normalize(pair.meaning()).isEmpty());
        }

        private static boolean distinctNormalized(
                List<PracticeItemData> pairs, Function<PracticeItemData, String> field) {
            Set<String> seen = new HashSet<>();
            return pairs.stream().allMatch(pair -> seen.add(LearnAnswers.normalize(field.apply(pair))));
        }

        @Override
        List<Item> items(MatchingGame entity) {
            List<MatchingGameItem> pairs = itemRepository.findInDisplayOrder(entity.getId());
            List<String> allMeanings = pairs.stream().map(MatchingGameItem::getRightText).toList();
            return pairs.stream()
                    .map(pair -> {
                        PracticeItemData data = new PracticeItemData(pair.getLeftText(), pair.getPinyin(),
                                pair.getRightText(), null, null, null, null, List.of(), null, null, null);
                        List<ChoiceOption> choices = meaningChoices(pair.getRightText(), allMeanings, pair.getId());
                        GradableItem gradable = new GradableItem(AnswerKind.CHOICE, LearnQuestionType.MEANING, null,
                                pair.getLeftText(), pair.getPinyin(), pair.getRightText(), null, choices, List.of(),
                                pair.getRightText(), null);
                        return new Item(String.valueOf(pair.getId()), data, gradable);
                    })
                    .toList();
        }

        @Override
        void writeChildren(MatchingGame saved, PracticeSetRequest request) {
            ChildRows.writeByPosition(itemRepository.findInDisplayOrder(saved.getId()),
                    request.items(), MatchingGameItem::new, itemRepository, (pair, position, data) -> {
                        pair.setMatchingGame(saved);
                        pair.setSortOrder(position);
                        pair.setLeftText(trimToNull(data.hanzi()));
                        pair.setRightText(trimToNull(data.meaning()));
                        pair.setPinyin(trimToNull(data.pinyin()));
                    });
        }
    }

    /**
     * Pinyin typing. A word is the meaning to read, the characters and pinyin of the answer, and decoys: other words
     * spelt with the same letters, which are offered next to the answer once the pinyin is typed. Graded as a choice
     * among those candidates, so a waiting mistake can be reviewed as "Which one is “thank you”?".
     */
    private static final class Typing extends TypeSupport<TypingExercise> {

        Typing() {
            super(TypingExercise.class, TypingExercise::new);
        }

        @Override
        void validate(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, ITEM_REQUIRED);
            }
            if (request.items().stream().anyMatch(Typing::missingWord)) {
                throw new ResponseStatusException(BAD_REQUEST, "Every word needs Chinese, pinyin and a meaning.");
            }
            if (request.items().stream().anyMatch(word -> Pinyin.typed(word.pinyin()).isEmpty())) {
                throw new ResponseStatusException(BAD_REQUEST, "Write the pinyin in letters, for example xièxie.");
            }
            // A repeated look-alike is one word offered twice, not a second one to choose from.
            boolean decoysOk = request.items().stream().allMatch(word -> {
                List<String> decoys = distinctTiles(word.distractors());
                return decoys.size() >= 2 && decoys.stream().noneMatch(decoy -> LearnAnswers.matches(decoy, word.hanzi()));
            });
            if (!decoysOk) {
                throw new ResponseStatusException(BAD_REQUEST,
                        "Every word needs at least 2 look-alike words that are not the answer.");
            }
        }

        @Override
        boolean playable(SetContent content) {
            return content.items().stream().allMatch(item ->
                    !missingWord(item.data()) && !Pinyin.typed(item.data().pinyin()).isEmpty());
        }

        private static boolean missingWord(PracticeItemData word) {
            return LearnAnswers.normalize(word.hanzi()).isEmpty()
                    || isBlankOrSpaces(word.pinyin()) || isBlankOrSpaces(word.meaning());
        }

        @Override
        List<Item> items(TypingExercise entity) {
            List<Item> items = new ArrayList<>();
            int index = 0;
            for (JsonNode node : LearnAnswers.read(entity.getWordPayload()).path("words")) {
                String meaning = textOf(node, "meaning");
                String hanzi = textOf(node, "hanzi");
                String pinyin = textOf(node, "pinyin");
                String decoys = textOf(node, "decoys");
                List<String> words = new ArrayList<>();
                if (hanzi != null) {
                    words.add(hanzi);
                }
                distinctTiles(decoys).stream().filter(decoy -> !LearnAnswers.matches(decoy, hanzi)).forEach(words::add);
                // Candidates in a stable mixed order: the answer is not always the first word offered. They carry no
                // pinyin: the decoys are spelt alike but have none, so the answer's own would mark it in the Mistakes
                // review (the verdict takes the item's pinyin).
                long seed = entity.getId() * 31 + index;
                List<ChoiceOption> candidates = shuffled(words, seed).stream()
                        .map(word -> new ChoiceOption(word, null))
                        .toList();
                PracticeItemData data = new PracticeItemData(hanzi, pinyin, meaning, null, null, null, null,
                        List.of(), null, null, decoys);
                GradableItem gradable = new GradableItem(AnswerKind.CHOICE, LearnQuestionType.HANZI, null, hanzi,
                        pinyin, meaning, null, candidates, List.of(), hanzi, null);
                items.add(new Item(String.valueOf(index++), data, gradable));
            }
            return List.copyOf(items);
        }

        @Override
        void applyFields(TypingExercise entity, PracticeSetRequest request) {
            ObjectNode root = JsonNodeFactory.instance.objectNode();
            ArrayNode words = root.putArray("words");
            for (PracticeItemData data : request.items()) {
                ObjectNode word = words.addObject();
                word.put("meaning", trimToNull(data.meaning()));
                word.put("hanzi", trimToNull(data.hanzi()));
                word.put("pinyin", trimToNull(data.pinyin()));
                word.put("decoys", String.join(" ", distinctTiles(data.distractors())));
            }
            entity.setWordPayload(root.toString());
        }
    }

    /** The tiles in a stable mixed order (the same each time, so a reload does not reshuffle a sentence). */
    static List<String> shuffled(List<String> tiles, long seed) {
        List<String> mixed = new ArrayList<>(tiles);
        Collections.shuffle(mixed, new Random(seed));
        if (mixed.size() > 1 && mixed.equals(tiles)) {
            Collections.rotate(mixed, 1);
        }
        return List.copyOf(mixed);
    }
}
