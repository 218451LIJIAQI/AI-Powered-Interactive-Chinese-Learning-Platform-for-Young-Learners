package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Random;
import java.util.Set;
import java.util.function.Supplier;

import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;

import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetRequest;
import com.example.aichinese.dto.learn.StoryLine;
import com.example.aichinese.entity.DragDropActivity;
import com.example.aichinese.entity.DragDropItem;
import com.example.aichinese.entity.Flashcard;
import com.example.aichinese.entity.FlashcardItem;
import com.example.aichinese.entity.ListeningExercise;
import com.example.aichinese.entity.MatchingGame;
import com.example.aichinese.entity.MatchingGameItem;
import com.example.aichinese.entity.PracticeQuiz;
import com.example.aichinese.entity.PracticeQuizQuestion;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StoryActivity;
import com.example.aichinese.entity.StoryQuestion;
import com.example.aichinese.entity.TracingExercise;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.DragDropActivityRepository;
import com.example.aichinese.repository.DragDropItemRepository;
import com.example.aichinese.repository.FlashcardItemRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.ListeningExerciseRepository;
import com.example.aichinese.repository.MatchingGameItemRepository;
import com.example.aichinese.repository.MatchingGameRepository;
import com.example.aichinese.repository.PracticeActivityRepository;
import com.example.aichinese.repository.PracticeQuizQuestionRepository;
import com.example.aichinese.repository.PracticeQuizRepository;
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.StoryQuestionRepository;
import com.example.aichinese.repository.TracingExerciseRepository;
import com.example.aichinese.util.TextUtils;

/**
 * The Practice library's seven set types on top of their activity tables. A practice set is platform content: an
 * activity created by an admin (or seeded, with no author). Activities that teachers created belong to their classes
 * and are never practice sets. For each type this class reads a set's items with their answer keys, and writes an
 * admin's request back to the entity and its child rows.
 */
@Component
public class PracticeCatalog {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String ITEM_REQUIRED = "Add at least one item to this set.";

    private final Map<PracticeType, TypeSupport<?>> types = new EnumMap<>(PracticeType.class);

    public PracticeCatalog(
            FlashcardRepository flashcardRepository,
            FlashcardItemRepository flashcardItemRepository,
            PracticeQuizRepository quizRepository,
            PracticeQuizQuestionRepository quizQuestionRepository,
            ListeningExerciseRepository listeningRepository,
            TracingExerciseRepository tracingRepository,
            DragDropActivityRepository dragDropRepository,
            DragDropItemRepository dragDropItemRepository,
            MatchingGameRepository matchingRepository,
            MatchingGameItemRepository matchingItemRepository,
            StoryActivityRepository storyRepository,
            StoryQuestionRepository storyQuestionRepository) {
        types.put(PracticeType.FLASHCARDS, new Flashcards(flashcardRepository, flashcardItemRepository));
        types.put(PracticeType.QUIZZES, new Quizzes(quizRepository, quizQuestionRepository));
        types.put(PracticeType.LISTENING, new Listening(listeningRepository));
        types.put(PracticeType.TRACING, new Tracing(tracingRepository));
        types.put(PracticeType.DRAG_DROP, new DragDrop(dragDropRepository, dragDropItemRepository));
        types.put(PracticeType.MATCHING, new Matching(matchingRepository, matchingItemRepository));
        types.put(PracticeType.STORY, new Story(storyRepository, storyQuestionRepository));
    }

    /** A set's content with its answer keys. {@code gradable} is null for items that are not graded. */
    public record SetContent(List<Item> items, List<StoryLine> storyLines, Integer strokeCount) {
    }

    public record Item(String key, PracticeItemData data, GradableItem gradable) {
    }

    // ===== Reads =====

    /** Practice sets of one type (all statuses when {@code status} is null), oldest first. */
    public List<PracticeActivityContent> sets(PracticeType type, ContentStatus status) {
        List<? extends PracticeActivityContent> found = support(type).repository().findAll(platformSets(status));
        return found.stream()
                .<PracticeActivityContent>map(entity -> entity)
                .sorted(Comparator.comparing(PracticeActivityContent::getId))
                .toList();
    }

    public Optional<PracticeActivityContent> find(PracticeType type, Long id) {
        return support(type).repository().findById(id)
                .filter(PracticeCatalog::isPlatformSet)
                .map(entity -> (PracticeActivityContent) entity);
    }

    public PracticeActivityContent require(PracticeType type, Long id) {
        return find(type, id).orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Practice set not found."));
    }

    public SetContent content(PracticeType type, PracticeActivityContent set) {
        return support(type).contentOf(set);
    }

    public int itemCount(PracticeType type, PracticeActivityContent set) {
        return content(type, set).items().size();
    }

    // ===== Writes =====

    /** Checks the request against the type's rules; a set is always complete enough to publish. */
    private void validate(PracticeType type, PracticeSetRequest request) {
        String problem = support(type).problemWith(request);
        if (problem != null) {
            throw new ResponseStatusException(BAD_REQUEST, problem);
        }
    }

    public PracticeActivityContent create(PracticeType type, PracticeSetRequest request, User author) {
        validate(type, request);
        return support(type).createFrom(request, author);
    }

    public PracticeActivityContent update(PracticeType type, PracticeActivityContent set, PracticeSetRequest request) {
        validate(type, request);
        return support(type).updateFrom(set, request);
    }

    public PracticeActivityContent save(PracticeType type, PracticeActivityContent set) {
        return support(type).saveRaw(set);
    }

    // ===== Platform sets =====

    /** A practice set: no author (seeded platform content) or an admin author. */
    private static boolean isPlatformSet(PracticeActivityContent activity) {
        User author = activity.getCreatedByUser();
        return author == null || author.getRoles().stream().anyMatch(role -> role.getName() == RoleName.ADMIN);
    }

    private static <T> Specification<T> platformSets(ContentStatus status) {
        return (root, query, cb) -> {
            Subquery<Long> admins = query.subquery(Long.class);
            Root<User> user = admins.from(User.class);
            Join<User, Role> role = user.join("roles");
            admins.select(user.get("id")).where(cb.equal(role.get("name"), RoleName.ADMIN));
            var platform = cb.or(cb.isNull(root.get("createdByUser")), root.get("createdByUser").get("id").in(admins));
            return status == null ? platform : cb.and(platform, cb.equal(root.get("contentStatus"), status));
        };
    }

    private TypeSupport<?> support(PracticeType type) {
        return types.get(type);
    }

    // ===== Shared helpers =====

    private static String text(String value) {
        return TextUtils.trimToNull(value);
    }

    private static boolean blank(String value) {
        return text(value) == null;
    }

    private static List<ChoiceOption> cleanOptions(List<ChoiceOption> options) {
        return options.stream()
                .map(option -> new ChoiceOption(text(option.text()), text(option.pinyin())))
                .toList();
    }

    /** A choice question needs 2+ answers with text and one of them ticked. */
    private static boolean choicesComplete(PracticeItemData item) {
        List<ChoiceOption> options = item.options();
        return options.size() >= 2
                && options.stream().noneMatch(option -> blank(option.text()))
                && item.correctIndex() != null
                && item.correctIndex() < options.size();
    }

    private static String answerOf(List<ChoiceOption> options, Integer correctIndex) {
        return correctIndex != null && correctIndex >= 0 && correctIndex < options.size()
                ? options.get(correctIndex).text()
                : null;
    }

    private static Integer indexOf(List<ChoiceOption> options, String answer) {
        for (int index = 0; index < options.size(); index++) {
            if (LearnAnswers.matches(options.get(index).text(), answer)) {
                return index;
            }
        }
        return null;
    }

    private static List<String> lines(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return List.of(value.split("\\R", -1));
    }

    private static String line(List<String> lines, int index) {
        return index < lines.size() ? text(lines.get(index)) : null;
    }

    // ===== Type support =====

    /** One set type on top of its activity table and child rows. */
    private abstract static class TypeSupport<E extends PracticeActivityContent> {

        private final PracticeActivityRepository<E> repository;
        private final Supplier<E> factory;
        /** The XP a set of this type gives when the admin sets none. */
        private final int defaultXp;

        TypeSupport(PracticeActivityRepository<E> repository, Supplier<E> factory, int defaultXp) {
            this.repository = repository;
            this.factory = factory;
            this.defaultXp = defaultXp;
        }

        PracticeActivityRepository<E> repository() {
            return repository;
        }

        abstract String problemWith(PracticeSetRequest request);

        abstract List<Item> items(E entity);

        List<StoryLine> storyLines(E entity) {
            return List.of();
        }

        Integer strokeCount(E entity) {
            return null;
        }

        /** Type fields of the entity; children are replaced in {@link #replaceChildren}. */
        void applyFields(E entity, PracticeSetRequest request) {
        }

        void replaceChildren(E saved, PracticeSetRequest request) {
        }

        SetContent contentOf(PracticeActivityContent set) {
            E entity = own(set);
            return new SetContent(items(entity), storyLines(entity), strokeCount(entity));
        }

        E createFrom(PracticeSetRequest request, User author) {
            E entity = factory.get();
            entity.setCreatedByUser(author);
            entity.setContentStatus(ContentStatus.DRAFT);
            return write(entity, request);
        }

        E updateFrom(PracticeActivityContent set, PracticeSetRequest request) {
            return write(own(set), request);
        }

        E saveRaw(PracticeActivityContent set) {
            return repository.save(own(set));
        }

        /** A set of this type: the catalogue only hands a support the sets it looked up by that type. */
        @SuppressWarnings("unchecked")
        private E own(PracticeActivityContent set) {
            return (E) set;
        }

        private E write(E entity, PracticeSetRequest request) {
            entity.setTitle(request.title());
            entity.setLearningStage(request.stage());
            entity.setHskLevel(request.hskLevel());
            entity.setXpReward(request.xpReward() == null ? defaultXp : request.xpReward());
            applyFields(entity, request);
            E saved = repository.save(entity);
            replaceChildren(saved, request);
            return saved;
        }
    }

    private static final class Flashcards extends TypeSupport<Flashcard> {

        private final FlashcardItemRepository itemRepository;

        Flashcards(FlashcardRepository repository, FlashcardItemRepository itemRepository) {
            super(repository, Flashcard::new, 10);
            this.itemRepository = itemRepository;
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                return ITEM_REQUIRED;
            }
            if (request.items().stream().anyMatch(card -> blank(card.hanzi()) || blank(card.meaning()))) {
                return "Every card needs Chinese and a meaning.";
            }
            return null;
        }

        @Override
        List<Item> items(Flashcard entity) {
            List<FlashcardItem> cards = itemRepository.findByFlashcardIdOrderBySortOrderAscIdAsc(entity.getId());
            if (cards.isEmpty()) {
                // A single-card flashcard from before sets had cards: its own front and back.
                return List.of(new Item(String.valueOf(entity.getId()), new PracticeItemData(null, entity.getFrontText(),
                        entity.getPinyin(), firstNonBlank(entity.getEnglishMeaning(), entity.getBackText()), null,
                        null, null, null, List.of(), null, null, null), null));
            }
            return cards.stream()
                    .map(card -> new Item(String.valueOf(card.getId()), new PracticeItemData(card.getId(), card.getHanzi(),
                            card.getPinyin(), card.getMeaning(), null, null, card.getExample(),
                            card.getExampleMeaning(), List.of(), null, null, null), null))
                    .toList();
        }

        @Override
        void applyFields(Flashcard entity, PracticeSetRequest request) {
            PracticeItemData cover = request.items().get(0);
            entity.setFrontText(text(cover.hanzi()));
            entity.setBackText(text(cover.meaning()));
            entity.setPinyin(text(cover.pinyin()));
            entity.setEnglishMeaning(text(cover.meaning()));
        }

        @Override
        void replaceChildren(Flashcard saved, PracticeSetRequest request) {
            itemRepository.deleteByFlashcardId(saved.getId());
            List<FlashcardItem> cards = new ArrayList<>();
            for (int index = 0; index < request.items().size(); index++) {
                PracticeItemData data = request.items().get(index);
                FlashcardItem card = new FlashcardItem();
                card.setFlashcard(saved);
                card.setSortOrder(index);
                card.setHanzi(text(data.hanzi()));
                card.setPinyin(text(data.pinyin()));
                card.setMeaning(text(data.meaning()));
                card.setExample(text(data.example()));
                card.setExampleMeaning(text(data.exampleMeaning()));
                cards.add(card);
            }
            itemRepository.saveAll(cards);
        }

        private static String firstNonBlank(String first, String second) {
            return blank(first) ? second : first;
        }
    }

    private static final class Quizzes extends TypeSupport<PracticeQuiz> {

        private final PracticeQuizQuestionRepository questionRepository;

        Quizzes(PracticeQuizRepository repository, PracticeQuizQuestionRepository questionRepository) {
            super(repository, PracticeQuiz::new, 15);
            this.questionRepository = questionRepository;
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                return ITEM_REQUIRED;
            }
            if (request.items().stream().anyMatch(question -> blank(question.prompt()))) {
                return "Every question needs the question text.";
            }
            if (!request.items().stream().allMatch(PracticeCatalog::choicesComplete)) {
                return "Every question needs at least 2 answers with the right one ticked.";
            }
            return null;
        }

        @Override
        List<Item> items(PracticeQuiz entity) {
            return questionRepository.findByPracticeQuizIdOrderBySortOrderAscIdAsc(entity.getId()).stream()
                    .map(question -> {
                        List<ChoiceOption> options = LearnAnswers.readOptions(question.getOptions());
                        PracticeItemData data = new PracticeItemData(question.getId(), question.getHanzi(), question.getPinyin(), null,
                                question.getPrompt(), null, null, null, options, question.getCorrectOptionIndex(),
                                question.getExplanation(), null);
                        GradableItem gradable = new GradableItem(AnswerKind.CHOICE, null, question.getPrompt(),
                                question.getHanzi(), question.getPinyin(), null, null, options, List.of(),
                                answerOf(options, question.getCorrectOptionIndex()), question.getExplanation());
                        return new Item(String.valueOf(question.getId()), data, gradable);
                    })
                    .toList();
        }

        @Override
        void replaceChildren(PracticeQuiz saved, PracticeSetRequest request) {
            questionRepository.deleteByPracticeQuizId(saved.getId());
            List<PracticeQuizQuestion> questions = new ArrayList<>();
            for (int index = 0; index < request.items().size(); index++) {
                PracticeItemData data = request.items().get(index);
                PracticeQuizQuestion question = new PracticeQuizQuestion();
                question.setPracticeQuiz(saved);
                question.setSortOrder(index);
                question.setPrompt(text(data.prompt()));
                question.setHanzi(text(data.hanzi()));
                question.setPinyin(text(data.pinyin()));
                question.setOptions(LearnAnswers.writeOptions(cleanOptions(data.options())));
                question.setCorrectOptionIndex(data.correctIndex());
                question.setExplanation(text(data.explanation()));
                questions.add(question);
            }
            questionRepository.saveAll(questions);
        }
    }

    /**
     * Listening questions live in the exercise's JSON payload:
     * {@code {"questions":[{"question":"...","audioText":"一杯茶","options":[{"text":"一杯茶","pinyin":"yì bēi chá"}],
     * "answer":"一杯茶","explanation":"..."}]}}. The Chinese is read aloud by speech synthesis.
     */
    private static final class Listening extends TypeSupport<ListeningExercise> {

        Listening(ListeningExerciseRepository repository) {
            super(repository, ListeningExercise::new, 15);
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                return ITEM_REQUIRED;
            }
            if (request.items().stream().anyMatch(question -> blank(question.audioText()))) {
                return "Every listening question needs the Chinese that is read aloud.";
            }
            if (!request.items().stream().allMatch(PracticeCatalog::choicesComplete)) {
                return "Every question needs at least 2 answers with the right one ticked.";
            }
            return null;
        }

        @Override
        List<Item> items(ListeningExercise entity) {
            JsonNode root = readJson(entity.getQuestionPayload());
            JsonNode questions = root == null ? null : root.isArray() ? root : root.get("questions");
            if (questions == null || !questions.isArray()) {
                return List.of();
            }
            List<Item> items = new ArrayList<>();
            int index = 0;
            for (JsonNode node : questions) {
                List<ChoiceOption> options = LearnAnswers.readOptions(node.path("options").toString());
                String answer = node.hasNonNull("answer") ? node.get("answer").asText() : null;
                Integer correctIndex = indexOf(options, answer);
                String prompt = textOf(node, "question");
                String audio = textOf(node, "audioText");
                String explanation = textOf(node, "explanation");
                PracticeItemData data = new PracticeItemData(null, null, null, null, prompt, audio, null, null, options, correctIndex,
                        explanation, null);
                GradableItem gradable = new GradableItem(AnswerKind.CHOICE, null, prompt, null, null, null, audio,
                        options, List.of(), answer, explanation);
                items.add(new Item(String.valueOf(index++), data, gradable));
            }
            return List.copyOf(items);
        }

        @Override
        void applyFields(ListeningExercise entity, PracticeSetRequest request) {
            ObjectNode root = MAPPER.createObjectNode();
            ArrayNode questions = root.putArray("questions");
            for (PracticeItemData data : request.items()) {
                List<ChoiceOption> options = cleanOptions(data.options());
                ObjectNode question = questions.addObject();
                question.put("question", text(data.prompt()));
                question.put("audioText", text(data.audioText()));
                question.set("options", readJson(LearnAnswers.writeOptions(options)));
                question.put("answer", answerOf(options, data.correctIndex()));
                if (!blank(data.explanation())) {
                    question.put("explanation", text(data.explanation()));
                }
            }
            entity.setQuestionPayload(root.toString());
            entity.setAudioUrl(null);
        }

        private static String textOf(JsonNode node, String field) {
            return node.hasNonNull(field) ? text(node.get(field).asText()) : null;
        }

        private static JsonNode readJson(String json) {
            if (json == null || json.isBlank()) {
                return null;
            }
            try {
                return MAPPER.readTree(json);
            } catch (JsonProcessingException exception) {
                return null;
            }
        }
    }

    private static final class Tracing extends TypeSupport<TracingExercise> {

        Tracing(TracingExerciseRepository repository) {
            super(repository, TracingExercise::new, 10);
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().isEmpty() || blank(request.items().get(0).hanzi())) {
                return "Add the characters to write.";
            }
            if (request.strokeCount() == null) {
                return "Enter how many strokes the characters have.";
            }
            return null;
        }

        @Override
        List<Item> items(TracingExercise entity) {
            return List.of(new Item("0", new PracticeItemData(null, entity.getTargetCharacter(), entity.getPinyin(),
                    entity.getMeaning(), null, null, null, null, List.of(), null, null, null), null));
        }

        @Override
        Integer strokeCount(TracingExercise entity) {
            return entity.getStrokeCount();
        }

        @Override
        void applyFields(TracingExercise entity, PracticeSetRequest request) {
            PracticeItemData data = request.items().get(0);
            entity.setTargetCharacter(text(data.hanzi()));
            entity.setPinyin(text(data.pinyin()));
            entity.setMeaning(text(data.meaning()));
            entity.setStrokeCount(request.strokeCount());
        }
    }

    private static final class DragDrop extends TypeSupport<DragDropActivity> {

        private final DragDropItemRepository itemRepository;

        DragDrop(DragDropActivityRepository repository, DragDropItemRepository itemRepository) {
            super(repository, DragDropActivity::new, 15);
            this.itemRepository = itemRepository;
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().isEmpty()) {
                return ITEM_REQUIRED;
            }
            boolean complete = request.items().stream()
                    .allMatch(sentence -> LearnAnswers.tiles(sentence.hanzi()).size() >= 2 && !blank(sentence.meaning()));
            return complete ? null : "Every sentence needs at least 2 tiles and a meaning.";
        }

        @Override
        List<Item> items(DragDropActivity entity) {
            return itemRepository.findByDragDropActivityIdOrderBySortOrderAscCreatedAtAsc(entity.getId()).stream()
                    .map(sentence -> {
                        List<String> tiles = new ArrayList<>(LearnAnswers.tiles(sentence.getDragText()));
                        tiles.addAll(LearnAnswers.tiles(sentence.getDistractors()));
                        PracticeItemData data = new PracticeItemData(sentence.getId(), sentence.getDragText(), sentence.getPinyin(),
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
        void replaceChildren(DragDropActivity saved, PracticeSetRequest request) {
            itemRepository.deleteByDragDropActivityId(saved.getId());
            List<DragDropItem> sentences = new ArrayList<>();
            for (int index = 0; index < request.items().size(); index++) {
                PracticeItemData data = request.items().get(index);
                DragDropItem sentence = new DragDropItem();
                sentence.setDragDropActivity(saved);
                sentence.setSortOrder(index);
                sentence.setDragText(String.join(" ", LearnAnswers.tiles(data.hanzi())));
                sentence.setTargetZone(text(data.meaning()));
                sentence.setPinyin(text(data.pinyin()));
                List<String> distractors = LearnAnswers.tiles(data.distractors());
                sentence.setDistractors(distractors.isEmpty() ? null : String.join(" ", distractors));
                sentences.add(sentence);
            }
            itemRepository.saveAll(sentences);
        }
    }

    private static final class Matching extends TypeSupport<MatchingGame> {

        private final MatchingGameItemRepository itemRepository;

        Matching(MatchingGameRepository repository, MatchingGameItemRepository itemRepository) {
            super(repository, MatchingGame::new, 15);
            this.itemRepository = itemRepository;
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.items().size() < 2) {
                return "Add at least 2 pairs.";
            }
            if (request.items().stream().anyMatch(pair -> blank(pair.hanzi()) || blank(pair.meaning()))) {
                return "Every pair needs Chinese and a meaning.";
            }
            Set<String> meanings = new HashSet<>();
            boolean distinct = request.items().stream().allMatch(pair -> meanings.add(LearnAnswers.normalize(pair.meaning())));
            return distinct ? null : "Each pair needs a different meaning.";
        }

        @Override
        List<Item> items(MatchingGame entity) {
            List<MatchingGameItem> pairs = itemRepository.findByMatchingGameIdOrderBySortOrderAscCreatedAtAsc(entity.getId());
            List<ChoiceOption> meanings = pairs.stream().map(pair -> new ChoiceOption(pair.getRightText(), null)).toList();
            return pairs.stream()
                    .map(pair -> {
                        PracticeItemData data = new PracticeItemData(pair.getId(), pair.getLeftText(), pair.getPinyin(),
                                pair.getRightText(), null, null, null, null, List.of(), null, null, null);
                        GradableItem gradable = new GradableItem(AnswerKind.CHOICE, LearnQuestionType.MEANING, null,
                                pair.getLeftText(), pair.getPinyin(), pair.getRightText(), null, meanings, List.of(),
                                pair.getRightText(), null);
                        return new Item(String.valueOf(pair.getId()), data, gradable);
                    })
                    .toList();
        }

        @Override
        void replaceChildren(MatchingGame saved, PracticeSetRequest request) {
            itemRepository.deleteByMatchingGameId(saved.getId());
            List<MatchingGameItem> pairs = new ArrayList<>();
            for (int index = 0; index < request.items().size(); index++) {
                PracticeItemData data = request.items().get(index);
                MatchingGameItem pair = new MatchingGameItem();
                pair.setMatchingGame(saved);
                pair.setSortOrder(index);
                pair.setLeftText(text(data.hanzi()));
                pair.setRightText(text(data.meaning()));
                pair.setPinyin(text(data.pinyin()));
                pairs.add(pair);
            }
            itemRepository.saveAll(pairs);
        }
    }

    /** Story lines are kept one per line in the story text, its pinyin and its translation. */
    private static final class Story extends TypeSupport<StoryActivity> {

        private final StoryQuestionRepository questionRepository;

        Story(StoryActivityRepository repository, StoryQuestionRepository questionRepository) {
            super(repository, StoryActivity::new, 20);
            this.questionRepository = questionRepository;
        }

        @Override
        String problemWith(PracticeSetRequest request) {
            if (request.storyLines().isEmpty()) {
                return "Add the story text.";
            }
            if (request.items().isEmpty()) {
                return "Add at least one question about the story.";
            }
            if (request.items().stream().anyMatch(question -> blank(question.prompt()))) {
                return "Every question needs the question text.";
            }
            if (!request.items().stream().allMatch(PracticeCatalog::choicesComplete)) {
                return "Every question needs at least 2 answers with the right one ticked.";
            }
            return null;
        }

        @Override
        List<Item> items(StoryActivity entity) {
            return questionRepository.findByStoryActivityIdOrderBySortOrderAscCreatedAtAsc(entity.getId()).stream()
                    .map(question -> {
                        List<ChoiceOption> options = LearnAnswers.readOptions(question.getOptionsPayload());
                        Integer correct = question.getCorrectOptionIndex();
                        PracticeItemData data = new PracticeItemData(question.getId(), null, null, null, question.getQuestionText(),
                                null, null, null, options, correct, question.getExplanation(), null);
                        GradableItem gradable = new GradableItem(AnswerKind.CHOICE, null, question.getQuestionText(),
                                null, null, null, null, options, List.of(), answerOf(options, correct),
                                question.getExplanation());
                        return new Item(String.valueOf(question.getId()), data, gradable);
                    })
                    .toList();
        }

        @Override
        List<StoryLine> storyLines(StoryActivity entity) {
            List<String> hanzi = lines(entity.getStoryText());
            List<String> pinyin = lines(entity.getPinyin());
            List<String> translation = lines(entity.getTranslation());
            List<StoryLine> result = new ArrayList<>();
            for (int index = 0; index < hanzi.size(); index++) {
                String line = text(hanzi.get(index));
                if (line != null) {
                    result.add(new StoryLine(line, line(pinyin, index), line(translation, index)));
                }
            }
            return List.copyOf(result);
        }

        @Override
        void applyFields(StoryActivity entity, PracticeSetRequest request) {
            List<StoryLine> lines = request.storyLines();
            entity.setStoryText(String.join("\n", lines.stream().map(line -> Objects.toString(text(line.hanzi()), "")).toList()));
            entity.setPinyin(String.join("\n", lines.stream().map(line -> Objects.toString(text(line.pinyin()), "")).toList()));
            entity.setTranslation(String.join("\n",
                    lines.stream().map(line -> Objects.toString(text(line.translation()), "")).toList()));
        }

        @Override
        void replaceChildren(StoryActivity saved, PracticeSetRequest request) {
            questionRepository.deleteByStoryActivityId(saved.getId());
            List<StoryQuestion> questions = new ArrayList<>();
            for (int index = 0; index < request.items().size(); index++) {
                PracticeItemData data = request.items().get(index);
                StoryQuestion question = new StoryQuestion();
                question.setStoryActivity(saved);
                question.setSortOrder(index);
                question.setQuestionText(text(data.prompt()));
                question.setOptionsPayload(LearnAnswers.writeOptions(cleanOptions(data.options())));
                question.setCorrectOptionIndex(data.correctIndex());
                question.setExplanation(text(data.explanation()));
                questions.add(question);
            }
            questionRepository.saveAll(questions);
        }
    }

    /** The tiles in a stable mixed order (the same each time, so a reload does not reshuffle a sentence). */
    static List<String> shuffled(List<String> tiles, Long seed) {
        List<String> mixed = new ArrayList<>(tiles);
        Collections.shuffle(mixed, new Random(seed == null ? 0 : seed));
        if (mixed.size() > 1 && mixed.equals(tiles)) {
            Collections.rotate(mixed, 1);
        }
        return List.copyOf(mixed);
    }
}
