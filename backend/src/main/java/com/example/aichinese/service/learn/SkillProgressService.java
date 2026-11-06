package com.example.aichinese.service.learn;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.SkillProgressResponse;
import com.example.aichinese.dto.learn.SkillProgressResponse.Skill;
import com.example.aichinese.dto.learn.SkillProgressResponse.SkillRow;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;

/**
 * The Skills card of the Progress page, read from real completions and nothing else. A skill's items are the published
 * Practice sets of its types and the lessons of the published Learn units it maps to (bonus chests, reviews and
 * checkpoints mix skills, so they do not count). An item is completed the first time the student finishes it: a
 * Practice set when every item was right, a Learn lesson when it was finished.
 */
@Service
public class SkillProgressService {

    private static final int WEEK_DAYS = 7;

    private static final Map<PracticeType, Skill> PRACTICE_SKILLS = Map.of(
            PracticeType.FLASHCARDS, Skill.VOCABULARY,
            PracticeType.MATCHING, Skill.VOCABULARY,
            PracticeType.QUIZZES, Skill.VOCABULARY,
            PracticeType.LISTENING, Skill.LISTENING,
            PracticeType.TRACING, Skill.CHARACTERS,
            PracticeType.DRAG_DROP, Skill.SENTENCES);

    private final PracticeCatalog practiceCatalog;
    private final PracticeCompletionRepository practiceCompletionRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnLessonCompletionRepository lessonCompletionRepository;
    private final LearnClock clock;

    public SkillProgressService(
            PracticeCatalog practiceCatalog,
            PracticeCompletionRepository practiceCompletionRepository,
            LearnLessonRepository lessonRepository,
            LearnLessonCompletionRepository lessonCompletionRepository,
            LearnClock clock) {
        this.practiceCatalog = practiceCatalog;
        this.practiceCompletionRepository = practiceCompletionRepository;
        this.lessonRepository = lessonRepository;
        this.lessonCompletionRepository = lessonCompletionRepository;
        this.clock = clock;
    }

    /** How many items a skill has and when the student first completed each completed one. */
    private static final class Tally {

        private int items;
        private final List<LocalDateTime> completedAt = new ArrayList<>();

        void add(int count, List<LocalDateTime> times) {
            items += count;
            completedAt.addAll(times);
        }
    }

    @Transactional(readOnly = true)
    public SkillProgressResponse skills(StudentProfile student) {
        Map<Skill, Tally> tallies = new EnumMap<>(Skill.class);
        for (Skill skill : Skill.values()) {
            tallies.put(skill, new Tally());
        }
        PRACTICE_SKILLS.forEach((type, skill) -> countPractice(student, type, tallies.get(skill)));
        countLearn(student, tallies);

        // The last 7 platform days, today included.
        LocalDateTime weekStart = clock.startOf(clock.today().minusDays(WEEK_DAYS - 1L));
        List<SkillRow> rows = new ArrayList<>();
        for (Skill skill : Skill.values()) {
            Tally tally = tallies.get(skill);
            int done = tally.completedAt.size();
            int doneBefore = (int) tally.completedAt.stream().filter(time -> time.isBefore(weekStart)).count();
            int percent = percent(done, tally.items);
            rows.add(new SkillRow(skill, percent, percent - percent(doneBefore, tally.items)));
        }
        boolean anyCompleted = tallies.values().stream().anyMatch(tally -> !tally.completedAt.isEmpty());
        Skill weakest = !anyCompleted ? null : rows.stream()
                .filter(row -> tallies.get(row.skill()).items > 0)
                .min(Comparator.comparingInt(SkillRow::percent))
                .map(SkillRow::skill)
                .orElse(null);
        return new SkillProgressResponse(rows, weakest);
    }

    private void countPractice(StudentProfile student, PracticeType type, Tally tally) {
        List<Long> setIds = practiceCatalog.sets(type, ContentStatus.PUBLISHED).stream()
                .map(PracticeActivityContent::getId)
                .toList();
        if (setIds.isEmpty()) {
            return;
        }
        Map<Long, LocalDateTime> firstAllCorrect = new HashMap<>();
        for (PracticeCompletion completion : practiceCompletionRepository
                .findByStudentIdAndPracticeTypeAndActivityIdIn(student.getId(), type, setIds)) {
            if (completion.allCorrect()) {
                firstAllCorrect.merge(completion.getActivityId(), completion.getCreatedAt(),
                        (first, second) -> first.isBefore(second) ? first : second);
            }
        }
        tally.add(setIds.size(), List.copyOf(firstAllCorrect.values()));
    }

    private void countLearn(StudentProfile student, Map<Skill, Tally> tallies) {
        Map<Long, LearnLessonCompletion> completions = lessonCompletionRepository.findByStudentIdWithLesson(student.getId()).stream()
                .collect(Collectors.toMap(completion -> completion.getLesson().getId(), completion -> completion));
        Map<Skill, Integer> items = new EnumMap<>(Skill.class);
        Map<Skill, List<LocalDateTime>> completed = new EnumMap<>(Skill.class);
        for (LearnLesson lesson : lessonRepository.findPathInOrder()) {
            if (lesson.getLessonKind() != LearnLessonKind.LESSON
                    || lesson.getUnit().getPublishStatus() != PublishStatus.PUBLISHED) {
                continue;
            }
            Skill skill = skillOfUnit(lesson.getUnit().getPosition());
            items.merge(skill, 1, Integer::sum);
            LearnLessonCompletion completion = completions.get(lesson.getId());
            if (completion != null) {
                completed.computeIfAbsent(skill, key -> new ArrayList<>()).add(completion.getFirstCompletedAt());
            }
        }
        items.forEach((skill, count) -> tallies.get(skill).add(count, completed.getOrDefault(skill, List.of())));
    }

    /**
     * What a Learn unit trains: Unit 1 is pinyin and hearing the tones, Unit 2 characters, Unit 3 first sentences,
     * Unit 4 describing and counting words, and Level 2 teaches sentence patterns.
     */
    static Skill skillOfUnit(int unitNumber) {
        return switch (unitNumber) {
            case 1 -> Skill.LISTENING;
            case 2 -> Skill.CHARACTERS;
            case 4 -> Skill.VOCABULARY;
            default -> Skill.SENTENCES;
        };
    }

    private static int percent(int done, int items) {
        return items == 0 ? 0 : Math.round(done * 100f / items);
    }
}
