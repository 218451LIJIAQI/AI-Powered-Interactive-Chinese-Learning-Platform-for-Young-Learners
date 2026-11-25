package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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
 * The adaptive level check that places a new student. Every step is stateless on the database and bound by a
 * server-signed {@code runToken} ({@link LevelCheckRunSigner}) that fixes the learner, starting level, issued question
 * sequence and already-submitted answers. It asks one question per skill, in the order of {@link LevelCheckSkill}. The
 * questions are ranked from Beginner 1 easy to Beginner 2 hard; the check starts at the picked level (Beginner 1 by
 * default), a right answer moves one step harder and a wrong or "I don't know yet" answer one step easier, and each
 * skill's question is the one nearest that step. It stops when every skill was asked (8 questions), or from the third
 * answer on when the level is already clear. Answers are checked by the system, never by AI, and earn no XP.
 */
@Service
@RequiredArgsConstructor
public class LevelCheckService {

    private static final int EARLY_STOP_AFTER = 3;
    private static final List<ActivityDifficulty> DIFFICULTY_ORDER =
            List.of(ActivityDifficulty.EASY, ActivityDifficulty.MEDIUM, ActivityDifficulty.HARD);
    private static final int TOP_TIER = 5;
    private static final Duration COUNTED_STEP_TTL = Duration.ofHours(24);
    private static final int MAX_COUNTED_STEPS = 50_000;

    private final LevelCheckQuestionRepository questionRepository;
    private final LevelCheckOptionRepository optionRepository;
    private final StudentLearningAccessService accessService;
    private final LevelCheckRunSigner runSigner;
    private final Cache<String, Instant> countedSteps = Caffeine.newBuilder()
            .expireAfterWrite(COUNTED_STEP_TTL)
            .maximumSize(MAX_COUNTED_STEPS)
            .build();

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
            return new LevelCheckNextResponse.Result(level, newSkills);
        }
    }

    @Transactional
    public LevelCheckNextResponse next(SecurityUserDetails currentUser, LevelCheckNextRequest request) {
        Long studentId = accessService.requireStudent(currentUser).getId();
        MandarinLevel startLevel = normalizedStartLevel(request.startLevel());
        List<LevelCheckNextRequest.Answer> answers = request.answers();
        if (answers.isEmpty()) {
            List<LevelCheckQuestion> published =
                    questionRepository.findByPublishStatusOrderByIdAsc(PublishStatus.PUBLISHED);
            int maxQuestions = countDistinctSkills(published);
            boolean done = maxQuestions == 0;
            Optional<LevelCheckQuestion> first = done
                    ? Optional.empty()
                    : pick(published, EnumSet.noneOf(LevelCheckSkill.class), startTier(startLevel));
            String runToken = runSigner.sign(
                    studentId,
                    startLevel,
                    runSigner.newRunId(),
                    List.of(),
                    first.map(LevelCheckQuestion::getId).orElse(null),
                    done);
            return new LevelCheckNextResponse(
                    null,
                    first.map(this::toQuestion).orElse(null),
                    done,
                    done ? placement(List.of(), startLevel).toResult() : null,
                    0,
                    maxQuestions,
                    runToken);
        }

        LevelCheckRunSigner.RunState state = runSigner.verify(studentId, startLevel, request.runToken());
        if (state.done()) {
            throw new ResponseStatusException(BAD_REQUEST, "The level check is already complete.");
        }
        validateNextStepAnswers(answers, state);

        Replay replay = replay(startLevel, answers, state.issuedQuestionIds());
        List<Graded> graded = replay.graded();
        Graded last = graded.getLast();
        countAnswerOnce(state.runId(), answers.size(), last.question().getId(), last.correct());
        LevelCheckNextResponse.Feedback feedback = feedback(last);
        boolean done = replay.done();
        Optional<LevelCheckQuestion> nextQuestion = replay.next();
        String nextToken = runSigner.sign(
                studentId,
                startLevel,
                state.runId(),
                answers,
                nextQuestion.map(LevelCheckQuestion::getId).orElse(null),
                done);
        return new LevelCheckNextResponse(
                feedback,
                nextQuestion.map(this::toQuestion).orElse(null),
                done,
                done ? placement(graded, startLevel).toResult() : null,
                graded.size(),
                replay.maxQuestions(),
                nextToken);
    }

    /**
     * The placement of a finished check, graded again from the answers and verified against the learner's signed run
     * token (the learning plan saves it, so the level is always the system's, never the client's). Statistics are not
     * counted twice. {@code startLevel} is where the check started; it places the student only when no question is
     * published, so nothing could be asked.
     */
    @Transactional(readOnly = true)
    public Placement evaluate(
            Long studentId,
            MandarinLevel startLevel,
            List<LevelCheckNextRequest.Answer> answers,
            String runToken) {
        MandarinLevel normalized = normalizedStartLevel(startLevel);
        if (answers.isEmpty()) {
            List<LevelCheckQuestion> published =
                    questionRepository.findByPublishStatusOrderByIdAsc(PublishStatus.PUBLISHED);
            if (!published.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
            }
            if (runToken != null) {
                LevelCheckRunSigner.RunState state = runSigner.verify(studentId, normalized, runToken);
                if (!state.done() || !state.answered().isEmpty()) {
                    throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
                }
            }
            return placement(List.of(), normalized);
        }

        LevelCheckRunSigner.RunState state = runSigner.verify(studentId, normalized, runToken);
        if (!state.done()) {
            throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
        }
        if (!answers.equals(state.answered())) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
        }
        Replay replay = replay(normalized, answers, state.issuedQuestionIds());
        return placement(replay.graded(), normalized);
    }

    private static void validateNextStepAnswers(
            List<LevelCheckNextRequest.Answer> answers, LevelCheckRunSigner.RunState state) {
        List<LevelCheckNextRequest.Answer> previous = state.answered();
        Long expectedNext = state.nextQuestionId();
        if (expectedNext != null
                && answers.size() == previous.size() + 1
                && answers.subList(0, previous.size()).equals(previous)
                && expectedNext.equals(answers.getLast().questionId())) {
            return;
        }
        Set<Long> seenIds = new HashSet<>();
        for (LevelCheckNextRequest.Answer answer : answers) {
            if (!seenIds.add(answer.questionId())) {
                throw new ResponseStatusException(BAD_REQUEST, "Answer each level-check skill only once.");
            }
        }
        throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
    }

    private void countAnswerOnce(String runId, int stepNumber, Long questionId, boolean correct) {
        Instant now = Instant.now();
        String stepKey = runId + ":" + stepNumber;
        if (countedSteps.asMap().putIfAbsent(stepKey, now) != null) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        countedSteps.asMap().remove(stepKey, now);
                    }
                }
            });
        }
        questionRepository.countAnswer(questionId, correct ? 1 : 0);
    }

    /**
     * Grades the answers in order against only the server-issued questions of this run, moving up or down a tier after
     * each, and finds the next published question. Where the check started ({@code startLevel}) only chooses the
     * questions; it has no part in grading them.
     */
    private Replay replay(
            MandarinLevel startLevel, List<LevelCheckNextRequest.Answer> answers, Set<Long> issuedIds) {
        Set<Long> answeredIds = answers.stream().map(LevelCheckNextRequest.Answer::questionId)
                .collect(Collectors.toSet());
        if (!issuedIds.containsAll(answeredIds)) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
        }
        List<LevelCheckQuestion> bank = questionRepository.findPublishedOrAnswered(issuedIds);
        Map<Long, LevelCheckQuestion> questions = bank.stream()
                .collect(Collectors.toMap(LevelCheckQuestion::getId, question -> question));
        if (!questions.keySet().containsAll(answeredIds)) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
        }
        Map<Long, List<LevelCheckOption>> options = optionRepository.findByQuestionIds(answeredIds).stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));

        int tier = startTier(startLevel);
        List<Graded> graded = new ArrayList<>();
        Set<LevelCheckSkill> askedSkills = EnumSet.noneOf(LevelCheckSkill.class);
        for (LevelCheckNextRequest.Answer answer : answers) {
            LevelCheckQuestion question = questions.get(answer.questionId());
            askedSkills.add(question.getSkill());
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

    private static int countDistinctSkills(List<LevelCheckQuestion> published) {
        Set<LevelCheckSkill> skills = EnumSet.noneOf(LevelCheckSkill.class);
        published.forEach(question -> skills.add(question.getSkill()));
        return skills.size();
    }

    private static MandarinLevel normalizedStartLevel(MandarinLevel picked) {
        return picked == null ? MandarinLevel.BEGINNER_1 : picked;
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
        return lastThree.stream().allMatch(entry -> entry.correct()
                && entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2)
                && qualifiesForBeginner2(graded);
    }

    private static boolean qualifiesForBeginner2(List<Graded> graded) {
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
        return b2Correct >= 2 && b2Correct * 3 >= b2Asked * 2;
    }

    /**
     * The result of a finished check. A finished check without answers had no published question to ask: the student
     * stays at the level it started from (Beginner 1 by default, as {@link #startTier}).
     */
    private static Placement placement(List<Graded> graded, MandarinLevel startLevel) {
        if (graded.isEmpty()) {
            return new Placement(startLevel, List.of(), List.of(), 0);
        }

        Set<LevelCheckSkill> known = EnumSet.noneOf(LevelCheckSkill.class);
        Set<LevelCheckSkill> fresh = EnumSet.noneOf(LevelCheckSkill.class);
        for (Graded entry : graded) {
            (entry.correct() ? known : fresh).add(entry.question().getSkill());
        }
        fresh.removeAll(known);
        MandarinLevel level = qualifiesForBeginner2(graded) ? MandarinLevel.BEGINNER_2
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

    /** Formats feedback for the latest graded answer after the whole sequence has been validated and counted once. */
    private static LevelCheckNextResponse.Feedback feedback(Graded last) {
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
        for (LevelCheckOption option : optionRepository.findByQuestionIdOrderByPositionAscIdAsc(question.getId())) {
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
