package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import com.example.aichinese.config.seed.LearnPathContent.LessonSpec;
import com.example.aichinese.config.seed.LearnPathContent.Sentence;
import com.example.aichinese.config.seed.LearnPathContent.SoundDrill;
import com.example.aichinese.config.seed.LearnPathContent.UnitSpec;
import com.example.aichinese.config.seed.LearnPathContent.Word;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.service.learn.LearnAnswers;

/**
 * Builds the questions of a Learn stop from its words and sentences: meaning, pinyin, character and listening choices
 * and sentence building from word tiles plus two extra tiles. A pronunciation lesson asks only for the correct pinyin
 * and what you hear. Distractors come only from what was taught up to that stop: the unit so far, then the earlier
 * units. Everything is deterministic, so seeding twice gives the same path.
 */
final class LearnQuestionGenerator {

    private static final int DISTRACTORS = 2;
    private static final int EXTRA_TILES = 2;
    private static final int WORD_ONLY_QUESTIONS = 6;
    private static final int SOUND_QUESTIONS = 6;
    private static final int SOUND_BONUS_QUESTIONS = 5;
    private static final int REVIEW_QUESTIONS = 8;

    private LearnQuestionGenerator() {
    }

    /** One generated question, ready to be stored. */
    record Question(
            LearnQuestionType type,
            String hanzi,
            String pinyin,
            String meaning,
            String audioText,
            String choices,
            String answer,
            String explanation) {
    }

    static List<Question> questionsFor(UnitSpec unit, LessonSpec lesson, List<UnitSpec> path) {
        if (lesson.kind() == LearnLessonKind.CHECKPOINT) {
            return checkpoint(path, unit.level());
        }
        Pool unitPool = Pool.taughtUpTo(unit, lesson);
        Pool earlier = Pool.earlierUnits(path, unit);
        return switch (lesson.kind()) {
            case LESSON -> lesson.isPronunciation() ? pronunciation(lesson, unitPool, earlier) : lesson(lesson, unitPool, earlier);
            case BONUS -> lesson.isPronunciation() ? soundBonus(lesson, unitPool, earlier) : bonus(lesson, unitPool, earlier);
            case REVIEW -> review(unit, lesson, unitPool, earlier);
            case CHECKPOINT -> throw new AssertionError("Handled above");
        };
    }

    /** The correct pinyin and what you hear, nothing else: "Select the correct pinyin", then "Click what you hear". */
    private static List<Question> pronunciation(LessonSpec lesson, Pool unit, Pool earlier) {
        Generator g = new Generator(unit, earlier, 0);
        List<Word> words = lesson.words();
        List<Question> questions = new ArrayList<>();
        questions.add(g.pinyin(words.get(0)));
        questions.add(g.drill(Objects.requireNonNull(
                lesson.drill(), () -> "Pronunciation lesson requires a sound drill: " + lesson.title())));
        for (int index = 1; questions.size() < SOUND_QUESTIONS; index++) {
            int position = index % words.size();
            int pass = index / words.size();
            Word word = words.get(position);
            questions.add((position + pass) % 2 == 1 ? g.listenSound(word) : g.pinyin(word));
        }
        return questions;
    }

    private static List<Question> lesson(LessonSpec lesson, Pool unit, Pool earlier) {
        List<Word> words = lesson.words();
        List<Sentence> sentences = lesson.sentences();
        Generator g = new Generator(unit, earlier, lesson.title().length());
        if (sentences.isEmpty()) {
            List<Word> picks = spread(words, WORD_ONLY_QUESTIONS);
            return List.of(
                    g.meaning(picks.get(0)),
                    g.listenChoose(picks.get(1)),
                    g.pinyin(picks.get(2)),
                    g.hanzi(picks.get(3)),
                    g.meaning(picks.get(4)),
                    g.listenChoose(picks.get(5)));
        }
        List<Word> picks = spread(words, 4);
        return List.of(
                g.meaning(picks.get(0)),
                g.listenChoose(picks.get(1)),
                g.pinyin(picks.get(2)),
                g.hanzi(picks.get(3)),
                g.sentenceMeaning(sentences.get(0)),
                g.listenBuild(sentences.get(sentences.size() > 1 ? 1 : 0)),
                g.translateBuild(sentences.get(sentences.size() > 2 ? 2 : 0)));
    }

    private static List<Question> soundBonus(LessonSpec lesson, Pool unit, Pool earlier) {
        Generator g = new Generator(unit, earlier, 3);
        List<Question> questions = new ArrayList<>();
        for (int index = 0; index < SOUND_BONUS_QUESTIONS; index++) {
            int position = index % lesson.words().size();
            int pass = index / lesson.words().size();
            Word word = lesson.words().get(position);
            questions.add((position + pass) % 2 == 0 ? g.pinyin(word) : g.listenSound(word));
        }
        return questions;
    }

    private static List<Question> bonus(LessonSpec lesson, Pool unit, Pool earlier) {
        List<Word> words = lesson.words();
        Generator g = new Generator(unit, earlier, 3);
        List<Question> questions = new ArrayList<>();
        questions.add(g.meaning(words.get(0)));
        questions.add(g.listenChoose(words.get(1)));
        questions.add(g.hanzi(words.get(2)));
        if (lesson.sentences().isEmpty()) {
            questions.add(g.pinyin(words.get(3)));
            questions.add(g.meaning(words.get(4)));
        } else {
            questions.add(g.sentenceMeaning(lesson.sentences().get(0)));
            questions.add(g.listenBuild(lesson.sentences().get(0)));
        }
        return questions;
    }

    /**
     * Eight questions spread over the whole unit (bonus words aside), starting with what the review itself adds: the
     * earlier sounds and sentences it brings back.
     */
    private static List<Question> review(UnitSpec unit, LessonSpec review, Pool unitPool, Pool earlier) {
        List<Word> words = new ArrayList<>();
        List<Sentence> unitSentences = new ArrayList<>();
        unit.lessons().stream().filter(lesson -> lesson.kind() == LearnLessonKind.LESSON).forEach(lesson -> {
            words.addAll(lesson.words());
            unitSentences.addAll(lesson.sentences());
        });
        Generator g = new Generator(unitPool, earlier, unit.number());
        List<Question> questions = new ArrayList<>();
        boolean isSoundReview = !words.isEmpty()
                && words.stream().allMatch(Word::isSoundDrill)
                && review.words().stream().allMatch(Word::isSoundDrill);
        if (isSoundReview) {
            List<Word> picks = new ArrayList<>(review.words());
            picks.addAll(spread(words, REVIEW_QUESTIONS - picks.size()));
            for (int index = 0; index < Math.min(REVIEW_QUESTIONS, picks.size()); index++) {
                questions.add(index % 2 == 0 ? g.pinyin(picks.get(index)) : g.listenSound(picks.get(index)));
            }
            return questions;
        }
        review.words().stream().limit(REVIEW_QUESTIONS).forEach(word -> questions.add(g.pinyin(word)));
        int remaining = REVIEW_QUESTIONS - questions.size();
        if (remaining <= 0) {
            return questions;
        }
        List<Sentence> reviewSentences = new ArrayList<>(review.sentences());
        if (reviewSentences.size() < 3 && !unitSentences.isEmpty()) {
            reviewSentences.addAll(spread(unitSentences, 3 - reviewSentences.size()));
        }
        if (reviewSentences.isEmpty()) {
            List<Word> picks = spread(words, remaining);
            for (int index = 0; index < picks.size(); index++) {
                Word word = picks.get(index);
                questions.add(switch (index % 4) {
                    case 0 -> g.meaning(word);
                    case 1 -> g.hanzi(word);
                    case 2 -> g.listenChoose(word);
                    default -> g.pinyin(word);
                });
            }
            return questions;
        }
        int step = Math.max(1, words.size() / 5);
        List<Question> mixed = List.of(
                g.meaning(words.get(0)),
                g.hanzi(words.get(step % words.size())),
                g.sentenceMeaning(reviewSentences.get(1 % reviewSentences.size())),
                g.listenBuild(reviewSentences.get(2 % reviewSentences.size())),
                g.translateBuild(reviewSentences.get(0)),
                g.listenChoose(words.get((3 * step) % words.size())),
                g.pinyin(words.get((2 * step) % words.size())),
                g.meaning(words.get((4 * step) % words.size())));
        questions.addAll(mixed.subList(0, Math.min(remaining, mixed.size())));
        return questions;
    }

    /** Ten questions from every unit of the level: a word and a sentence per unit, then listening and pinyin. */
    private static List<Question> checkpoint(List<UnitSpec> path, int level) {
        List<UnitSpec> units = path.stream().filter(unit -> unit.level() == level).toList();
        Pool levelPool = Pool.of(path.stream().filter(unit -> unit.level() <= level).toList());
        List<Question> questions = new ArrayList<>();
        List<List<LessonSpec>> lessonsByUnit = new ArrayList<>(units.size());
        for (UnitSpec unit : units) {
            List<LessonSpec> lessons = unit.lessons().stream()
                    .filter(lesson -> lesson.kind() == LearnLessonKind.LESSON)
                    .toList();
            lessonsByUnit.add(lessons);
            Generator g = new Generator(Pool.of(List.of(unit)), levelPool, unit.number() * 7);
            if (lessons.get(0).isPronunciation()) {
                List<LessonSpec> picks = spread(lessons, 4);
                LessonSpec pinyinStop = picks.get(2);
                LessonSpec listenStop = picks.get(3);
                questions.add(g.pinyin(pinyinStop.words().get(0)));
                questions.add(g.listenSound(listenStop.words().get(Math.min(1, listenStop.words().size() - 1))));
            } else {
                List<Word> unitWords = lessons.get(0).words();
                questions.add(g.meaning(unitWords.get(Math.min(1, unitWords.size() - 1))));
                Sentence sentence = lessons.stream()
                        .filter(lesson -> !lesson.sentences().isEmpty())
                        .findFirst()
                        .orElseThrow()
                        .sentences()
                        .get(0);
                questions.add(g.translateBuild(sentence));
            }
        }
        int secondIdx = Math.min(1, units.size() - 1);
        int thirdIdx = Math.min(2, units.size() - 1);
        UnitSpec second = units.get(secondIdx);
        List<LessonSpec> secondLessons = lessonsByUnit.get(secondIdx);
        UnitSpec third = units.get(thirdIdx);
        List<LessonSpec> thirdLessons = lessonsByUnit.get(thirdIdx);
        LessonSpec listenLesson = secondLessons.get(Math.min(1, secondLessons.size() - 1));
        LessonSpec pinyinLesson = thirdLessons.get(Math.min(3, thirdLessons.size() - 1));
        questions.add(new Generator(Pool.of(List.of(second)), levelPool, 11)
                .listenChoose(listenLesson.words().get(0)));
        questions.add(new Generator(Pool.of(List.of(third)), levelPool, 13)
                .pinyin(pinyinLesson.words().get(Math.min(1, pinyinLesson.words().size() - 1))));
        return questions;
    }

    /** {@code count} items spread evenly over the list; the list repeats when it is shorter. */
    private static <T> List<T> spread(List<T> items, int count) {
        if (count <= 0 || items.isEmpty()) {
            return List.of();
        }
        List<T> picks = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            picks.add(items.get(index * items.size() / count));
        }
        return picks;
    }

    /** The words and sentences distractors are drawn from: what was taught, never optional bonus chests or future stops. */
    private record Pool(List<Word> words, List<Sentence> sentences) {

        static Pool of(List<UnitSpec> units) {
            List<Word> words = new ArrayList<>();
            List<Sentence> sentences = new ArrayList<>();
            for (UnitSpec unit : units) {
                for (LessonSpec lesson : unit.lessons()) {
                    if (lesson.kind() == LearnLessonKind.BONUS) {
                        continue;
                    }
                    words.addAll(lesson.words());
                    sentences.addAll(lesson.sentences());
                }
            }
            return new Pool(List.copyOf(words), List.copyOf(sentences));
        }

        /** The unit's mandatory stops up to and including {@code stop} (plus {@code stop}'s own words if it is a bonus chest). */
        static Pool taughtUpTo(UnitSpec unit, LessonSpec stop) {
            List<Word> words = new ArrayList<>();
            List<Sentence> sentences = new ArrayList<>();
            for (LessonSpec lesson : unit.lessons()) {
                boolean isStop = lesson.equals(stop);
                if (isStop || lesson.kind() != LearnLessonKind.BONUS) {
                    words.addAll(lesson.words());
                    sentences.addAll(lesson.sentences());
                }
                if (isStop) {
                    return new Pool(List.copyOf(words), List.copyOf(sentences));
                }
            }
            throw new IllegalArgumentException("Stop not found in unit " + unit.number() + ": " + stop.title());
        }

        static Pool earlierUnits(List<UnitSpec> path, UnitSpec unit) {
            return of(path.stream().filter(other -> other.number() < unit.number()).toList());
        }
    }

    /**
     * Builds the questions of one stop, one question type per method, with distractors from the unit's pool first. Its
     * seed moves on with every question, so the answer's place and the distractors differ from question to question.
     */
    private static final class Generator {

        private final Pool unit;
        private final Pool earlier;
        private int seed;

        Generator(Pool unit, Pool earlier, int seed) {
            this.unit = unit;
            this.earlier = new Pool(excludingByKey(earlier.words(), unit.words(), Word::hanzi),
                    excludingByKey(earlier.sentences(), unit.sentences(), Sentence::tiles));
            this.seed = seed;
        }

        Question meaning(Word word) {
            List<String> options = choices(word.meaning(), others(word, Word::meaning));
            return new Question(LearnQuestionType.MEANING, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    plainOptions(options),
                    word.meaning(), word.hanzi() + " (" + word.pinyin() + ") means “" + word.meaning() + "”.");
        }

        /** "Select the correct pinyin": a sound drill word offers its own wrong pinyin, other words those of the pool. */
        Question pinyin(Word word) {
            List<String> options = word.isSoundDrill()
                    ? placed(word.pinyin(), word.wrongPinyin())
                    : choices(word.pinyin(), others(word, Word::pinyin));
            return new Question(LearnQuestionType.PINYIN, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    plainOptions(options),
                    word.pinyin(), withNote(word.hanzi() + " is read " + word.pinyin() + ": “" + word.meaning() + "”.", word.note()));
        }

        /** "Click what you hear" before characters are taught: the word is spoken and the answers are its pinyin. */
        Question listenSound(Word word) {
            if (!word.isSoundDrill()) {
                throw new IllegalArgumentException("Expected a sound drill word: " + word.hanzi());
            }
            List<String> options = placed(word.pinyin(), word.wrongPinyin());
            return new Question(LearnQuestionType.LISTEN_CHOOSE, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    plainOptions(options),
                    word.pinyin(), withNote("You heard " + word.pinyin() + ": " + word.hanzi() + ", “" + word.meaning() + "”.", word.note()));
        }

        /** "Click what you hear" with syllable tiles. {@code hanzi} holds the spaced syllables so the answer shows them. */
        Question drill(SoundDrill drill) {
            List<String> answer = LearnAnswers.tiles(drill.answer());
            List<String> tiles = new ArrayList<>(answer);
            tiles.addAll(LearnAnswers.tiles(drill.extras()));
            return new Question(LearnQuestionType.LISTEN_BUILD, drill.answer(), null, drill.meaning(), drill.audio(),
                    LearnAnswers.writeTiles(mixed(tiles, answer, drill.answer().length())), drill.answer(), drill.note());
        }

        Question hanzi(Word word) {
            return wordChoice(LearnQuestionType.HANZI, word,
                    "“" + word.meaning() + "” is " + word.hanzi() + " (" + word.pinyin() + ").");
        }

        Question listenChoose(Word word) {
            return wordChoice(LearnQuestionType.LISTEN_CHOOSE, word,
                    "You heard " + word.hanzi() + " (" + word.pinyin() + "): “" + word.meaning() + "”.");
        }

        Question sentenceMeaning(Sentence sentence) {
            List<String> candidates = preferUnit(unit.sentences(), earlier.sentences()).stream()
                    .filter(other -> !other.tiles().equals(sentence.tiles()))
                    .map(Sentence::meaning)
                    .toList();
            return new Question(LearnQuestionType.MEANING, sentence.hanzi(), sentence.pinyin(), sentence.meaning(),
                    sentence.hanzi(), plainOptions(choices(sentence.meaning(), candidates)), sentence.meaning(), explain(sentence));
        }

        Question listenBuild(Sentence sentence) {
            return build(LearnQuestionType.LISTEN_BUILD, sentence);
        }

        Question translateBuild(Sentence sentence) {
            return build(LearnQuestionType.TRANSLATE_BUILD, sentence);
        }

        private Question wordChoice(LearnQuestionType type, Word word, String explanation) {
            List<Word> ordered = placed(word, pickWords(word, type == LearnQuestionType.LISTEN_CHOOSE));
            return new Question(type, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(ordered.stream().map(item -> new ChoiceOption(item.hanzi(), item.pinyin())).toList()),
                    word.hanzi(), explanation);
        }

        private Question build(LearnQuestionType type, Sentence sentence) {
            List<String> answer = LearnAnswers.tiles(sentence.tiles());
            List<String> tiles = new ArrayList<>(answer);
            String unspaced = sentence.tiles().replace(" ", "");
            for (Word word : preferUnit(unit.words(), earlier.words())) {
                if (tiles.size() >= answer.size() + EXTRA_TILES) {
                    break;
                }
                if (!tiles.contains(word.hanzi()) && !unspaced.contains(word.hanzi())) {
                    tiles.add(word.hanzi());
                }
            }
            return new Question(type, sentence.hanzi(), sentence.pinyin(), sentence.meaning(), sentence.hanzi(),
                    LearnAnswers.writeTiles(mixed(tiles, answer, sentence.tiles().length())), String.join(" ", answer),
                    explain(sentence));
        }

        /** The tiles in a seed-dependent order that is never already the answer. */
        private List<String> mixed(List<String> tiles, List<String> answer, int salt) {
            Collections.shuffle(tiles, new Random(seed++ * 31L + salt));
            if (tiles.subList(0, answer.size()).equals(answer)) {
                Collections.rotate(tiles, 1);
            }
            return tiles;
        }

        private static String plainOptions(List<String> options) {
            return LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList());
        }

        private static String explain(Sentence sentence) {
            return withNote(sentence.hanzi() + " means “" + sentence.meaning() + "”.", sentence.note());
        }

        private static String withNote(String base, String note) {
            return note == null ? base : base + " " + note;
        }

        /** The answer with two distractors, the answer moving through the three places. */
        private List<String> choices(String answer, List<String> candidates) {
            Map<String, String> unique = new LinkedHashMap<>();
            for (String candidate : candidates) {
                if (unique.size() == DISTRACTORS) {
                    break;
                }
                if (!LearnAnswers.matches(candidate, answer)) {
                    String key = LearnAnswers.normalize(candidate);
                    if (!key.isEmpty()) {
                        unique.putIfAbsent(key, candidate);
                    }
                }
            }
            if (unique.size() < DISTRACTORS) {
                throw new IllegalStateException("Not enough distinct distractors for answer: " + answer);
            }
            return placed(answer, List.copyOf(unique.values()));
        }

        private <T> List<T> placed(T answer, List<T> distractors) {
            List<T> options = new ArrayList<>(distractors);
            options.add(Math.floorMod(seed++, options.size() + 1), answer);
            return options;
        }

        /** Two other words; a listening question also leaves out words that sound the same (他 and 她). */
        private List<Word> pickWords(Word word, boolean heard) {
            List<Word> picked = new ArrayList<>();
            for (Word candidate : preferUnit(unit.words(), earlier.words())) {
                if (picked.size() == DISTRACTORS) {
                    break;
                }
                boolean clash = candidate.hanzi().equals(word.hanzi())
                        || LearnAnswers.matches(candidate.meaning(), word.meaning())
                        || (heard && candidate.pinyin().equalsIgnoreCase(word.pinyin()))
                        || picked.stream().anyMatch(other -> other.hanzi().equals(candidate.hanzi())
                                || (heard && other.pinyin().equalsIgnoreCase(candidate.pinyin())));
                if (!clash) {
                    picked.add(candidate);
                }
            }
            if (picked.size() < DISTRACTORS) {
                throw new IllegalStateException("Not enough distinct distractor words for: " + word.hanzi());
            }
            return picked;
        }

        private List<String> others(Word word, Function<Word, String> field) {
            return preferUnit(unit.words(), earlier.words()).stream()
                    .filter(other -> !other.hanzi().equals(word.hanzi()))
                    .map(field)
                    .toList();
        }

        /** Remove overlap by identity key once per stop. Earlier items keep their order and may repeat across earlier units. */
        private static <T, K> List<T> excludingByKey(List<T> earlierItems, List<T> unitItems, Function<T, K> key) {
            Set<K> unitKeys = new HashSet<>(unitItems.size());
            for (T item : unitItems) {
                unitKeys.add(key.apply(item));
            }
            return earlierItems.stream().filter(item -> !unitKeys.contains(key.apply(item))).toList();
        }

        /** The list starting at a seed-dependent place, so neighbouring questions get different distractors. */
        private <T> List<T> rotate(List<T> items) {
            List<T> rotated = new ArrayList<>(items);
            Collections.rotate(rotated, -3 * seed);
            return rotated;
        }

        /** The unit's items first, then the earlier units', each part starting at a seed-dependent place. */
        private <T> List<T> preferUnit(List<T> unitItems, List<T> earlierItems) {
            List<T> all = rotate(unitItems);
            all.addAll(rotate(earlierItems));
            return all;
        }
    }
}
