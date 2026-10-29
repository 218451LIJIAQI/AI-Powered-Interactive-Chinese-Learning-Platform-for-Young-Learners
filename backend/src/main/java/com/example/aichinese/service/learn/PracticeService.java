package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.ChoiceOption;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteResponse;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.PracticeItemData;
import com.example.aichinese.dto.learn.PracticeSetResponse;
import com.example.aichinese.dto.learn.PracticeSetsResponse;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.PracticeCatalog.Item;
import com.example.aichinese.service.learn.PracticeCatalog.SetContent;

/**
 * Practice for students: the published sets per type, a set to play (no answers), instant checks (a wrong answer
 * becomes a mistake) and finished runs. A set's XP is paid the first time every item is right; a quiz also pays a
 * one-time bonus for 3 right answers in a row.
 */
@Service
public class PracticeService {

    private final PracticeCatalog catalog;
    private final PracticeCompletionRepository completionRepository;
    private final MistakeService mistakeService;
    private final XpService xpService;

    public PracticeService(
            PracticeCatalog catalog,
            PracticeCompletionRepository completionRepository,
            MistakeService mistakeService,
            XpService xpService) {
        this.catalog = catalog;
        this.completionRepository = completionRepository;
        this.mistakeService = mistakeService;
        this.xpService = xpService;
    }

    @Transactional(readOnly = true)
    public PracticeSetsResponse sets(StudentProfile student, PracticeType type) {
        List<PracticeActivityContent> sets = catalog.sets(type, ContentStatus.PUBLISHED);
        Set<Long> done = doneSetIds(student, type, sets);
        return new PracticeSetsResponse(type, sets.stream()
                .map(set -> new PracticeSetsResponse.Summary(
                        set.getId(),
                        set.getTitle(),
                        set.getLearningStage(),
                        set.getHskLevel(),
                        catalog.itemCount(type, set),
                        xp(set),
                        done.contains(set.getId())))
                .toList());
    }

    @Transactional(readOnly = true)
    public PracticeSetResponse set(StudentProfile student, PracticeType type, Long id) {
        PracticeActivityContent set = published(type, id);
        SetContent content = catalog.content(type, set);
        List<String> matchingChoices = type == PracticeType.MATCHING
                ? PracticeCatalog.shuffled(content.items().stream().map(item -> item.data().meaning()).toList(), set.getId())
                : List.of();
        return new PracticeSetResponse(
                set.getId(),
                type,
                set.getTitle(),
                set.getLearningStage(),
                set.getHskLevel(),
                xp(set),
                doneSetIds(student, type, List.of(set)).contains(set.getId()),
                content.items().stream().map(item -> view(type, item)).toList(),
                content.storyLines(),
                matchingChoices,
                content.strokeCount());
    }

    /** Instant feedback on one answer; a wrong answer is kept as a mistake. */
    @Transactional
    public AnswerCheckResponse check(StudentProfile student, PracticeType type, Long id, PracticeItemAnswer answer) {
        PracticeActivityContent set = published(type, id);
        Item item = catalog.content(type, set).items().stream()
                .filter(candidate -> candidate.key().equals(answer.itemKey()))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This item is not part of the set."));
        if (item.gradable() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "This activity has no answers to check.");
        }
        AnswerCheckResponse result = item.gradable().check(answer.answer());
        if (!result.correct()) {
            mistakeService.record(student, MistakeSource.PRACTICE, mistakeRef(type, set, item), set.getTitle(),
                    item.gradable(), answer.answer());
        }
        return result;
    }

    /** Grades a finished run and records it; XP only the first time every item is right. */
    @Transactional
    public PracticeCompleteResponse complete(
            StudentProfile student, PracticeType type, Long id, PracticeCompleteRequest request) {
        PracticeActivityContent set = published(type, id);
        SetContent content = catalog.content(type, set);
        Map<String, String> answers = new LinkedHashMap<>();
        request.answers().forEach(answer -> answers.putIfAbsent(answer.itemKey(), answer.answer()));

        List<PracticeCompleteResponse.ItemResult> results = new ArrayList<>();
        for (Item item : content.items()) {
            boolean correct = switch (type) {
                case FLASHCARDS -> true;
                case TRACING -> request.strokeCount() != null
                        && request.strokeCount() >= Math.max(1, Objects.requireNonNullElse(content.strokeCount(), 1));
                default -> item.gradable() != null && item.gradable().isCorrect(answers.get(item.key()));
            };
            results.add(new PracticeCompleteResponse.ItemResult(item.key(), correct));
        }
        if (type == PracticeType.TRACING && request.strokeCount() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Draw the character before you finish.");
        }
        int correctCount = (int) results.stream().filter(PracticeCompleteResponse.ItemResult::correct).count();
        boolean allCorrect = !results.isEmpty() && correctCount == results.size();

        XpService.XpSession session = xpService.begin(student);
        int setXp = allCorrect
                ? session.award(XpService.practiceSource(type), set.getId(), xp(set), set.getTitle())
                : 0;
        int streakBonus = 0;
        if (type == PracticeType.QUIZZES) {
            Map<String, Boolean> byKey = results.stream()
                    .collect(Collectors.toMap(PracticeCompleteResponse.ItemResult::key,
                            PracticeCompleteResponse.ItemResult::correct));
            List<Boolean> inAnswerOrder = answers.keySet().stream().filter(byKey::containsKey).map(byKey::get).toList();
            if (RewardRules.longestRun(inAnswerOrder) >= RewardRules.QUIZ_STREAK_LENGTH) {
                streakBonus = session.award(XpService.SOURCE_QUIZ_STREAK, set.getId(), RewardRules.QUIZ_STREAK_BONUS_XP,
                        set.getTitle());
            }
            if (RewardRules.accuracyPercent(correctCount, results.size()) >= RewardRules.QUIZ_STAR_PERCENT) {
                session.badge(RewardBadge.QUIZ_STAR);
            }
        }
        PracticeCompletion completion = new PracticeCompletion();
        completion.setStudent(session.student());
        completion.setPracticeType(type);
        completion.setActivityId(set.getId());
        completion.setCorrectCount(correctCount);
        completion.setItemCount(results.size());
        completion.setXpAwarded(setXp + streakBonus);
        completionRepository.saveAndFlush(completion);
        session.finishedLessonOrSet();

        return new PracticeCompleteResponse(
                type,
                set.getId(),
                set.getTitle(),
                correctCount,
                results.size(),
                allCorrect,
                results,
                nextSet(type, set),
                session.finish());
    }

    // ===== Internals =====

    private PracticeActivityContent published(PracticeType type, Long id) {
        return catalog.find(type, id)
                .filter(set -> set.getContentStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This practice set is not available."));
    }

    private Set<Long> doneSetIds(StudentProfile student, PracticeType type, List<PracticeActivityContent> sets) {
        if (sets.isEmpty()) {
            return Set.of();
        }
        return completionRepository.findByStudentIdAndPracticeTypeAndActivityIdIn(
                        student.getId(), type, sets.stream().map(PracticeActivityContent::getId).toList()).stream()
                .filter(PracticeCompletion::allCorrect)
                .map(PracticeCompletion::getActivityId)
                .collect(Collectors.toSet());
    }

    /** The set after this one in the list, or the first one when this was the last. */
    private PracticeCompleteResponse.NextSet nextSet(PracticeType type, PracticeActivityContent current) {
        List<PracticeActivityContent> sets = catalog.sets(type, ContentStatus.PUBLISHED);
        if (sets.size() < 2) {
            return null;
        }
        int index = sets.indexOf(current);
        PracticeActivityContent next = sets.get(index < 0 ? 0 : (index + 1) % sets.size());
        return new PracticeCompleteResponse.NextSet(next.getId(), next.getTitle());
    }

    private static int xp(PracticeActivityContent set) {
        return set.getXpReward() == null ? 0 : set.getXpReward();
    }

    /** The key of a Practice item's waiting mistake (one row per item and student). */
    public static String mistakeRef(PracticeType type, PracticeActivityContent set, Item item) {
        return type.name() + "#" + set.getId() + "#" + item.key();
    }

    /** What a student may see of an item before answering it. */
    private static PracticeSetResponse.Item view(PracticeType type, Item item) {
        PracticeItemData data = item.data();
        List<ChoiceOption> options = item.gradable() == null ? List.of() : item.gradable().options();
        return switch (type) {
            case FLASHCARDS, TRACING -> new PracticeSetResponse.Item(item.key(), null, data.hanzi(), data.pinyin(),
                    data.meaning(), null, data.example(), data.exampleMeaning(), List.of(), List.of());
            case QUIZZES -> new PracticeSetResponse.Item(item.key(), data.prompt(), data.hanzi(), data.pinyin(), null,
                    null, null, null, options, List.of());
            case LISTENING -> new PracticeSetResponse.Item(item.key(), data.prompt(), null, null, null,
                    data.audioText(), null, null, options, List.of());
            case DRAG_DROP -> new PracticeSetResponse.Item(item.key(), null, null, null, data.meaning(), null, null,
                    null, List.of(), item.gradable().tiles());
            case MATCHING -> new PracticeSetResponse.Item(item.key(), null, data.hanzi(), data.pinyin(), null, null,
                    null, null, List.of(), List.of());
            case STORY -> new PracticeSetResponse.Item(item.key(), data.prompt(), null, null, null, null, null, null,
                    options, List.of());
        };
    }
}
