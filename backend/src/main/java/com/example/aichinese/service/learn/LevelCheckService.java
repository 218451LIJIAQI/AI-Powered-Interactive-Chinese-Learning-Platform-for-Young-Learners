package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
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

/**
 * The adaptive level check that places a new student. It keeps no state: every call sends all answers so far. The
 * questions are ranked from Beginner 1 easy to Beginner 2 hard; the check starts at the picked level (Beginner 1 by
 * default), a right answer moves one step harder and a wrong or "I don't know yet" answer one step easier. It stops
 * after 6 questions, or from the third answer on when the level is already clear. Answers are checked by the system,
 * never by AI, and earn no XP.
 */
@Service
public class LevelCheckService {

    public static final int MAX_QUESTIONS = 6;
    static final int EARLY_STOP_AFTER = 3;

    private static final List<ActivityDifficulty> DIFFICULTY_ORDER =
            List.of(ActivityDifficulty.EASY, ActivityDifficulty.MEDIUM, ActivityDifficulty.HARD);
    private static final int TOP_TIER = 5;

    private final LevelCheckQuestionRepository questionRepository;
    private final LevelCheckOptionRepository optionRepository;

    public LevelCheckService(LevelCheckQuestionRepository questionRepository, LevelCheckOptionRepository optionRepository) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
    }

    @Transactional
    public LevelCheckNextResponse next(LevelCheckNextRequest request) {
        Replay replay = replay(request);
        List<Graded> graded = replay.graded();
        LevelCheckNextResponse.Feedback feedback = graded.isEmpty() ? null : feedback(graded, replay.options());
        Optional<LevelCheckQuestion> next = replay.next();
        boolean done = replay.done();
        return new LevelCheckNextResponse(
                feedback,
                done ? null : toQuestion(next.get(), replay.options().getOrDefault(next.get().getId(), List.of())),
                done,
                done ? result(graded) : null,
                graded.size(),
                MAX_QUESTIONS);
    }

    /**
     * The placement of a finished check, graded again from the answers (the learning plan saves it, so the level is
     * always the system's, never the client's). Statistics are not counted twice.
     */
    @Transactional(readOnly = true)
    public LevelCheckNextResponse.Result evaluate(LevelCheckNextRequest request) {
        Replay replay = replay(request);
        if (replay.graded().isEmpty() || !replay.done()) {
            throw new ResponseStatusException(BAD_REQUEST, "Finish the level check first.");
        }
        return result(replay.graded());
    }

    /** Grades the answers in order, moving up or down a tier after each, and finds the next question. */
    private Replay replay(LevelCheckNextRequest request) {
        List<LevelCheckQuestion> published = questionRepository.findByPublishStatusOrderByIdAsc(PublishStatus.PUBLISHED);
        Set<Long> answeredIds = request.answers().stream().map(LevelCheckNextRequest.Answer::questionId)
                .collect(Collectors.toSet());
        Map<Long, LevelCheckQuestion> questions = questionRepository.findAllById(answeredIds).stream()
                .collect(Collectors.toMap(LevelCheckQuestion::getId, question -> question));
        if (questions.size() != answeredIds.size()) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is not part of the level check.");
        }
        Set<Long> optionQuestionIds = new HashSet<>(answeredIds);
        published.forEach(question -> optionQuestionIds.add(question.getId()));
        Map<Long, List<LevelCheckOption>> options = optionRepository.findByQuestionIds(optionQuestionIds).stream()
                .collect(Collectors.groupingBy(option -> option.getQuestion().getId()));

        int tier = startTier(request.startLevel());
        List<Graded> graded = new ArrayList<>();
        for (LevelCheckNextRequest.Answer answer : request.answers()) {
            LevelCheckQuestion question = questions.get(answer.questionId());
            Optional<LevelCheckOption> right = options.getOrDefault(question.getId(), List.of()).stream()
                    .filter(LevelCheckOption::isCorrect)
                    .findFirst();
            boolean correct = right.isPresent() && right.get().getId().equals(answer.optionId());
            graded.add(new Graded(question, answer.optionId(), correct, right.orElse(null)));
            tier = correct ? Math.min(TOP_TIER, tier + 1) : Math.max(0, tier - 1);
        }

        Set<Long> asked = graded.stream().map(entry -> entry.question().getId()).collect(Collectors.toSet());
        Optional<LevelCheckQuestion> next = pick(published, asked, tier);
        boolean done = graded.size() >= MAX_QUESTIONS
                || (graded.size() >= EARLY_STOP_AFTER && levelIsClear(graded))
                || next.isEmpty();
        return new Replay(graded, options, next, done);
    }

    private record Replay(
            List<Graded> graded,
            Map<Long, List<LevelCheckOption>> options,
            Optional<LevelCheckQuestion> next,
            boolean done) {
    }

    // ===== Rules =====

    static int startTier(MandarinLevel picked) {
        if (picked == MandarinLevel.NEW_TO_MANDARIN) {
            return 0;
        }
        return picked == MandarinLevel.BEGINNER_2 ? 3 : 1;
    }

    static int tierOf(LevelCheckQuestion question) {
        int base = question.getMandarinLevel() == LevelCheckLevel.BEGINNER_2 ? 3 : 0;
        return base + DIFFICULTY_ORDER.indexOf(question.getDifficulty());
    }

    /** Clear early: every answer so far wrong or unknown, or the last three right on Beginner 2 questions. */
    static boolean levelIsClear(List<Graded> graded) {
        if (graded.stream().noneMatch(Graded::correct)) {
            return true;
        }
        List<Graded> lastThree = graded.subList(graded.size() - EARLY_STOP_AFTER, graded.size());
        return lastThree.stream().allMatch(entry -> entry.correct()
                && entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2);
    }

    static LevelCheckNextResponse.Result result(List<Graded> graded) {
        long b2Asked = graded.stream().filter(entry -> entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2).count();
        long b2Correct = graded.stream()
                .filter(entry -> entry.question().getMandarinLevel() == LevelCheckLevel.BEGINNER_2 && entry.correct())
                .count();
        long correct = graded.stream().filter(Graded::correct).count();
        MandarinLevel level;
        if (b2Correct >= 2 && b2Correct * 3 >= b2Asked * 2) {
            level = MandarinLevel.BEGINNER_2;
        } else if (correct >= 2) {
            level = MandarinLevel.BEGINNER_1;
        } else {
            level = MandarinLevel.NEW_TO_MANDARIN;
        }
        Map<LevelCheckSkill, Boolean> allRight = new EnumMap<>(LevelCheckSkill.class);
        graded.forEach(entry -> allRight.merge(entry.question().getSkill(), entry.correct(), Boolean::logicalAnd));
        List<LevelCheckSkill> known = allRight.entrySet().stream().filter(Map.Entry::getValue).map(Map.Entry::getKey).toList();
        List<LevelCheckSkill> fresh = allRight.entrySet().stream().filter(entry -> !entry.getValue())
                .map(Map.Entry::getKey).toList();
        return new LevelCheckNextResponse.Result(level, level != MandarinLevel.NEW_TO_MANDARIN, known, fresh,
                graded.size());
    }

    /** The unasked published question nearest to the target tier (the lower one on a tie, then the oldest). */
    static Optional<LevelCheckQuestion> pick(List<LevelCheckQuestion> published, Set<Long> asked, int tier) {
        return published.stream()
                .filter(question -> !asked.contains(question.getId()))
                .min(Comparator.comparingInt((LevelCheckQuestion question) -> Math.abs(tierOf(question) - tier))
                        .thenComparingInt(LevelCheckService::tierOf)
                        .thenComparing(LevelCheckQuestion::getId));
    }

    // ===== Internals =====

    /** Feedback on the last answer; it also counts in the question's statistics (once per check). */
    private LevelCheckNextResponse.Feedback feedback(List<Graded> graded, Map<Long, List<LevelCheckOption>> options) {
        Graded last = graded.get(graded.size() - 1);
        boolean firstTimeAsked = graded.stream().filter(entry -> entry.question().getId().equals(last.question().getId()))
                .count() == 1;
        if (firstTimeAsked) {
            questionRepository.findLockedById(last.question().getId()).ifPresent(question -> {
                question.setAnsweredCount(question.getAnsweredCount() + 1);
                if (last.correct()) {
                    question.setCorrectCount(question.getCorrectCount() + 1);
                }
                questionRepository.save(question);
            });
        }
        LevelCheckOption chosen = options.getOrDefault(last.question().getId(), List.of()).stream()
                .filter(option -> option.getId().equals(last.optionId()))
                .findFirst()
                .orElse(null);
        LevelCheckOption right = last.right();
        String explanation = !last.correct() && chosen != null && chosen.getExplanation() != null
                ? chosen.getExplanation()
                : right == null ? null : right.getExplanation();
        return new LevelCheckNextResponse.Feedback(
                last.correct(),
                last.optionId() == null,
                right == null ? null : right.getOptionText(),
                explanation);
    }

    private static LevelCheckNextResponse.Question toQuestion(LevelCheckQuestion question, List<LevelCheckOption> options) {
        return new LevelCheckNextResponse.Question(
                question.getId(),
                question.getPrompt(),
                question.getHanzi(),
                question.getPinyin(),
                question.getAudioText(),
                options.stream()
                        .sorted(Comparator.comparing(LevelCheckOption::getPosition))
                        .map(option -> new LevelCheckNextResponse.Option(option.getId(), option.getOptionText()))
                        .toList());
    }

    record Graded(LevelCheckQuestion question, Long optionId, boolean correct, LevelCheckOption right) {
    }
}
