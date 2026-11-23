package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.LevelCheckNextRequest;
import com.example.aichinese.dto.learn.LevelCheckNextResponse;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.enums.ActivityDifficulty;
import com.example.aichinese.entity.enums.LevelCheckLevel;
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.MandarinLevel;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * The adaptive level check that places a new student. It keeps no state: every call sends all answers so far. It asks
 * one question per skill, in the order of {@link LevelCheckSkill}. The questions are ranked from Beginner 1 easy to
 * Beginner 2 hard; the check starts at the picked level (Beginner 1 by default), a right answer moves one step harder
 * and a wrong or "I don't know yet" answer one step easier, and each skill's question is the one nearest that step. It
 * stops when every skill was asked (8 questions), or from the third answer on when the level is already clear. Answers
 * are checked by the system, never by AI, and earn no XP.
 */
@Service
@RequiredArgsConstructor
public class LevelCheckService {

    private static final int EARLY_STOP_AFTER = 3;
    private static final List<ActivityDifficulty> DIFFICULTY_ORDER =
            List.of(ActivityDifficulty.EASY, ActivityDifficulty.MEDIUM, ActivityDifficulty.HARD);
    private static final int TOP_TIER = 5;

    private final LevelCheckQuestionRepository questionRepository;
    private final LevelCheckOptionRepository optionRepository;
    private final StudentLearningAccessService accessService;

    /**
     * Internal evaluation outcome of a finished level check, carrying both the wire-level summary and the server-only
     * answered count and head-start rule used by {@code LearningPlanService}.
     */
    public record Placement(
            MandarinLevel level,
            List<LevelCheckSkill> knownSkills,
            List<LevelCheckSkill> newSkills,
            int answeredCount) {

        /** The student already knows some Mandarin (Beginner 1 or 2), so the plan's first unit can be a quick review. */
        public boolean headStart() {
            return level != null && level != MandarinLevel.NEW_TO_MANDARIN;
        }

        public LevelCheckNextResponse.Result toResult() {
            return new LevelCheckNextResponse.Result(level, knownSkills, newSkills);
        }
    }

    @Transactional
    public LevelCheckNextResponse next(SecurityUserDetails currentUser, LevelCheckNextRequest request) {
        accessService.requireStudent(currentUser);
        return next(request);
    }

    @Transactional
    public LevelCheckNextResponse next(LevelCheckNextRequest request) {
        Replay replay = replay(request.startLevel(), request.answers());
        List<Graded> graded = replay.graded();
        LevelCheckNextResponse.Feedback feedback = graded.isEmpty() ? null : feedback(graded.getLast());
        boolean done = replay.done();
        return new LevelCheckNextResponse(
                feedback,
                replay.next().map(this::toQuestion).orElse(null),
                done,
                done ? placement(graded, request.startLevel()).toResult() : null,
                graded.size(),
                replay.maxQuestions());
    }

    /**
     * The placement of a finished check, graded again from the answers (the learning plan saves it, so the level is
     * always the system's, never the client's). Statistics are not counted twice. {@code startLevel} is where the
     * check started; it places the student only when no question is published, so nothing could be asked.
     */
    @Transactional(readOnly = true)
    public Placement evaluate(MandarinLevel startLevel, List<LevelCheckNextRequest.Answer> answers) {
        Replay replay = replay(startLevel, answers);
        // With published questions an empty list is never done: the check can't be skipped this way.
        if (!replay.done()) {
            throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
        }
        return placement(replay.graded(), startLevel);
    }

    /**
     * Grades the answers in order, moving up or down a tier after each, and finds the next question. Where the check
     * started ({@code startLevel}) only chooses the questions; it has no part in grading them.
     */
    private Replay replay(MandarinLevel startLevel, List<LevelCheckNextRequest.Answer> answers) {
        Set<Long> answeredIds = answers.stream().map(LevelCheckNextRequest.Answer::questionId)
                .collect(Collectors.toSet());
        List<LevelCheckQuestion> bank = questionRepository.findPublishedOrAnswered(answeredIds);
        Map<Long, LevelCheckQuestion> questions = bank.stream()
                .collect(Collectors.toMap(LevelCheckQuestion::getId, question -> question));
        if (!questions.keySet().containsAll(answeredIds)) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
        }
        Map<Long, List<LevelCheckOption>> options = answeredIds.isEmpty() ? Map.of()
                : optionRepository.findByQuestionIds(answeredIds).stream()
                        .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));

        int tier = startTier(startLevel);
        List<Graded> graded = new ArrayList<>();
        Set<LevelCheckSkill> askedSkills = EnumSet.noneOf(LevelCheckSkill.class);
        for (LevelCheckNextRequest.Answer answer : answers) {
            if (graded.size() >= EARLY_STOP_AFTER && levelIsClear(graded)) {
                throw new ResponseStatusException(BAD_REQUEST, "The level check is already complete.");
            }
            LevelCheckQuestion question = questions.get(answer.questionId());
            if (!askedSkills.add(question.getSkill())) {
                throw new ResponseStatusException(BAD_REQUEST, "Answer each level-check skill only once.");
            }
            List<LevelCheckOption> choices = options.getOrDefault(question.getId(), List.of());
            LevelCheckOption right = choices.stream()
                    .filter(LevelCheckOption::isCorrect)
                    .findFirst()
                    .orElse(null);
            LevelCheckOption chosen = choices.stream().filter(option -> option.getId().equals(answer.optionId()))
                    .findFirst().orElse(null);
            Graded grade = new Graded(question, answer.optionId() == null, chosen, right);
            graded.add(grade);
            tier = grade.correct() ? Math.min(TOP_TIER, tier + 1) : Math.max(0, tier - 1);
        }

        List<LevelCheckQuestion> published = bank.stream()
                .filter(question -> question.getPublishStatus() == PublishStatus.PUBLISHED).toList();
        // Keep a skill already answered even if its last published question was withdrawn during this check.
        Set<LevelCheckSkill> availableSkills = EnumSet.noneOf(LevelCheckSkill.class);
        availableSkills.addAll(askedSkills);
        published.forEach(question -> availableSkills.add(question.getSkill()));
        int maxQuestions = availableSkills.size();
        boolean done = graded.size() >= maxQuestions || (graded.size() >= EARLY_STOP_AFTER && levelIsClear(graded));
        Optional<LevelCheckQuestion> next = done ? Optional.empty() : pick(published, askedSkills, tier);
        return new Replay(graded, next, done, maxQuestions);
    }

    private record Replay(
            List<Graded> graded,
            Optional<LevelCheckQuestion> next,
            boolean done,
            int maxQuestions) {
    }

    // Rules

    private static int startTier(MandarinLevel picked) {
        if (picked == MandarinLevel.NEW_TO_MANDARIN) {
            return 0;
        }
        return picked == MandarinLevel.BEGINNER_2 ? 3 : 1;
    }

    private static int tierOf(LevelCheckQuestion question) {
        int base = question.getMandarinLevel() == LevelCheckLevel.BEGINNER_2 ? 3 : 0;
        return base + DIFFICULTY_ORDER.indexOf(question.getDifficulty());
    }

    /**
     * Clear early: every answer so far wrong or unknown, or the last three right on Beginner 2 questions AND the
     * overall Beginner 2 share already meets {@link #placement}'s threshold (so stopping early never contradicts it).
     */
    private static boolean levelIsClear(List<Graded> graded) {
        if (graded.stream().noneMatch(Graded::correct)) {
            return true;
        }
        List<Graded> lastThree = graded.subList(graded.size() - EARLY_STOP_AFTER, graded.size());
        if (!lastThree.stream().allMatch(entry -> entry.correct()
                && entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2)) {
            return false;
        }
        int b2Asked = 0;
        int b2Correct = 0;
        for (Graded entry : graded) {
            if (entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2) {
                b2Asked++;
                if (entry.correct()) {
                    b2Correct++;
                }
            }
        }
        return qualifiesForBeginner2(b2Correct, b2Asked);
    }

    private static boolean qualifiesForBeginner2(int b2Correct, int b2Asked) {
        return b2Correct >= 2 && b2Correct * 3 >= b2Asked * 2;
    }

    /**
     * The result of a finished check. A finished check without answers had no published question to ask: the student
     * stays at the level it started from (Beginner 1 by default, as {@link #startTier}).
     */
    private static Placement placement(List<Graded> graded, MandarinLevel startLevel) {
        if (graded.isEmpty()) {
            MandarinLevel level = startLevel == null ? MandarinLevel.BEGINNER_1 : startLevel;
            return new Placement(level, List.of(), List.of(), 0);
        }

        Set<LevelCheckSkill> known = EnumSet.noneOf(LevelCheckSkill.class);
        Set<LevelCheckSkill> fresh = EnumSet.noneOf(LevelCheckSkill.class);
        int b2Asked = 0;
        int b2Correct = 0;
        for (Graded entry : graded) {
            boolean correct = entry.correct();
            (correct ? known : fresh).add(entry.question().getSkill());
            if (entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2) {
                b2Asked++;
                if (correct) {
                    b2Correct++;
                }
            }
        }
        MandarinLevel level = qualifiesForBeginner2(b2Correct, b2Asked) ? MandarinLevel.BEGINNER_2
                : known.size() >= 2 ? MandarinLevel.BEGINNER_1
                : MandarinLevel.NEW_TO_MANDARIN;
        return new Placement(level, List.copyOf(known), List.copyOf(fresh), graded.size());
    }

    /**
     * A question of the next skill not asked yet: the one nearest to the target tier (the lower one on a tie, then the
     * oldest). Skills without a published question are passed over.
     */
    private static Optional<LevelCheckQuestion> pick(List<LevelCheckQuestion> published, Set<LevelCheckSkill> askedSkills, int tier) {
        return published.stream()
                .filter(question -> !askedSkills.contains(question.getSkill()))
                .min(Comparator.comparing(LevelCheckQuestion::getSkill)
                        .thenComparingInt((LevelCheckQuestion question) -> Math.abs(tierOf(question) - tier))
                        .thenComparingInt(LevelCheckService::tierOf)
                        .thenComparing(LevelCheckQuestion::getId));
    }

    /** Grades and counts the latest answer after the whole sequence has been validated. */
    private LevelCheckNextResponse.Feedback feedback(Graded last) {
        questionRepository.countAnswer(last.question().getId(), last.correct() ? 1 : 0);
        LevelCheckOption chosen = last.chosen();
        LevelCheckOption right = last.right();
        String explanation = !last.correct() && chosen != null && chosen.getExplanation() != null
                ? chosen.getExplanation()
                : right == null ? null : right.getExplanation();
        return new LevelCheckNextResponse.Feedback(
                last.correct(),
                last.skipped(),
                right == null ? null : TextUtils.stripFormSpaces(right.getOptionText()),
                explanation);
    }

    private LevelCheckNextResponse.Question toQuestion(LevelCheckQuestion question) {
        // Older questions may repeat a label. Keep its position and send the gradable ID for that label.
        Map<String, LevelCheckOption> byLabel = new LinkedHashMap<>();
        for (LevelCheckOption option : optionRepository.findByQuestionIdOrderByPositionAsc(question.getId())) {
            byLabel.merge(TextUtils.stripFormSpaces(option.getOptionText()), option,
                    (first, duplicate) -> duplicate.isCorrect() ? duplicate : first);
        }
        return new LevelCheckNextResponse.Question(
                question.getId(),
                question.getPrompt(),
                question.getHanzi(),
                question.getPinyin(),
                question.getAudioText(),
                question.getSkill(),
                byLabel.entrySet().stream()
                        .map(entry -> new LevelCheckNextResponse.Option(entry.getValue().getId(), entry.getKey()))
                        .toList());
    }

    private record Graded(LevelCheckQuestion question, boolean skipped, LevelCheckOption chosen, LevelCheckOption right) {
        boolean correct() {
            if (chosen == null || right == null) {
                return false;
            }
            // A student who already saw both IDs for the same answer gets the same grade for either one.
            String rightLabel = TextUtils.stripFormSpaces(right.getOptionText());
            return chosen.getId().equals(right.getId())
                    || (!rightLabel.isEmpty() && rightLabel.equals(TextUtils.stripFormSpaces(chosen.getOptionText())));
        }
    }
}
