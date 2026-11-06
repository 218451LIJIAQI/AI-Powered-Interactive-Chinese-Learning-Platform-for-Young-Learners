package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
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
        Pool unitPool = Pool.taughtUpTo(unit, lesson);
        Pool earlier = Pool.earlierUnits(path, unit);
        return switch (lesson.kind()) {
            case LESSON -> lesson.isPronunciation() ? pronunciation(lesson, unitPool, earlier) : lesson(lesson, unitPool, earlier);
            case BONUS -> lesson.isPronunciation() ? soundBonus(lesson, unitPool, earlier) : bonus(lesson, unitPool, earlier);
            case REVIEW -> review(unit, lesson, unitPool, earlier);
            case CHECKPOINT -> checkpoint(path, unit.level());
        };
    }

    // ===== Stops =====

    /** The correct pinyin and what you hear, nothing else: "Select the correct pinyin", then "Tap what you hear". */
    private static List<Question> pronunciation(LessonSpec lesson, Pool unit, Pool earlier) {
        Generator g = new Generator(unit, earlier, 0);
        List<Word> words = lesson.words();
        List<Question> questions = new ArrayList<>();
        questions.add(g.pinyin(words.get(0)));
        if (lesson.drill() != null) {
            questions.add(g.drill(lesson.drill()));
        }
        for (int index = 1; questions.size() < SOUND_QUESTIONS; index++) {
            Word word = words.get(index % words.size());
            questions.add(index % 2 == 1 ? g.listenSound(word) : g.pinyin(word));
        }
        return questions;
    }

    private static List<Question> lesson(LessonSpec lesson, Pool unit, Pool earlier) {
        List<Word> words = lesson.words();
        List<Sentence> sentences = lesson.sentences();
        Generator g = new Generator(unit, earlier, lesson.title().length());
        List<Question> questions = new ArrayList<>();
        if (sentences.isEmpty()) {
            List<Word> picks = spread(words, WORD_ONLY_QUESTIONS);
            questions.add(g.meaning(picks.get(0)));
            questions.add(g.listenChoose(picks.get(1)));
            questions.add(g.pinyin(picks.get(2)));
            questions.add(g.hanzi(picks.get(3)));
            questions.add(g.meaning(picks.get(4)));
            questions.add(g.listenChoose(picks.get(5)));
            return questions;
        }
        questions.add(g.meaning(words.get(0)));
        questions.add(g.listenChoose(words.get(1 % words.size())));
        questions.add(g.pinyin(words.get(2 % words.size())));
        questions.add(g.hanzi(words.get(3 % words.size())));
        questions.add(g.sentenceMeaning(sentences.get(0)));
        questions.add(g.listenBuild(sentences.get(1 % sentences.size())));
        if (sentences.size() > 1) {
            questions.add(g.translateBuild(sentences.get(sentences.size() > 2 ? 2 : 0)));
        }
        return questions;
    }

    private static List<Question> soundBonus(LessonSpec lesson, Pool unit, Pool earlier) {
        Generator g = new Generator(unit, earlier, 3);
        List<Question> questions = new ArrayList<>();
        for (int index = 0; index < SOUND_BONUS_QUESTIONS; index++) {
            Word word = lesson.words().get(index % lesson.words().size());
            questions.add(index % 2 == 0 ? g.pinyin(word) : g.listenSound(word));
        }
        return questions;
    }

    private static List<Question> bonus(LessonSpec lesson, Pool unit, Pool earlier) {
        List<Word> words = lesson.words();
        Generator g = new Generator(unit, earlier, 3);
        List<Question> questions = new ArrayList<>();
        questions.add(g.meaning(words.get(0)));
        questions.add(g.listenChoose(words.get(1 % words.size())));
        questions.add(g.hanzi(words.get(2 % words.size())));
        if (lesson.sentences().isEmpty()) {
            questions.add(g.pinyin(words.get(3 % words.size())));
            questions.add(g.meaning(words.get(4 % words.size())));
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
        List<Sentence> sentences = new ArrayList<>(review.sentences());
        unit.lessons().stream().filter(lesson -> lesson.kind() == LearnLessonKind.LESSON).forEach(lesson -> {
            words.addAll(lesson.words());
            sentences.addAll(lesson.sentences());
        });
        Generator g = new Generator(unitPool, earlier, unit.number());
        List<Question> questions = new ArrayList<>();
        if (sentences.isEmpty()) {
            List<Word> picks = new ArrayList<>(review.words());
            picks.addAll(spread(words, REVIEW_QUESTIONS - review.words().size()));
            for (int index = 0; index < picks.size(); index++) {
                questions.add(index % 2 == 0 ? g.pinyin(picks.get(index)) : g.listenSound(picks.get(index)));
            }
            return questions;
        }
        review.words().forEach(word -> questions.add(g.pinyin(word)));
        int step = Math.max(1, words.size() / 5);
        List<Question> mixed = List.of(
                g.meaning(words.get(0)),
                g.hanzi(words.get(step % words.size())),
                g.sentenceMeaning(sentences.get(1 % sentences.size())),
                g.listenBuild(sentences.get(2 % sentences.size())),
                g.translateBuild(sentences.get(0)),
                g.listenChoose(words.get((3 * step) % words.size())),
                g.pinyin(words.get((2 * step) % words.size())),
                g.meaning(words.get((4 * step) % words.size())));
        questions.addAll(mixed.subList(0, REVIEW_QUESTIONS - questions.size()));
        return questions;
    }

    /** Ten questions from every unit of the level: a word and a sentence per unit, then listening and pinyin. */
    private static List<Question> checkpoint(List<UnitSpec> path, int level) {
        List<UnitSpec> units = path.stream().filter(unit -> unit.level() == level).toList();
        Pool levelPool = Pool.of(units);
        List<Question> questions = new ArrayList<>();
        for (UnitSpec unit : units) {
            List<LessonSpec> lessons = unit.lessons().stream()
                    .filter(lesson -> lesson.kind() == LearnLessonKind.LESSON)
                    .toList();
            Generator g = new Generator(Pool.of(List.of(unit)), levelPool, unit.number() * 7);
            if (lessons.get(0).isPronunciation()) {
                questions.add(g.pinyin(lessons.get(2).words().get(0)));
                questions.add(g.listenSound(lessons.get(3).words().get(1)));
            } else {
                questions.add(g.meaning(lessons.get(0).words().get(1)));
                Sentence sentence = lessons.stream()
                        .filter(lesson -> !lesson.sentences().isEmpty())
                        .findFirst()
                        .orElseThrow()
                        .sentences()
                        .get(0);
                questions.add(g.translateBuild(sentence));
            }
        }
        UnitSpec second = units.get(Math.min(1, units.size() - 1));
        UnitSpec third = units.get(Math.min(2, units.size() - 1));
        questions.add(new Generator(Pool.of(List.of(second)), levelPool, 11)
                .listenChoose(second.lessons().get(1).words().get(0)));
        questions.add(new Generator(Pool.of(List.of(third)), levelPool, 13)
                .pinyin(third.lessons().get(3).words().get(1)));
        return questions;
    }

    /** {@code count} items spread evenly over the list; the list repeats when it is shorter. */
    private static List<Word> spread(List<Word> words, int count) {
        List<Word> picks = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            picks.add(words.get(index * words.size() / count % words.size()));
        }
        return picks;
    }

    // ===== Question types =====

    /** The words and sentences distractors are drawn from: what was taught, never what is still to come. */
    private record Pool(List<Word> words, List<Sentence> sentences) {

        static Pool of(List<UnitSpec> units) {
            List<Word> words = new ArrayList<>();
            List<Sentence> sentences = new ArrayList<>();
            units.forEach(unit -> unit.lessons().forEach(lesson -> {
                words.addAll(lesson.words());
                sentences.addAll(lesson.sentences());
            }));
            return new Pool(List.copyOf(words), List.copyOf(sentences));
        }

        /** The unit's stops up to and including {@code stop}. */
        static Pool taughtUpTo(UnitSpec unit, LessonSpec stop) {
            List<Word> words = new ArrayList<>();
            List<Sentence> sentences = new ArrayList<>();
            for (LessonSpec lesson : unit.lessons()) {
                words.addAll(lesson.words());
                sentences.addAll(lesson.sentences());
                if (lesson == stop) {
                    break;
                }
            }
            return new Pool(List.copyOf(words), List.copyOf(sentences));
        }

        static Pool earlierUnits(List<UnitSpec> path, UnitSpec unit) {
            return of(path.stream().filter(other -> other.number() < unit.number()).toList());
        }
    }

    private static final class Generator {

        private final Pool unit;
        private final Pool earlier;
        private int seed;

        Generator(Pool unit, Pool earlier, int seed) {
            this.unit = unit;
            this.earlier = earlier;
            this.seed = seed;
        }

        Question meaning(Word word) {
            List<String> options = choices(word.meaning(), others(word, Word::meaning));
            return new Question(LearnQuestionType.MEANING, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    word.meaning(), word.hanzi() + " (" + word.pinyin() + ") means “" + word.meaning() + "”.");
        }

        /** "Select the correct pinyin": a sound drill word offers its own wrong pinyin, other words those of the pool. */
        Question pinyin(Word word) {
            List<String> options = word.isSoundDrill()
                    ? placed(word.pinyin(), word.wrongPinyin())
                    : choices(word.pinyin(), others(word, Word::pinyin));
            return new Question(LearnQuestionType.PINYIN, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    word.pinyin(), withNote(word.hanzi() + " is read " + word.pinyin() + ": “" + word.meaning() + "”.", word.note()));
        }

        /** "Tap what you hear" before characters are taught: the word is spoken and the answers are its pinyin. */
        Question listenSound(Word word) {
            List<String> options = placed(word.pinyin(), word.wrongPinyin());
            return new Question(LearnQuestionType.LISTEN_CHOOSE, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    word.pinyin(), withNote("You heard " + word.pinyin() + ": " + word.hanzi() + ", “" + word.meaning() + "”.", word.note()));
        }

        /** "Tap what you hear" with syllable tiles. {@code hanzi} holds the spaced syllables so the answer shows them. */
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
            List<String> options = choices(sentence.meaning(), otherSentences(sentence).stream().map(Sentence::meaning).toList());
            return new Question(LearnQuestionType.MEANING, sentence.hanzi(), sentence.pinyin(), sentence.meaning(),
                    sentence.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    sentence.meaning(), explain(sentence));
        }

        Question listenBuild(Sentence sentence) {
            return build(LearnQuestionType.LISTEN_BUILD, sentence);
        }

        Question translateBuild(Sentence sentence) {
            return build(LearnQuestionType.TRANSLATE_BUILD, sentence);
        }

        private Question wordChoice(LearnQuestionType type, Word word, String explanation) {
            List<Word> distractors = pickWords(word, type == LearnQuestionType.LISTEN_CHOOSE);
            List<Word> ordered = place(word, distractors);
            return new Question(type, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(ordered.stream().map(item -> new ChoiceOption(item.hanzi(), item.pinyin())).toList()),
                    word.hanzi(), explanation);
        }

        private Question build(LearnQuestionType type, Sentence sentence) {
            List<String> answer = LearnAnswers.tiles(sentence.tiles());
            List<String> tiles = new ArrayList<>(answer);
            for (Word word : preferUnit(unit.words(), earlier.words())) {
                if (tiles.size() >= answer.size() + EXTRA_TILES) {
                    break;
                }
                if (!tiles.contains(word.hanzi()) && !sentence.tiles().replace(" ", "").contains(word.hanzi())) {
                    tiles.add(word.hanzi());
                }
            }
            return new Question(type, sentence.hanzi(), sentence.pinyin(), sentence.meaning(), sentence.hanzi(),
                    LearnAnswers.writeTiles(mixed(tiles, answer, sentence.tiles().length())), String.join(" ", answer),
                    explain(sentence));
        }

        /** The tiles in a seed-dependent order that is never already the answer. */
        private List<String> mixed(List<String> tiles, List<String> answer, int salt) {
            List<String> mixed = new ArrayList<>(tiles);
            Collections.shuffle(mixed, new Random(seed++ * 31L + salt));
            if (mixed.subList(0, answer.size()).equals(answer)) {
                Collections.rotate(mixed, 1);
            }
            return mixed;
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
                String key = LearnAnswers.matches(candidate, answer) ? null : candidate.toLowerCase();
                if (key != null && unique.size() < DISTRACTORS) {
                    unique.putIfAbsent(key, candidate);
                }
            }
            return placed(answer, List.copyOf(unique.values()));
        }

        private List<String> placed(String answer, List<String> distractors) {
            List<String> options = new ArrayList<>(distractors);
            options.add(Math.floorMod(seed++, options.size() + 1), answer);
            return options;
        }

        /** Two other words; a listening question also leaves out words that sound the same (他 and 她). */
        private List<Word> pickWords(Word word, boolean heard) {
            List<Word> picked = new ArrayList<>();
            for (Word candidate : preferUnit(unit.words(), earlier.words())) {
                boolean clash = candidate.hanzi().equals(word.hanzi())
                        || candidate.meaning().equalsIgnoreCase(word.meaning())
                        || (heard && candidate.pinyin().equalsIgnoreCase(word.pinyin()))
                        || picked.stream().anyMatch(other -> other.hanzi().equals(candidate.hanzi())
                                || (heard && other.pinyin().equalsIgnoreCase(candidate.pinyin())));
                if (!clash && picked.size() < DISTRACTORS) {
                    picked.add(candidate);
                }
            }
            return picked;
        }

        private List<Word> place(Word answer, List<Word> distractors) {
            List<Word> ordered = new ArrayList<>(distractors);
            ordered.add(Math.floorMod(seed++, ordered.size() + 1), answer);
            return ordered;
        }

        private List<String> others(Word word, Function<Word, String> field) {
            return preferUnit(unit.words(), earlier.words()).stream()
                    .filter(other -> !other.hanzi().equals(word.hanzi()))
                    .map(field)
                    .toList();
        }

        private List<Sentence> otherSentences(Sentence sentence) {
            return preferUnit(unit.sentences(), earlier.sentences()).stream()
                    .filter(other -> !other.tiles().equals(sentence.tiles()))
                    .toList();
        }

        /** The list starting at a seed-dependent place, so neighbouring questions get different distractors. */
        private <T> List<T> rotate(List<T> items) {
            List<T> rotated = new ArrayList<>(items);
            if (!rotated.isEmpty()) {
                Collections.rotate(rotated, -Math.floorMod(seed * 3, rotated.size()));
            }
            return rotated;
        }

        /** The unit's items first, then the earlier units', each part starting at a seed-dependent place. */
        private <T> List<T> preferUnit(List<T> unitItems, List<T> earlierItems) {
            List<T> all = new ArrayList<>(rotate(unitItems));
            all.addAll(rotate(earlierItems.stream().filter(item -> !unitItems.contains(item)).toList()));
            return all;
        }
    }
}
