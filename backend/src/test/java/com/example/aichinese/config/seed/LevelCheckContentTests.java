package com.example.aichinese.config.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.learn.LevelCheckQuestionData.Option;
import com.example.aichinese.dto.learn.LevelCheckQuestionData.Request;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.service.learn.LearnAnswers;

/** Pins structural integrity, skill-tier coverage, and zero cross-skill answer leakage in {@link LevelCheckContent}. */
class LevelCheckContentTests {

    private static final List<Request> QUESTIONS = LevelCheckContent.questions();

    @Test
    void everyQuestionHasValidOptionsAndMediaFields() {
        assertThat(QUESTIONS).isNotEmpty();
        for (Request question : QUESTIONS) {
            String context = question.level() + "/" + question.difficulty() + "/" + question.skill() + ": " + question.prompt();
            assertThat(question.prompt()).as(context).isNotBlank();
            assertThat(question.options().size()).as(context).isBetween(3, 4);
            assertThat(question.options()).as(context)
                    .filteredOn(Option::correct)
                    .hasSize(1);
            List<String> normalized = question.options().stream()
                    .map(option -> LearnAnswers.normalize(option.text()))
                    .toList();
            assertThat(normalized).as(context).doesNotContain("").doesNotHaveDuplicates();
            assertThat(question.options()).as(context)
                    .extracting(Option::explanation)
                    .allSatisfy(explanation -> assertThat(explanation).isNotBlank());

            if (question.skill() == LevelCheckSkill.LISTENING) {
                assertThat(question.audioText()).as(context).isNotBlank();
                assertThat(question.hanzi()).as(context).isNull();
                assertThat(question.pinyin()).as(context).isNull();
            }
            if (question.skill() == LevelCheckSkill.PINYIN) {
                assertThat(question.hanzi()).as(context).isNotBlank();
                assertThat(question.pinyin()).as(context).isNull();
            }
        }
    }

    @Test
    void publishedBankCoversEveryDifficultyStepForEveryNonIdiomSkill() {
        List<Request> published = QUESTIONS.stream()
                .filter(question -> question.status() == PublishStatus.PUBLISHED)
                .toList();
        assertThat(published).hasSize(51);

        for (LevelCheckLevel level : LevelCheckLevel.values()) {
            for (ActivityDifficulty difficulty : ActivityDifficulty.values()) {
                for (LevelCheckSkill skill : LevelCheckSkill.values()) {
                    if (skill == LevelCheckSkill.IDIOMS) {
                        continue;
                    }
                    long count = published.stream()
                            .filter(question -> question.level() == level
                                    && question.difficulty() == difficulty
                                    && question.skill() == skill)
                            .count();
                    assertThat(count).as(level + " / " + difficulty + " / " + skill).isGreaterThanOrEqualTo(1);
                }
            }
        }
    }

    /**
     * Skills are asked in {@link LevelCheckSkill} enum order: an earlier published question that displays hanzi + pinyin
     * must not reveal the exact answer to a later published PINYIN question.
     */
    @Test
    void earlierSkillsNeverRevealAnswersToLaterPinyinQuestions() {
        Map<LevelCheckSkill, Integer> skillOrder = new EnumMap<>(LevelCheckSkill.class);
        LevelCheckSkill[] values = LevelCheckSkill.values();
        for (int index = 0; index < values.length; index++) {
            skillOrder.put(values[index], index);
        }
        int pinyinIndex = skillOrder.get(LevelCheckSkill.PINYIN);

        Set<String> exposedHanziWithPinyin = new HashSet<>();
        for (Request question : QUESTIONS) {
            if (question.status() != PublishStatus.PUBLISHED) {
                continue;
            }
            if (skillOrder.get(question.skill()) < pinyinIndex
                    && question.hanzi() != null && !question.hanzi().isBlank()
                    && question.pinyin() != null && !question.pinyin().isBlank()) {
                exposedHanziWithPinyin.add(question.hanzi().strip());
            }
        }

        for (Request question : QUESTIONS) {
            if (question.status() == PublishStatus.PUBLISHED && question.skill() == LevelCheckSkill.PINYIN) {
                assertThat(exposedHanziWithPinyin)
                        .as("PINYIN question for %s must not have its pinyin revealed by an earlier skill", question.hanzi())
                        .doesNotContain(question.hanzi().strip());
            }
        }
    }
}
