package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.learn.AnswerCheckResponse;
import com.example.aichinese.dto.learn.GuidebookResponse;
import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.LessonCompleteRequest;
import com.example.aichinese.dto.learn.LessonCompleteResponse;
import com.example.aichinese.dto.learn.LessonPlayResponse;
import com.example.aichinese.dto.learn.QuestionAnswer;
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
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.service.learn.LearnPathModel.StopNode;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.LearnPathModel.UnitNode;
import com.example.aichinese.service.learn.LearnPathModel.UnitState;

/**
 * The student's Learn path and lesson player. Answers are checked on the server; a wrong answer becomes a waiting
 * mistake. A finished lesson earns its XP the first time and opens the next stop; a checkpoint must be passed.
 */
@Service
public class LearnPathService {

    private static final String LOCKED_MESSAGE = "This lesson is still locked. Finish the lessons before it first.";
    private static final String NOT_FOUND_MESSAGE = "This lesson is not on the learning path.";

    private final LearnUnitRepository unitRepository;
    private final LearnUnitPhraseRepository phraseRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LearnLessonCompletionRepository completionRepository;
    private final MistakeService mistakeService;
    private final XpService xpService;
    private final LearnClock clock;

    public LearnPathService(
            LearnUnitRepository unitRepository,
            LearnUnitPhraseRepository phraseRepository,
            LearnLessonRepository lessonRepository,
            LearnQuestionRepository questionRepository,
            LearnLessonCompletionRepository completionRepository,
            MistakeService mistakeService,
            XpService xpService,
            LearnClock clock) {
        this.unitRepository = unitRepository;
        this.phraseRepository = phraseRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.completionRepository = completionRepository;
        this.mistakeService = mistakeService;
        this.xpService = xpService;
        this.clock = clock;
    }

    // ===== Path =====

    @Transactional(readOnly = true)
    public LearnPathResponse path(StudentProfile student) {
        LearnPathModel model = model(student);
        Map<Long, Long> questionCounts = questionRepository.countPerLesson().stream()
                .collect(Collectors.toMap(row -> row.getId(), row -> row.getTotal()));
        Long nextId = model.next().map(stop -> stop.lesson().getId()).orElse(null);

        Map<Integer, List<UnitNode>> byLevel = new LinkedHashMap<>();
        model.units().forEach(unit -> byLevel.computeIfAbsent(unit.unit().getPathLevel(), level -> new ArrayList<>()).add(unit));
        List<LearnPathResponse.Level> levels = new ArrayList<>();
        byLevel.forEach((level, units) -> {
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
                            stop.state(),
                            stop.unlockAfter() == null ? null : stop.unlockAfter().getTitle()))
                    .orElse(null);
            int lessonCount = unitViews.stream().mapToInt(LearnPathResponse.Unit::lessonCount).sum();
            boolean locked = units.get(0).state() == UnitState.LOCKED;
            levels.add(new LearnPathResponse.Level(
                    level, units.get(0).unit().getHskLevel(), lessonCount, locked, unitViews, checkpoint));
        });

        LearnPathResponse.NextLesson next = model.next()
                .filter(StopNode::countsAsLesson)
                .map(stop -> {
                    LearnUnit unit = stop.lesson().getUnit();
                    return new LearnPathResponse.NextLesson(
                            stop.lesson().getId(),
                            stop.lesson().getTitle(),
                            stop.lesson().getLessonKind(),
                            unit.getId(),
                            unit.getPosition(),
                            unit.getTitle(),
                            unit.getTitleZh(),
                            stop.position(),
                            stop.unitLessons(),
                            stop.lesson().getMinutes(),
                            stop.lesson().getXpReward());
                })
                .orElse(null);
        return new LearnPathResponse(
                model.lessonCount(),
                model.completedLessonCount(),
                levels,
                next,
                mistakeService.waiting(student, MistakeSource.LEARN));
    }

    @Transactional(readOnly = true)
    public GuidebookResponse guidebook(Long unitId) {
        LearnUnit unit = unitRepository.findById(unitId)
                .filter(found -> found.getPublishStatus() == PublishStatus.PUBLISHED)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This unit is not on the learning path."));
        List<GuidebookResponse.Phrase> phrases = phraseRepository.findByUnitIdOrderByPositionAsc(unitId).stream()
                .map(phrase -> new GuidebookResponse.Phrase(phrase.getHanzi(), phrase.getPinyin(), phrase.getMeaning()))
                .toList();
        List<String> tips = unit.getGuidebookTips() == null
                ? List.of()
                : Arrays.stream(unit.getGuidebookTips().split("\\R")).map(String::strip).filter(tip -> !tip.isEmpty()).toList();
        return new GuidebookResponse(unit.getId(), unit.getPosition(), unit.getTitle(), unit.getTitleZh(), phrases, tips);
    }

    // ===== Lesson player =====

    @Transactional(readOnly = true)
    public LessonPlayResponse lesson(StudentProfile student, Long lessonId) {
        StopNode stop = openStop(model(student), lessonId);
        LearnLesson lesson = stop.lesson();
        LearnUnit unit = lesson.getUnit();
        List<LearnQuestion> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
        return new LessonPlayResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getLessonKind(),
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                stop.position(),
                stop.unitLessons(),
                lesson.getMinutes(),
                lesson.getXpReward(),
                lesson.getLessonKind() == LearnLessonKind.CHECKPOINT ? RewardRules.CHECKPOINT_PASS_PERCENT : null,
                questions.stream().map(question -> gradable(question).view(question.getId())).toList());
    }

    /** Instant feedback on one answer; a wrong (or skipped) answer is kept as a mistake. */
    @Transactional
    public AnswerCheckResponse check(StudentProfile student, Long lessonId, QuestionAnswer answer) {
        StopNode stop = openStop(model(student), lessonId);
        LearnQuestion question = questionRepository.findByIdAndLessonId(answer.questionId(), lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This question is not part of the lesson."));
        GradableItem item = gradable(question);
        AnswerCheckResponse result = item.check(answer.answer());
        if (!result.correct()) {
            mistakeService.record(student, MistakeSource.LEARN, mistakeRef(question), stop.lesson().getTitle(), item,
                    answer.answer());
        }
        return result;
    }

    /**
     * Grades every answer of the played stop. A lesson is finished whatever the score (its mistakes wait for review);
     * a checkpoint only from its pass mark. The first finish earns the stop's XP and opens the next stop.
     */
    @Transactional
    public LessonCompleteResponse complete(StudentProfile student, Long lessonId, LessonCompleteRequest request) {
        LearnPathModel before = model(student);
        StopNode stop = openStop(before, lessonId);
        LearnLesson lesson = stop.lesson();
        List<LearnQuestion> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId);
        Map<Long, String> answers = new LinkedHashMap<>();
        request.answers().forEach(answer -> answers.putIfAbsent(answer.questionId(), answer.answer()));
        int correct = (int) questions.stream()
                .filter(question -> gradable(question).isCorrect(answers.get(question.getId())))
                .count();
        int accuracy = RewardRules.accuracyPercent(correct, questions.size());
        boolean checkpoint = lesson.getLessonKind() == LearnLessonKind.CHECKPOINT;
        boolean passed = !checkpoint || accuracy >= RewardRules.CHECKPOINT_PASS_PERCENT;

        XpService.XpSession session = xpService.begin(student);
        LessonCompleteResponse.NextStop next = null;
        if (passed) {
            recordCompletion(session.student(), lesson, accuracy);
            session.award(XpService.SOURCE_LEARN_LESSON, lesson.getId(), lesson.getXpReward(), lesson.getTitle());
            session.finishedLessonOrSet();
            LearnPathModel after = model(session.student());
            if (after.unitOf(lessonId).orElseThrow().state() == UnitState.DONE) {
                session.badge(RewardBadge.UNIT_CHAMPION);
            }
            next = after.next()
                    .map(found -> new LessonCompleteResponse.NextStop(
                            found.lesson().getId(),
                            found.lesson().getTitle(),
                            before.stop(found.lesson().getId()).map(old -> old.state() == StopState.LOCKED).orElse(true)))
                    .orElse(null);
        }
        XpResult xp = session.finish();
        return new LessonCompleteResponse(
                lesson.getId(),
                lesson.getTitle(),
                lesson.getLessonKind(),
                lesson.getUnit().getPosition(),
                stop.position(),
                passed,
                checkpoint ? RewardRules.CHECKPOINT_PASS_PERCENT : null,
                correct,
                questions.size(),
                accuracy,
                request.durationSeconds(),
                next,
                xp);
    }

    // ===== Internals =====

    private static LearnPathResponse.Unit toUnit(UnitNode node, Long nextId) {
        LearnUnit unit = node.unit();
        List<StopNode> lessons = node.lessons();
        int completed = (int) lessons.stream().filter(stop -> stop.state() == StopState.DONE).count();
        Integer unlockAfterUnit = node.state() == UnitState.LOCKED && node.previousUnit() != null
                ? node.previousUnit().getPosition()
                : null;
        return new LearnPathResponse.Unit(
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                unit.getTitleZh(),
                unit.getIconKey(),
                unit.getHskLevel(),
                node.state(),
                lessons.size(),
                completed,
                unlockAfterUnit,
                lessons.stream().map(stop -> toStop(stop, nextId)).toList());
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
                stop.state() == StopState.LOCKED && stop.unlockAfter() != null ? stop.unlockAfter().getTitle() : null);
    }

    /** The path as this student sees it now. */
    private LearnPathModel model(StudentProfile student) {
        List<LearnUnit> units = unitRepository.findByPublishStatusOrderByPositionAsc(PublishStatus.PUBLISHED);
        Set<Long> unitIds = units.stream().map(LearnUnit::getId).collect(Collectors.toSet());
        List<LearnLesson> lessons = lessonRepository.findPathInOrder().stream()
                .filter(lesson -> unitIds.contains(lesson.getUnit().getId()))
                .toList();
        Set<Long> completed = completionRepository.findByStudentIdWithLesson(student.getId()).stream()
                .map(completion -> completion.getLesson().getId())
                .collect(Collectors.toSet());
        return LearnPathModel.build(units, lessons, completed);
    }

    private static StopNode openStop(LearnPathModel model, Long lessonId) {
        StopNode stop = model.stop(lessonId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, NOT_FOUND_MESSAGE));
        if (stop.state() == StopState.LOCKED) {
            throw new ResponseStatusException(CONFLICT, LOCKED_MESSAGE);
        }
        return stop;
    }

    private void recordCompletion(StudentProfile student, LearnLesson lesson, int accuracy) {
        LocalDateTime now = clock.now();
        LearnLessonCompletion completion = completionRepository
                .findLockedByStudentIdAndLessonId(student.getId(), lesson.getId())
                .orElse(null);
        if (completion == null) {
            completion = new LearnLessonCompletion();
            completion.setStudent(student);
            completion.setLesson(lesson);
            completion.setFirstCompletedAt(now);
            completion.setXpAwarded(lesson.getXpReward());
        }
        completion.setAttempts(completion.getAttempts() + 1);
        completion.setBestAccuracy(Math.max(completion.getBestAccuracy(), accuracy));
        completion.setLastCompletedAt(now);
        completionRepository.saveAndFlush(completion);
    }

    /** The key of a Learn question's waiting mistake (one row per question and student). */
    public static String mistakeRef(LearnQuestion question) {
        return "LEARN#" + question.getId();
    }

    public static GradableItem gradable(LearnQuestion question) {
        boolean build = question.getQuestionKind().buildsFromTiles();
        return new GradableItem(
                build ? AnswerKind.BUILD : AnswerKind.CHOICE,
                question.getQuestionKind(),
                null,
                question.getHanzi(),
                question.getPinyin(),
                question.getMeaning(),
                question.getAudioText(),
                build ? List.of() : LearnAnswers.readOptions(question.getChoices()),
                build ? LearnAnswers.readTiles(question.getChoices()) : List.of(),
                question.getAnswer(),
                question.getExplanation());
    }
}
