package com.example.aichinese.config.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.LearnPathContent.LessonSpec;
import com.example.aichinese.config.seed.LearnPathContent.UnitSpec;
import com.example.aichinese.dto.learn.LevelCheckQuestionData;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.LearnUnitPhrase;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.service.learn.PracticeSetDirectory;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Platform content every installation needs, in every profile (production included): the Learn path, the level
 * check question bank and the Practice library. The transaction locks the startup row before checking for missing
 * content. Each Practice type is checked separately to preserve the admins' changes.
 */
@Component
@Order(1)
@RequiredArgsConstructor
public class PlatformContentSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PlatformContentSeeder.class);

    private final LearnUnitRepository unitRepository;
    private final LevelCheckQuestionRepository levelCheckQuestionRepository;
    private final PracticeCatalog practiceCatalog;
    private final PracticeSetDirectory practiceSetDirectory;
    private final EntityManager entityManager;
    private final SystemSettingRepository systemSettings;

    @Override
    @Transactional
    public void run(String... args) {
        systemSettings.lockForSeeding();
        if (unitRepository.count() == 0) {
            seedLearnPath();
        }
        if (levelCheckQuestionRepository.count() == 0) {
            seedLevelCheck();
        }
        seedMissingPracticeSets();
    }

    private void seedLearnPath() {
        List<UnitSpec> path = LearnPathContent.units();
        int questionCount = 0;
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
            entityManager.persist(unit);

            for (int index = 0; index < spec.phrases().size(); index++) {
                LearnPathContent.Phrase data = spec.phrases().get(index);
                LearnUnitPhrase phrase = new LearnUnitPhrase();
                phrase.setUnit(unit);
                phrase.setPosition(index + 1);
                phrase.setHanzi(data.hanzi());
                phrase.setPinyin(data.pinyin());
                phrase.setMeaning(data.meaning());
                entityManager.persist(phrase);
            }

            for (int index = 0; index < spec.lessons().size(); index++) {
                LessonSpec lessonSpec = spec.lessons().get(index);
                LearnLesson lesson = new LearnLesson();
                lesson.setUnit(unit);
                lesson.setPosition(index + 1);
                lesson.setTitle(lessonSpec.title());
                lesson.setLessonKind(lessonSpec.kind());
                lesson.setMinutes(lessonSpec.minutes());
                lesson.setXpReward(lessonSpec.xp());
                entityManager.persist(lesson);
                List<LearnQuestionGenerator.Question> generated = LearnQuestionGenerator.questionsFor(spec, lessonSpec, path);
                for (int position = 0; position < generated.size(); position++) {
                    entityManager.persist(toEntity(lesson, position + 1, generated.get(position)));
                }
                questionCount += generated.size();
            }
        }
        log.info("Seeded the Learn path: {} units, {} questions.", path.size(), questionCount);
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
        List<LevelCheckQuestionData.Request> questions = LevelCheckContent.questions();
        for (int index = 0; index < questions.size(); index++) {
            LevelCheckQuestionData.Request data = questions.get(index);
            LevelCheckQuestion question = new LevelCheckQuestion();
            question.setPrompt(data.prompt());
            question.setHanzi(data.hanzi());
            question.setPinyin(data.pinyin());
            question.setAudioText(data.audioText());
            question.setSkill(data.skill());
            question.setDifficulty(data.difficulty());
            question.setMandarinLevel(data.level());
            question.setPublishStatus(data.status());
            entityManager.persist(question);
            // The content lists the right answer first; it moves through the places so it is not always first.
            List<LevelCheckQuestionData.Option> ordered = new ArrayList<>(data.options());
            Collections.rotate(ordered, index % ordered.size());
            for (int position = 0; position < ordered.size(); position++) {
                LevelCheckQuestionData.Option optionData = ordered.get(position);
                LevelCheckOption option = new LevelCheckOption();
                option.setQuestion(question);
                option.setPosition(position);
                option.setOptionText(optionData.text());
                option.setCorrect(optionData.correct());
                option.setExplanation(optionData.explanation());
                entityManager.persist(option);
            }
        }
        log.info("Seeded the level check: {} questions.", questions.size());
    }

    private void seedMissingPracticeSets() {
        Set<PracticeType> missingTypes = EnumSet.noneOf(PracticeType.class);
        for (PracticeType type : PracticeType.values()) {
            if (practiceSetDirectory.countPlatformSets(type) == 0) {
                missingTypes.add(type);
            }
        }
        if (missingTypes.isEmpty()) {
            return;
        }
        int seeded = 0;
        for (PracticeSetContent.SetSpec spec : PracticeSetContent.sets()) {
            if (!missingTypes.contains(spec.type())) {
                continue;
            }
            PracticeActivityContent set = practiceCatalog.create(spec.type(), spec.request(), null);
            set.setContentStatus(ContentStatus.PUBLISHED);
            seeded++;
        }
        log.info("Seeded the Practice library: {} sets across {}.", seeded, missingTypes);
    }
}
