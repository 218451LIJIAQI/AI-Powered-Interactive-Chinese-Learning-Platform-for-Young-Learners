package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.GuidebookResponse;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.LessonCompleteRequest;
import com.example.aichinese.dto.learn.LessonCompleteResponse;
import com.example.aichinese.dto.learn.LessonPlayResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.dto.learn.XpResult;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnLessonCompletion;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.AnswerKind;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.MistakeSource;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearnUnitPhraseRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.LearnPathModel.StopNode;
import com.example.aichinese.service.learn.LearnPathModel.UnitNode;

import lombok.RequiredArgsConstructor;

/**
 * The student's Learn path and lesson player. Answers are checked on the server; a wrong answer becomes a waiting
 * mistake. A finished lesson earns its XP the first time and opens the next stop; a checkpoint must be passed.
 */
@Service
@RequiredArgsConstructor
public class LearnPathService {

    private final LearnUnitRepository unitRepository;
    private final LearnUnitPhraseRepository phraseRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LearnLessonCompletionRepository completionRepository;
    private final MistakeService mistakeService;
    private final XpService xpService;
    private final HintPolicyService hintPolicyService;
    private final StudentLearningAccessService accessService;

    // Path

    @Transactional(readOnly = true)
    public LearnPathResponse path(SecurityUserDetails currentUser) {
        return path(accessService.requireStudent(currentUser));
    }

    @Transactional(readOnly = true)
    public LearnPathResponse path(StudentProfile student) {
        LearnPathModel model = model(student);
        Map<Long, Long> questionCounts = questionRepository.countPerCheckpoint().stream()
                .collect(Collectors.toMap(row -> row.getId(), row -> row.getTotal()));
        Optional<StopNode> nextStop = model.next();
        Long nextId = nextStop.map(stop -> stop.lesson().getId()).orElse(null);

        Map<Integer, List<UnitNode>> byLevel = new LinkedHashMap<>();
        model.units().forEach(unit -> byLevel.computeIfAbsent(unit.unit().getPathLevel(), level -> new ArrayList<>()).add(unit));
        List<LearnPathResponse.Level> levels = new ArrayList<>();
        byLevel.forEach((level, units) -> levels.add(toLevel(level, units, nextId, questionCounts)));

        // The next lesson to take: a checkpoint (Home and the Learn rail show it from the level) and an optional bonus
        // chest left behind at the end of the path are not lessons, so there is none then.
        LearnPathResponse.NextLesson next = nextStop
                .filter(StopNode::countsAsLesson)
                .map(LearnPathService::toNextLesson)
                .orElse(null);
        int completedLessons = model.units().stream().mapToInt(UnitNode::completedCount).sum();
        return new LearnPathResponse(completedLessons, levels, next);
    }

    @Transactional(readOnly = true)
    public GuidebookResponse guidebook(SecurityUserDetails currentUser, Long unitId) {
        accessService.requireStudent(currentUser);
        LearnUnit unit = unitRepository.findById(unitId)
                .filter(found -> found.getPublishStatus() == PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This unit is not on the learning path."));
        List<GuidebookResponse.Phrase> phrases = phraseRepository.findByUnitIdOrderByPositionAscIdAsc(unitId).stream()
                .map(phrase -> new GuidebookResponse.Phrase(phrase.getHanzi(), phrase.getPinyin(), phrase.getMeaning()))
                .toList();
        List<String> tips = unit.getGuidebookTips() == null
                ? List.of()
                : Arrays.stream(unit.getGuidebookTips().split("\\R")).map(String::strip).filter(tip -> !tip.isEmpty()).toList();
        return new GuidebookResponse(phrases, tips);
    }

    // Lesson player

    @Transactional(readOnly = true)
    public LessonPlayResponse lesson(SecurityUserDetails currentUser, Long lessonId) {
        StudentProfile student = accessService.requireStudent(currentUser);
        LearnLesson lesson = openStop(model(student), lessonId).lesson();
        List<LearnQuestion> questions = playableQuestions(lessonId);
        HintPolicyService.Run run = hintPolicyService.startRun(student, hintScope(lessonId));
        return new LessonPlayResponse(
                lesson.getTitle(),
                lesson.getLessonKind(),
                lesson.getLessonKind() == LearnLessonKind.CHECKPOINT ? RewardRules.CHECKPOINT_PASS_PERCENT : null,
                questions.stream().map(question -> gradable(question).view(question.getId())).toList(),
                run.startedAt(),
                run.signature());
    }

    /** Instant feedback on one answer; a wrong (or skipped) answer is kept as a mistake. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AnswerCheckResponse check(SecurityUserDetails currentUser, Long lessonId, QuestionAnswer answer) {
        StudentProfile student = accessService.requireStudent(currentUser);
        StopNode stop = openStop(model(student), lessonId);
        LearnQuestion question = lessonQuestion(lessonId, answer.questionId());
        GradableItem item = gradable(question);
        AnswerCheckResponse result = item.check(answer.answer());
        if (!result.correct()) {
            mistakeService.record(student, MistakeSource.LEARN, mistakeRef(question), stop.lesson().getTitle(), item,
                    answer.answer(), false);
        }
        return result;
    }

    /**
     * The one hint for a question of an open lesson, as much as the student's level gives and held to its hints per
     * set for the run the server started when the lesson was read ({@code runStartedAt}, signed). Checkpoints test what
     * a level taught, so they have no hints, like the level check. Hints never change grading or XP.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public HintDto.Step hint(SecurityUserDetails currentUser, Long lessonId, HintDto.QuestionRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        hintPolicyService.requireHintsOn(student);
        StopNode stop = openStop(model(student), lessonId);
        if (stop.lesson().getLessonKind() == LearnLessonKind.CHECKPOINT) {
            throw new ResponseStatusException(BAD_REQUEST, "Checkpoints have no hints.");
        }
        LearnQuestion question = lessonQuestion(lessonId, request.questionId());
        String scope = hintScope(lessonId);
        return hintPolicyService.hintInRun(student, scope, scope + question.getId(),
                new HintPolicyService.Run(request.runStartedAt(), request.runSignature()),
                HintBuilder.ladder(gradable(question)));
    }

    /** What a run of the lesson is signed for, and its questions' served hints start with ({@code LESSON#12#}). */
    private static String hintScope(Long lessonId) {
        return "LESSON#" + lessonId + "#";
    }

    /**
     * Grades every answer of the played stop. A lesson is finished whatever the score (its mistakes wait for review);
     * a checkpoint only from its pass mark. The first finish earns the stop's XP and opens the next stop.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LessonCompleteResponse complete(SecurityUserDetails currentUser, Long lessonId, LessonCompleteRequest request) {
        StudentProfile student = accessService.requireStudent(currentUser);
        // Acquire the student's XP lock before reading the path so concurrent completions serialize cleanly.
        XpService.XpSession session = xpService.begin(student);
        PathInputs inputs = pathInputs(session.student());
        LearnPathModel before = inputs.model();
        StopNode stop = openStop(before, lessonId);
        LearnLesson lesson = stop.lesson();
        List<LearnQuestion> questions = playableQuestions(lessonId);
        Map<Long, String> answers =
                LearnAnswers.firstAnswers(request.answers(), QuestionAnswer::questionId, QuestionAnswer::answer);
        // The player sends an entry for every question it showed (null for "Skip"): a lesson is finished by going
        // through it, not by sending its completion alone, and class quizzes hold the same rule.
        if (questions.stream().anyMatch(question -> !answers.containsKey(question.getId()))) {
            throw new ResponseStatusException(BAD_REQUEST, "Answer or skip every question before you finish the lesson.");
        }
        int correct = (int) questions.stream()
                .filter(question -> LearnAnswers.matches(question.getAnswer(), answers.get(question.getId())))
                .count();
        int accuracy = RewardRules.accuracyPercent(correct, questions.size());
        boolean checkpoint = lesson.getLessonKind() == LearnLessonKind.CHECKPOINT;
        // Exactly "70% or more", like Quiz Star: a rounded 69.5% does not pass.
        boolean passed = !checkpoint
                || correct * 100L >= (long) RewardRules.CHECKPOINT_PASS_PERCENT * questions.size();

        LessonCompleteResponse.NextStop next = null;
        if (passed) {
            recordCompletion(session.student(), lesson, accuracy, session.sessionTimestamp());
            session.award(XpService.SOURCE_LEARN_LESSON, lesson.getId(), lesson.getXpReward(), lesson.getTitle());
            session.finishedLessonOrSet();
            LearnPathModel after = inputs.withCompleted(lesson.getId());
            // The unit's last lesson makes it done; the level checkpoint after it is not part of the unit.
            if (after.unit(lesson.getUnit().getId()).map(unit -> unit.state() == UnitState.DONE).orElse(false)) {
                session.badge(RewardBadge.UNIT_CHAMPION);
            }
            next = after.next()
                    .map(found -> new LessonCompleteResponse.NextStop(
                            found.lesson().getTitle(),
                            before.stop(found.lesson().getId()).map(old -> old.state() == StopState.LOCKED).orElse(true)))
                    .orElse(null);
        }
        XpResult xp = session.finish();
        return new LessonCompleteResponse(
                lesson.getLessonKind(),
                passed,
                checkpoint ? RewardRules.CHECKPOINT_PASS_PERCENT : null,
                accuracy,
                next,
                xp);
    }

    private static LearnPathResponse.Level toLevel(
            int level, List<UnitNode> units, Long nextId, Map<Long, Long> questionCounts) {
        List<LearnPathResponse.Unit> unitViews = units.stream().map(unit -> toUnit(unit, nextId)).toList();
        LearnPathResponse.Checkpoint checkpoint = units.stream()
                .map(UnitNode::checkpoint)
                .flatMap(Optional::stream)
                .findFirst()
                .map(stop -> new LearnPathResponse.Checkpoint(
                        stop.lesson().getId(),
                        stop.lesson().getTitle(),
                        questionCounts.getOrDefault(stop.lesson().getId(), 0L).intValue(),
                        stop.lesson().getXpReward(),
                        RewardRules.CHECKPOINT_PASS_PERCENT,
                        stop.state()))
                .orElse(null);
        boolean locked = units.stream().allMatch(unit -> unit.state() == UnitState.LOCKED);
        return new LearnPathResponse.Level(level, units.get(0).unit().getHskLevel(), locked, unitViews, checkpoint);
    }

    private LearnQuestion lessonQuestion(Long lessonId, Long questionId) {
        return questionRepository.findByIdAndLessonId(questionId, lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This question is not part of the lesson."));
    }

    private List<LearnQuestion> playableQuestions(Long lessonId) {
        List<LearnQuestion> questions = questionRepository.findByLessonIdOrderByPositionAscIdAsc(lessonId);
        if (questions.isEmpty()) {
            throw new ResponseStatusException(CONFLICT, "This lesson has no questions yet.");
        }
        return questions;
    }

    private static LearnPathResponse.NextLesson toNextLesson(StopNode stop) {
        LearnUnit unit = stop.lesson().getUnit();
        return new LearnPathResponse.NextLesson(
                stop.lesson().getId(),
                stop.lesson().getTitle(),
                stop.lesson().getLessonKind(),
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                unit.getTitleZh(),
                stop.lesson().getMinutes(),
                stop.lesson().getXpReward());
    }

    private static LearnPathResponse.Unit toUnit(UnitNode node, Long nextId) {
        LearnUnit unit = node.unit();
        // The road shows the bonus chests too; the counts are of lessons only.
        List<StopNode> road = node.roadStops();
        // The first unit of a level behind a checkpoint waits for the checkpoint, not for the unit before it (done).
        boolean waitsForCheckpoint = road.stream().findFirst()
                .map(StopNode::unlockAfter)
                .map(after -> after.getLessonKind() == LearnLessonKind.CHECKPOINT)
                .orElse(false);
        UnitState state = node.state();
        Integer unlockAfterUnit = state == UnitState.LOCKED && node.previousUnit() != null && !waitsForCheckpoint
                ? node.previousUnit().getPosition()
                : null;
        return new LearnPathResponse.Unit(
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                unit.getTitleZh(),
                unit.getIconKey(),
                state,
                node.lessonCount(),
                node.completedCount(),
                unlockAfterUnit,
                road.stream().map(stop -> toStop(stop, nextId)).toList());
    }

    private static LearnPathResponse.Stop toStop(StopNode stop, Long nextId) {
        LearnLesson lesson = stop.lesson();
        return new LearnPathResponse.Stop(
                lesson.getId(),
                stop.position(),
                lesson.getTitle(),
                lesson.getLessonKind(),
                lesson.getMinutes(),
                lesson.getXpReward(),
                stop.state(),
                lesson.getId().equals(nextId),
                stop.state() == StopState.LOCKED ? stop.unlockAfter().getTitle() : null);
    }

    /** The path as this student sees it now. */
    private LearnPathModel model(StudentProfile student) {
        return pathInputs(student).model();
    }

    private PathInputs pathInputs(StudentProfile student) {
        return new PathInputs(
                unitRepository.findByPublishStatusOrderByPositionAsc(PublishStatus.PUBLISHED),
                lessonRepository.findPathInOrder(),
                Set.copyOf(completionRepository.findCompletedLessonIds(student.getId())));
    }

    private record PathInputs(List<LearnUnit> units, List<LearnLesson> lessons, Set<Long> completedIds) {
        LearnPathModel model() {
            return LearnPathModel.build(units, lessons, completedIds);
        }

        LearnPathModel withCompleted(Long lessonId) {
            if (completedIds.contains(lessonId)) {
                return model();
            }
            Set<Long> nextCompleted = new HashSet<>(completedIds);
            nextCompleted.add(lessonId);
            return LearnPathModel.build(units, lessons, nextCompleted);
        }
    }

    private static StopNode openStop(LearnPathModel model, Long lessonId) {
        StopNode stop = model.stop(lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This lesson is not on the learning path."));
        if (stop.state() == StopState.LOCKED) {
            throw new ResponseStatusException(CONFLICT,
                    "This lesson is still locked. Finish the lessons before it first.");
        }
        return stop;
    }

    private void recordCompletion(StudentProfile student, LearnLesson lesson, int accuracy, LocalDateTime now) {
        LearnLessonCompletion completion = completionRepository
                .findLockedByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElseGet(() -> {
                    LearnLessonCompletion created = new LearnLessonCompletion();
                    created.setStudent(student);
                    created.setLesson(lesson);
                    created.setFirstCompletedAt(now);
                    created.setCreatedAt(now);
                    return created;
                });
        completion.setAttempts(completion.getAttempts() + 1);
        completion.setBestAccuracy(Math.max(completion.getBestAccuracy(), accuracy));
        completion.setLastCompletedAt(now);
        completion.setUpdatedAt(now);
        completionRepository.saveAndFlush(completion);
    }

    /** The key of a Learn question's waiting mistake (one row per question and student). */
    public static String mistakeRef(LearnQuestion question) {
        return "LEARN#" + question.getId();
    }

    public static GradableItem gradable(LearnQuestion question) {
        return GradableItem.stored(
                question.getQuestionKind().buildsFromTiles() ? AnswerKind.BUILD : AnswerKind.CHOICE,
                question.getQuestionKind(),
                null,
                question.getHanzi(),
                question.getPinyin(),
                question.getMeaning(),
                question.getAudioText(),
                question.getChoices(),
                question.getAnswer(),
                question.getExplanation());
    }
}
