package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.learn.AdminLearningPathResponse;
import com.example.aichinese.dto.learn.LevelCheckQuestionData;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnUnit;
import com.example.aichinese.entity.LevelCheckOption;
import com.example.aichinese.entity.LevelCheckQuestion;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.repository.projection.IdCountProjection;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

/**
 * The admin Learning path page: the units of the platform's own path (publish or unpublish a unit; students never see
 * a draft unit) and the level check question bank. Every change is audited.
 */
@Service
public class LearningPathAdminService {

    private final LearnUnitRepository unitRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnQuestionRepository questionRepository;
    private final LearnLessonCompletionRepository completionRepository;
    private final LevelCheckQuestionRepository levelCheckQuestionRepository;
    private final LevelCheckOptionRepository levelCheckOptionRepository;
    private final AuditLogService auditLogService;

    public LearningPathAdminService(
            LearnUnitRepository unitRepository,
            LearnLessonRepository lessonRepository,
            LearnQuestionRepository questionRepository,
            LearnLessonCompletionRepository completionRepository,
            LevelCheckQuestionRepository levelCheckQuestionRepository,
            LevelCheckOptionRepository levelCheckOptionRepository,
            AuditLogService auditLogService) {
        this.unitRepository = unitRepository;
        this.lessonRepository = lessonRepository;
        this.questionRepository = questionRepository;
        this.completionRepository = completionRepository;
        this.levelCheckQuestionRepository = levelCheckQuestionRepository;
        this.levelCheckOptionRepository = levelCheckOptionRepository;
        this.auditLogService = auditLogService;
    }

    // ===== Units =====

    @Transactional(readOnly = true)
    public AdminLearningPathResponse overview() {
        List<LearnUnit> units = unitRepository.findAllByOrderByPositionAsc();
        Map<Long, Long> lessonCounts = lessonRepository.findPathInOrder().stream()
                .filter(lesson -> lesson.getLessonKind() != LearnLessonKind.CHECKPOINT)
                .collect(Collectors.groupingBy(lesson -> lesson.getUnit().getId(), Collectors.counting()));
        Map<Long, Long> started = toMap(completionRepository.countStudentsPerUnit());

        Map<Integer, List<AdminLearningPathResponse.UnitRow>> byLevel = new LinkedHashMap<>();
        for (LearnUnit unit : units) {
            byLevel.computeIfAbsent(unit.getPathLevel(), level -> new ArrayList<>())
                    .add(toRow(unit, lessonCounts.getOrDefault(unit.getId(), 0L).intValue(),
                            started.getOrDefault(unit.getId(), 0L)));
        }
        List<AdminLearningPathResponse.Level> levels = new ArrayList<>();
        byLevel.forEach((level, rows) -> levels.add(new AdminLearningPathResponse.Level(
                level,
                rows.get(0).hskLevel(),
                rows.stream().mapToInt(AdminLearningPathResponse.UnitRow::lessonCount).sum(),
                rows)));
        int lessonCount = lessonCounts.values().stream().mapToInt(Long::intValue).sum();
        return new AdminLearningPathResponse(units.size(), lessonCount, levels);
    }

    @Transactional(readOnly = true)
    public AdminLearningPathResponse.UnitDetail unit(Long unitId) {
        return toDetail(requireUnit(unitId));
    }

    @Transactional
    public AdminLearningPathResponse.UnitDetail setUnitStatus(SecurityUserDetails admin, Long unitId, PublishStatus target) {
        LearnUnit unit = requireUnit(unitId);
        if (unit.getPublishStatus() == target) {
            throw new ResponseStatusException(BAD_REQUEST, target == PublishStatus.PUBLISHED
                    ? "This unit is already published."
                    : "This unit is already a draft.");
        }
        unit.setPublishStatus(target);
        unitRepository.save(unit);
        auditLogService.record(admin, target == PublishStatus.PUBLISHED ? "LEARN_UNIT_PUBLISH" : "LEARN_UNIT_UNPUBLISH",
                "LEARN_UNIT", unit.getId(), "Admin " + (target == PublishStatus.PUBLISHED ? "published" : "unpublished")
                        + " Learn unit " + unit.getPosition() + " \"" + unit.getTitle() + "\".");
        return toDetail(unit);
    }

    // ===== Level check questions =====

    @Transactional(readOnly = true)
    public PageResponse<LevelCheckQuestionData.Row> questions(int page, int size) {
        Page<LevelCheckQuestion> rows = levelCheckQuestionRepository.findAllForAdmin(PaginationUtils.pageRequest(page, size));
        return PageResponse.from(rows, LearningPathAdminService::toQuestionRow);
    }

    @Transactional(readOnly = true)
    public LevelCheckQuestionData.Detail question(Long id) {
        return toQuestionDetail(requireQuestion(id));
    }

    @Transactional
    public LevelCheckQuestionData.Detail createQuestion(SecurityUserDetails admin, LevelCheckQuestionData.Request request) {
        LevelCheckQuestion question = new LevelCheckQuestion();
        LevelCheckQuestion saved = write(question, request);
        auditLogService.record(admin, "LEVEL_CHECK_QUESTION_CREATE", "LEVEL_CHECK_QUESTION", saved.getId(),
                "Admin added a level check question.");
        return toQuestionDetail(saved);
    }

    @Transactional
    public LevelCheckQuestionData.Detail updateQuestion(
            SecurityUserDetails admin, Long id, LevelCheckQuestionData.Request request) {
        LevelCheckQuestion saved = write(requireQuestion(id), request);
        auditLogService.record(admin, "LEVEL_CHECK_QUESTION_UPDATE", "LEVEL_CHECK_QUESTION", saved.getId(),
                "Admin edited a level check question.");
        return toQuestionDetail(saved);
    }

    // ===== Internals =====

    private LevelCheckQuestion write(LevelCheckQuestion question, LevelCheckQuestionData.Request request) {
        long rightAnswers = request.options().stream().filter(LevelCheckQuestionData.Option::correct).count();
        if (rightAnswers != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Tick exactly one right answer.");
        }
        question.setPrompt(request.prompt().strip());
        question.setHanzi(TextUtils.trimToNull(request.hanzi()));
        question.setPinyin(TextUtils.trimToNull(request.pinyin()));
        question.setAudioText(TextUtils.trimToNull(request.audioText()));
        question.setSkill(request.skill());
        question.setDifficulty(request.difficulty());
        question.setMandarinLevel(request.level());
        question.setPublishStatus(request.status());
        LevelCheckQuestion saved = levelCheckQuestionRepository.save(question);
        levelCheckOptionRepository.deleteByQuestionId(saved.getId());
        List<LevelCheckOption> options = new ArrayList<>();
        for (int index = 0; index < request.options().size(); index++) {
            LevelCheckQuestionData.Option data = request.options().get(index);
            LevelCheckOption option = new LevelCheckOption();
            option.setQuestion(saved);
            option.setPosition(index);
            option.setOptionText(data.text().strip());
            option.setCorrect(data.correct());
            option.setExplanation(TextUtils.trimToNull(data.explanation()));
            options.add(option);
        }
        levelCheckOptionRepository.saveAll(options);
        return saved;
    }

    private LearnUnit requireUnit(Long unitId) {
        return unitRepository.findById(unitId)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This unit is not on the learning path."));
    }

    private LevelCheckQuestion requireQuestion(Long id) {
        return levelCheckQuestionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Level check question not found."));
    }

    private AdminLearningPathResponse.UnitDetail toDetail(LearnUnit unit) {
        List<LearnLesson> lessons = lessonRepository.findByUnitIdOrderByPositionAsc(unit.getId());
        Map<Long, Long> questionCounts = toMap(questionRepository.countPerLesson());
        Map<Long, Long> started = toMap(completionRepository.countStudentsPerUnit());
        int lessonCount = (int) lessons.stream().filter(lesson -> lesson.getLessonKind() != LearnLessonKind.CHECKPOINT).count();
        return new AdminLearningPathResponse.UnitDetail(
                toRow(unit, lessonCount, started.getOrDefault(unit.getId(), 0L)),
                lessons.stream()
                        .map(lesson -> new AdminLearningPathResponse.StopRow(
                                lesson.getId(),
                                lesson.getPosition(),
                                lesson.getTitle(),
                                lesson.getLessonKind(),
                                lesson.getMinutes(),
                                lesson.getXpReward(),
                                questionCounts.getOrDefault(lesson.getId(), 0L).intValue()))
                        .toList());
    }

    private static AdminLearningPathResponse.UnitRow toRow(LearnUnit unit, int lessonCount, long studentsStarted) {
        return new AdminLearningPathResponse.UnitRow(
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                unit.getTitleZh(),
                unit.getHskLevel(),
                lessonCount,
                studentsStarted,
                unit.getPublishStatus());
    }

    private static Map<Long, Long> toMap(List<IdCountProjection> rows) {
        return rows.stream().collect(Collectors.toMap(IdCountProjection::getId, IdCountProjection::getTotal));
    }

    private static Integer correctPercent(LevelCheckQuestion question) {
        int answered = question.getAnsweredCount() == null ? 0 : question.getAnsweredCount();
        return answered == 0 ? null : RewardRules.accuracyPercent(question.getCorrectCount(), answered);
    }

    private static LevelCheckQuestionData.Row toQuestionRow(LevelCheckQuestion question) {
        return new LevelCheckQuestionData.Row(
                question.getId(),
                question.getPrompt(),
                question.getHanzi(),
                question.getSkill(),
                question.getDifficulty(),
                question.getMandarinLevel(),
                question.getPublishStatus(),
                question.getAnsweredCount(),
                correctPercent(question));
    }

    private LevelCheckQuestionData.Detail toQuestionDetail(LevelCheckQuestion question) {
        List<LevelCheckQuestionData.Option> options = levelCheckOptionRepository
                .findByQuestionIdOrderByPositionAsc(question.getId()).stream()
                .map(option -> new LevelCheckQuestionData.Option(
                        option.getId(), option.getOptionText(), option.isCorrect(), option.getExplanation()))
                .toList();
        return new LevelCheckQuestionData.Detail(
                question.getId(),
                question.getPrompt(),
                question.getHanzi(),
                question.getPinyin(),
                question.getAudioText(),
                question.getSkill(),
                question.getDifficulty(),
                question.getMandarinLevel(),
                question.getPublishStatus(),
                question.getAnsweredCount(),
                correctPercent(question),
                options);
    }
}
