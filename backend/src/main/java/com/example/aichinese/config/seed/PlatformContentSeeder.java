package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.LearnPathContent.LessonSpec;
import com.example.aichinese.config.seed.LearnPathContent.UnitSpec;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.LearnUnitPhrase;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearnUnitPhraseRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.repository.PracticeQuizRepository;
import com.example.aichinese.service.learn.PracticeCatalog;

/**
 * Platform content every installation needs, in every profile (production included): the Learn path, the level
 * check question bank and the Practice library. Each part is written only while its tables are empty, so admins'
 * later changes are never overwritten and a restart costs three count queries.
 */
@Component
@Order(1)
public class PlatformContentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformContentSeeder.class);

    private final LearnUnitRepository unitRepository;
    private final LearnUnitPhraseRepository phraseRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LevelCheckQuestionRepository levelCheckQuestionRepository;
    private final LevelCheckOptionRepository levelCheckOptionRepository;
    private final PracticeQuizRepository practiceQuizRepository;
    private final PracticeCatalog practiceCatalog;

    public PlatformContentSeeder(
            LearnUnitRepository unitRepository,
            LearnUnitPhraseRepository phraseRepository,
            LearnLessonRepository lessonRepository,
            LearnQuestionRepository questionRepository,
            LevelCheckQuestionRepository levelCheckQuestionRepository,
            LevelCheckOptionRepository levelCheckOptionRepository,
            PracticeQuizRepository practiceQuizRepository,
            PracticeCatalog practiceCatalog) {
        this.unitRepository = unitRepository;
        this.phraseRepository = phraseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.levelCheckQuestionRepository = levelCheckQuestionRepository;
        this.levelCheckOptionRepository = levelCheckOptionRepository;
        this.practiceQuizRepository = practiceQuizRepository;
        this.practiceCatalog = practiceCatalog;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (unitRepository.count() == 0) {
            seedLearnPath();
        }
        if (levelCheckQuestionRepository.count() == 0) {
            seedLevelCheck();
        }
        // Practice quizzes are new with the Practice library, so an empty table means it was never filled.
        if (practiceQuizRepository.count() == 0) {
            seedPracticeLibrary();
        }
    }

    private void seedLearnPath() {
        List<UnitSpec> path = LearnPathContent.units();
        List<LearnQuestion> questions = new ArrayList<>();
        for (UnitSpec spec : path) {
            LearnUnit unit = new LearnUnit();
            unit.setPosition(spec.number());
            unit.setPathLevel(spec.level());
            unit.setTitle(spec.title());
            unit.setTitleZh(spec.titleZh());
            unit.setHskLevel(spec.hsk());
            unit.setIconKey(spec.icon());
            unit.setPublishStatus(spec.status());
            unit.setGuidebookTips(String.join("\n", spec.tips()));
            unit = unitRepository.save(unit);

            List<LearnUnitPhrase> phrases = new ArrayList<>();
            for (int index = 0; index < spec.phrases().size(); index++) {
                LearnPathContent.Phrase data = spec.phrases().get(index);
                LearnUnitPhrase phrase = new LearnUnitPhrase();
                phrase.setUnit(unit);
                phrase.setPosition(index + 1);
                phrase.setHanzi(data.hanzi());
                phrase.setPinyin(data.pinyin());
                phrase.setMeaning(data.meaning());
                phrases.add(phrase);
            }
            phraseRepository.saveAll(phrases);

            for (int index = 0; index < spec.lessons().size(); index++) {
                LessonSpec lessonSpec = spec.lessons().get(index);
                LearnLesson lesson = new LearnLesson();
                lesson.setUnit(unit);
                lesson.setPosition(index + 1);
                lesson.setTitle(lessonSpec.title());
                lesson.setLessonKind(lessonSpec.kind());
                lesson.setMinutes(lessonSpec.minutes());
                lesson.setXpReward(lessonSpec.xp());
                lesson = lessonRepository.save(lesson);
                List<LearnQuestionGenerator.Question> generated = LearnQuestionGenerator.questionsFor(spec, lessonSpec, path);
                for (int position = 0; position < generated.size(); position++) {
                    questions.add(toEntity(lesson, position + 1, generated.get(position)));
                }
            }
        }
        questionRepository.saveAll(questions);
        log.info("Seeded the Learn path: {} units, {} questions.", path.size(), questions.size());
    }

    private static LearnQuestion toEntity(LearnLesson lesson, int position, LearnQuestionGenerator.Question generated) {
        LearnQuestion question = new LearnQuestion();
        question.setLesson(lesson);
        question.setPosition(position);
        question.setQuestionKind(generated.type());
        question.setHanzi(generated.hanzi());
        question.setPinyin(generated.pinyin());
        question.setMeaning(generated.meaning());
        question.setAudioText(generated.audioText());
        question.setChoices(generated.choices());
        question.setAnswer(generated.answer());
        question.setExplanation(generated.explanation());
        return question;
    }

    private void seedLevelCheck() {
        List<LevelCheckContent.Question> questions = LevelCheckContent.questions();
        List<LevelCheckOption> options = new ArrayList<>();
        for (int index = 0; index < questions.size(); index++) {
            LevelCheckContent.Question data = questions.get(index);
            LevelCheckQuestion question = new LevelCheckQuestion();
            question.setPrompt(data.prompt());
            question.setHanzi(data.hanzi());
            question.setPinyin(data.pinyin());
            question.setAudioText(data.audioText());
            question.setSkill(data.skill());
            question.setDifficulty(data.difficulty());
            question.setMandarinLevel(data.level());
            question.setPublishStatus(data.status());
            question = levelCheckQuestionRepository.save(question);
            // The content lists the right answer first; it moves through the places so it is not always first.
            List<LevelCheckContent.Option> ordered = new ArrayList<>(data.options());
            Collections.rotate(ordered, index % ordered.size());
            for (int position = 0; position < ordered.size(); position++) {
                LevelCheckContent.Option optionData = ordered.get(position);
                LevelCheckOption option = new LevelCheckOption();
                option.setQuestion(question);
                option.setPosition(position);
                option.setOptionText(optionData.text());
                option.setCorrect(optionData.correct());
                option.setExplanation(optionData.explanation());
                options.add(option);
            }
        }
        levelCheckOptionRepository.saveAll(options);
        log.info("Seeded the level check: {} questions.", questions.size());
    }

    private void seedPracticeLibrary() {
        List<PracticeSetContent.SetSpec> sets = PracticeSetContent.sets();
        for (PracticeSetContent.SetSpec spec : sets) {
            PracticeActivityContent set = practiceCatalog.create(spec.type(), spec.request(), null);
            set.setContentStatus(ContentStatus.PUBLISHED);
            practiceCatalog.save(spec.type(), set);
        }
        log.info("Seeded the Practice library: {} sets.", sets.size());
    }
}
