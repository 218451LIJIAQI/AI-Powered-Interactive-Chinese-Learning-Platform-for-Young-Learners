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
import com.example.aichinese.config.seed.LearnPathContent.UnitSpec;
import com.example.aichinese.config.seed.LearnPathContent.Word;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.service.learn.LearnAnswers;

/**
 * Builds the questions of a Learn stop from its words and sentences: meaning, pinyin, character and listening choices
 * with distractors from the same unit, and sentence building from word tiles plus two extra tiles. Everything is
 * deterministic, so seeding twice gives the same path.
 */
final class LearnQuestionGenerator {

    private static final int DISTRACTORS = 2;
    private static final int EXTRA_TILES = 2;

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
        Pool unitPool = Pool.of(List.of(unit));
        Pool pathPool = Pool.of(path);
        return switch (lesson.kind()) {
            case LESSON -> lesson(lesson, unitPool, pathPool);
            case BONUS -> bonus(lesson, unitPool, pathPool);
            case REVIEW -> review(unit, unitPool, pathPool);
            case CHECKPOINT -> checkpoint(path, unit.level(), pathPool);
        };
    }

    // ===== Stops =====

    private static List<Question> lesson(LessonSpec lesson, Pool unit, Pool path) {
        List<Word> words = lesson.words();
        List<Sentence> sentences = lesson.sentences();
        Generator g = new Generator(unit, path, lesson.title().length());
        List<Question> questions = new ArrayList<>();
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

    private static List<Question> bonus(LessonSpec lesson, Pool unit, Pool path) {
        List<Word> words = lesson.words();
        Generator g = new Generator(unit, path, 3);
        return List.of(
                g.meaning(words.get(0)),
                g.listenChoose(words.get(1 % words.size())),
                g.hanzi(words.get(2 % words.size())),
                g.sentenceMeaning(lesson.sentences().get(0)),
                g.listenBuild(lesson.sentences().get(0)));
    }

    /** Eight questions spread over the whole unit (bonus words aside). */
    private static List<Question> review(UnitSpec unit, Pool unitPool, Pool path) {
        List<Word> words = new ArrayList<>();
        List<Sentence> sentences = new ArrayList<>();
        unit.lessons().stream().filter(lesson -> lesson.kind() == LearnLessonKind.LESSON).forEach(lesson -> {
            words.addAll(lesson.words());
            sentences.addAll(lesson.sentences());
        });
        int step = Math.max(1, words.size() / 5);
        Generator g = new Generator(unitPool, path, unit.number());
        return List.of(
                g.meaning(words.get(0)),
                g.hanzi(words.get(step % words.size())),
                g.pinyin(words.get((2 * step) % words.size())),
                g.listenChoose(words.get((3 * step) % words.size())),
                g.sentenceMeaning(sentences.get(1 % sentences.size())),
                g.listenBuild(sentences.get(2 % sentences.size())),
                g.translateBuild(sentences.get(sentences.size() - 1)),
                g.meaning(words.get((4 * step) % words.size())));
    }

    /** Ten questions from every unit of the level: a word and a sentence per unit, then listening and pinyin. */
    private static List<Question> checkpoint(List<UnitSpec> path, int level, Pool pathPool) {
        List<UnitSpec> units = path.stream().filter(unit -> unit.level() == level).toList();
        List<Question> questions = new ArrayList<>();
        for (UnitSpec unit : units) {
            List<LessonSpec> lessons = unit.lessons().stream()
                    .filter(lesson -> lesson.kind() == LearnLessonKind.LESSON)
                    .toList();
            Generator g = new Generator(Pool.of(List.of(unit)), pathPool, unit.number() * 7);
            questions.add(g.meaning(lessons.get(0).words().get(1)));
            questions.add(g.translateBuild(lessons.get(1).sentences().get(0)));
        }
        UnitSpec second = units.get(Math.min(1, units.size() - 1));
        UnitSpec third = units.get(Math.min(2, units.size() - 1));
        questions.add(new Generator(Pool.of(List.of(second)), pathPool, 11)
                .listenChoose(second.lessons().get(2).words().get(0)));
        questions.add(new Generator(Pool.of(List.of(third)), pathPool, 13)
                .pinyin(third.lessons().get(3).words().get(1)));
        return questions;
    }

    // ===== Question types =====

    /** The words and sentences distractors are drawn from: the unit first, then the whole path. */
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
    }

    private static final class Generator {

        private final Pool unit;
        private final Pool path;
        private int seed;

        Generator(Pool unit, Pool path, int seed) {
            this.unit = unit;
            this.path = path;
            this.seed = seed;
        }

        Question meaning(Word word) {
            List<String> options = choices(word.meaning(), others(word, Word::meaning));
            return new Question(LearnQuestionType.MEANING, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    word.meaning(), word.hanzi() + " (" + word.pinyin() + ") means “" + word.meaning() + "”.");
        }

        Question pinyin(Word word) {
            List<String> options = choices(word.pinyin(), others(word, Word::pinyin));
            return new Question(LearnQuestionType.PINYIN, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(options.stream().map(text -> new ChoiceOption(text, null)).toList()),
                    word.pinyin(), word.hanzi() + " is read " + word.pinyin() + ": “" + word.meaning() + "”.");
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
            List<Word> distractors = pickWords(word);
            List<Word> ordered = place(word, distractors);
            return new Question(type, word.hanzi(), word.pinyin(), word.meaning(), word.hanzi(),
                    LearnAnswers.writeOptions(ordered.stream().map(item -> new ChoiceOption(item.hanzi(), item.pinyin())).toList()),
                    word.hanzi(), explanation);
        }

        private Question build(LearnQuestionType type, Sentence sentence) {
            List<String> answer = LearnAnswers.tiles(sentence.tiles());
            List<String> tiles = new ArrayList<>(answer);
            for (Word word : rotate(unit.words())) {
                if (tiles.size() >= answer.size() + EXTRA_TILES) {
                    break;
                }
                if (!tiles.contains(word.hanzi()) && !sentence.tiles().replace(" ", "").contains(word.hanzi())) {
                    tiles.add(word.hanzi());
                }
            }
            List<String> mixed = new ArrayList<>(tiles);
            Collections.shuffle(mixed, new Random(seed++ * 31L + sentence.tiles().length()));
            if (mixed.subList(0, answer.size()).equals(answer)) {
                Collections.rotate(mixed, 1);
            }
            return new Question(type, sentence.hanzi(), sentence.pinyin(), sentence.meaning(), sentence.hanzi(),
                    LearnAnswers.writeTiles(mixed), String.join(" ", answer), explain(sentence));
        }

        private static String explain(Sentence sentence) {
            String base = sentence.hanzi() + " means “" + sentence.meaning() + "”.";
            return sentence.note() == null ? base : base + " " + sentence.note();
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
            List<String> options = new ArrayList<>(unique.values());
            options.add(Math.floorMod(seed++, options.size() + 1), answer);
            return options;
        }

        private List<Word> pickWords(Word word) {
            List<Word> picked = new ArrayList<>();
            for (Word candidate : preferUnit(unit.words(), path.words())) {
                boolean clash = candidate.hanzi().equals(word.hanzi())
                        || candidate.meaning().equalsIgnoreCase(word.meaning())
                        || picked.stream().anyMatch(other -> other.hanzi().equals(candidate.hanzi()));
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
            return preferUnit(unit.words(), path.words()).stream()
                    .filter(other -> !other.hanzi().equals(word.hanzi()))
                    .map(field)
                    .toList();
        }

        private List<Sentence> otherSentences(Sentence sentence) {
            return preferUnit(unit.sentences(), path.sentences()).stream()
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

        /** The unit's items first, then the rest of the path, each part starting at a seed-dependent place. */
        private <T> List<T> preferUnit(List<T> unitItems, List<T> pathItems) {
            List<T> all = new ArrayList<>(rotate(unitItems));
            all.addAll(rotate(pathItems.stream().filter(item -> !unitItems.contains(item)).toList()));
            return all;
        }
    }
}
