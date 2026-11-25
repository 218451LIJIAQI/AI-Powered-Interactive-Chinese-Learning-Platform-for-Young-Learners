package com.example.aichinese.service.learn;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.SkillProgressResponse;
import com.example.aichinese.dto.learn.SkillProgressResponse.Skill;
import com.example.aichinese.dto.learn.SkillProgressResponse.SkillRow;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.StudentLearningAccessService;

import lombok.RequiredArgsConstructor;

/**
 * The Skills card of the Progress page, read from real completions and nothing else. A skill's items are the published
 * Practice sets of its types and the lessons of the published Learn units it maps to (bonus chests, reviews and
 * checkpoints mix skills, so they do not count). An item is completed the first time the student finishes it: a
 * Practice set when every item was right, a Learn lesson when it was finished.
 */
@Service
@RequiredArgsConstructor
public class SkillProgressService {

    /**
     * Primary skill of each seeded Learn unit position when {@code learn_units} has no dedicated skill column:
     * Unit 1 = Pinyin and tones (LISTENING), Unit 2 = Characters and first words (CHARACTERS),
     * Unit 4 = Questions and counting (VOCABULARY); all remaining curriculum units focus on SENTENCES.
     */
    private static final Map<Integer, Skill> LEARN_UNIT_PRIMARY_SKILL = Map.of(
            1, Skill.LISTENING,
            2, Skill.CHARACTERS,
            4, Skill.VOCABULARY);

    // One row per published item, including unfinished items. Typing mixes skills and has no single skill to count.
    private static final String SKILL_ITEMS = String.join(" union all ",
            practiceItems(PracticeType.FLASHCARDS, Skill.VOCABULARY),
            practiceItems(PracticeType.MATCHING, Skill.VOCABULARY),
            practiceItems(PracticeType.LISTENING, Skill.LISTENING),
            practiceItems(PracticeType.TRACING, Skill.CHARACTERS),
            practiceItems(PracticeType.DRAG_DROP, Skill.SENTENCES),
            learnUnitItems());

    private static String learnUnitItems() {
        String branches = LEARN_UNIT_PRIMARY_SKILL.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(entry -> "when " + entry.getKey() + " then '" + entry.getValue().name() + "'")
                .collect(Collectors.joining(" "));
        return """
                select case u.position %s else '%s' end as skill, c.first_completed_at
                from learn_lessons l join learn_units u on u.id = l.unit_id
                left join learn_lesson_completions c on c.lesson_id = l.id and c.student_id = :student
                where l.lesson_kind = 'LESSON' and u.publish_status = 'PUBLISHED'
                """.formatted(branches, Skill.SENTENCES.name());
    }

    private final JdbcClient jdbc;
    private final LearnClock clock;
    private final StudentLearningAccessService accessService;

    @Transactional(readOnly = true)
    public SkillProgressResponse skills(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        String query = """
                select skill, count(*) as total, count(first_completed_at) as done,
                       count(case when first_completed_at < :weekStart then 1 end) as done_before
                from (
                """ + SKILL_ITEMS + ") items group by skill";
        var completed = new EnumMap<Skill, Counts>(Skill.class);
        jdbc.sql(query).param("student", student.getId()).param("weekStart", Timestamp.valueOf(clock.weekStart()))
                .query(Counts.class).list().forEach(counts -> completed.put(counts.skill(), counts));

        List<SkillRow> rows = new ArrayList<>();
        boolean anyCompleted = false;
        SkillRow weakest = null;
        for (Skill skill : Skill.values()) {
            Counts counts = completed.get(skill);
            if (counts == null) {
                rows.add(new SkillRow(skill, 0, 0));
                continue;
            }
            int percent = RewardRules.accuracyPercent(counts.done(), counts.total());
            SkillRow row = new SkillRow(skill, percent, percent - RewardRules.accuracyPercent(counts.doneBefore(), counts.total()));
            rows.add(row);
            anyCompleted |= counts.done() > 0;
            if (weakest == null || percent < weakest.percent()) {
                weakest = row;
            }
        }
        return new SkillProgressResponse(rows, anyCompleted ? weakest.skill() : null);
    }

    private static String practiceItems(PracticeType type, Skill skill) {
        return "select '" + skill.name() + "' as skill, p.first_success as first_completed_at"
                + PracticeSetDirectory.publishedWithProgress(type);
    }

    private record Counts(Skill skill, long total, long done, long doneBefore) {
    }
}
