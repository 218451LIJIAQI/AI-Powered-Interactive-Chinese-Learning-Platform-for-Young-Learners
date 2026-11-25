package com.example.aichinese.service.learn;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
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
import com.example.aichinese.entity.enums.LevelCheckSkill;
import com.example.aichinese.entity.enums.PublishStatus;
import com.example.aichinese.repository.LearnLessonCompletionRepository;
import com.example.aichinese.repository.LearnLessonRepository;
import com.example.aichinese.repository.LearnUnitRepository;
import com.example.aichinese.repository.LevelCheckOptionRepository;
import com.example.aichinese.repository.LevelCheckQuestionRepository;
import com.example.aichinese.repository.projection.IdCountProjection;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.RewardRules;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

import lombok.RequiredArgsConstructor;

/**
 * The admin Learning path page: the units of the platform's own path (publish or unpublish a unit; students never see
 * a draft unit) and the level check question bank. What learners can notice is audited: a unit or question published
 * or unpublished, and an edit of a published question; a draft question being written is not.
 */
@Service
@RequiredArgsConstructor
public class LearningPathAdminService {

    private static final String QUESTION_TARGET = "LEVEL_CHECK_QUESTION";
    private static final String QUESTION_PUBLISH = "LEVEL_CHECK_QUESTION_PUBLISH";
    private static final String QUESTION_UNPUBLISH = "LEVEL_CHECK_QUESTION_UNPUBLISH";
    private static final String QUESTION_UPDATE = "LEVEL_CHECK_QUESTION_UPDATE";

    private final LearnUnitRepository unitRepository;
    private final LearnLessonRepository lessonRepository;
    private final LearnLessonCompletionRepository completionRepository;
    private final LevelCheckQuestionRepository levelCheckQuestionRepository;
    private final LevelCheckOptionRepository levelCheckOptionRepository;
    private final AuditLogService auditLogService;

    // Units

    @Transactional(readOnly = true)
    public AdminLearningPathResponse overview() {
        List<LearnUnit> units = unitRepository.findAllByOrderByPositionAsc();
        // One grouped query for every unit: its lessons and, among them, its bonus chests.
        Map<Long, LearnLessonRepository.UnitLessonCounts> lessonCounts = lessonRepository.countLessonsPerUnit().stream()
                .collect(Collectors.toMap(LearnLessonRepository.UnitLessonCounts::getId, Function.identity()));
        Map<Long, Long> started = completionRepository.countStudentsPerUnit().stream()
                .collect(Collectors.toMap(IdCountProjection::getId, IdCountProjection::getTotal));

        // The query counts every stop but a checkpoint; a bonus chest is not a lesson either (as on the student path).
        Map<Integer, List<AdminLearningPathResponse.UnitRow>> byLevel = new LinkedHashMap<>();
        for (LearnUnit unit : units) {
            LearnLessonRepository.UnitLessonCounts counts = lessonCounts.get(unit.getId());
            byLevel.computeIfAbsent(unit.getPathLevel(), level -> new ArrayList<>())
                    .add(toRow(unit,
                            counts == null ? 0 : (int) (counts.getTotal() - counts.getBonus()),
                            counts == null ? 0 : (int) counts.getBonus(),
                            started.getOrDefault(unit.getId(), 0L)));
        }
        List<AdminLearningPathResponse.Level> levels = new ArrayList<>();
        byLevel.forEach((level, rows) ->
                levels.add(new AdminLearningPathResponse.Level(level, rows.get(0).hskLevel(), rows)));
        return new AdminLearningPathResponse(units.size(), levels);
    }

    @Transactional(readOnly = true)
    public AdminLearningPathResponse.UnitDetail unit(Long unitId) {
        return toDetail(requireUnit(unitRepository.findById(unitId)));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminLearningPathResponse.UnitDetail setUnitStatus(SecurityUserDetails admin, Long unitId, PublishStatus target) {
        LearnUnit unit = requireUnit(unitRepository.findLockedById(unitId));
        boolean publish = target == PublishStatus.PUBLISHED;
        if (unit.getPublishStatus() == target) {
            throw new ResponseStatusException(BAD_REQUEST,
                    publish ? "This unit is already published." : "This unit is already a draft.");
        }
        unit.setPublishStatus(target);
        auditLogService.record(admin, publish ? "LEARN_UNIT_PUBLISH" : "LEARN_UNIT_UNPUBLISH", "LEARN_UNIT",
                unit.getId(), "Admin " + (publish ? "published" : "unpublished") + " Learn unit " + unit.getPosition()
                        + " \"" + unit.getTitle() + "\".");
        return toDetail(unit);
    }

    // Level check questions

    @Transactional(readOnly = true)
    public PageResponse<LevelCheckQuestionData.Row> questions(int page, int size) {
        return PageResponse.from(PaginationUtils.readPage(PaginationUtils.pageRequest(page, size),
                levelCheckQuestionRepository::findAllForAdmin), LearningPathAdminService::toQuestionRow);
    }

    @Transactional(readOnly = true)
    public LevelCheckQuestionData.Detail question(Long id) {
        return toQuestionDetail(requireQuestion(levelCheckQuestionRepository.findById(id)));
    }

    /** A new question; one saved as a draft is not audited until it is published. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LevelCheckQuestionData.Detail createQuestion(SecurityUserDetails admin, LevelCheckQuestionData.Request request) {
        LevelCheckQuestion saved = write(new LevelCheckQuestion(), request);
        if (saved.getPublishStatus() == PublishStatus.PUBLISHED) {
            auditQuestion(admin, QUESTION_PUBLISH, saved, "Admin added a level check question as published.");
        }
        return toQuestionDetail(saved);
    }

    /**
     * Saves the question with the status the form gives. Audited when learners can notice it: an edit of a published
     * question, or a change of status; an edit of a draft is not.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LevelCheckQuestionData.Detail updateQuestion(
            SecurityUserDetails admin, Long id, LevelCheckQuestionData.Request request) {
        LevelCheckQuestion question = requireQuestion(levelCheckQuestionRepository.findLockedById(id));
        PublishStatus before = question.getPublishStatus();
        LevelCheckQuestion saved = write(question, request);
        PublishStatus after = saved.getPublishStatus();
        if (before != after) {
            boolean published = after == PublishStatus.PUBLISHED;
            auditQuestion(admin, published ? QUESTION_PUBLISH : QUESTION_UNPUBLISH, saved,
                    published ? "Admin edited and published a level check question."
                            : "Admin edited a level check question and made it a draft again.");
        } else if (after == PublishStatus.PUBLISHED) {
            auditQuestion(admin, QUESTION_UPDATE, saved, "Admin edited a published level check question.");
        }
        return toQuestionDetail(saved);
    }

    /** Checks the stored choices and audio again, including drafts written before the current validation. */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LevelCheckQuestionData.Detail publishQuestion(SecurityUserDetails admin, Long id) {
        LevelCheckQuestion question = requireQuestion(levelCheckQuestionRepository.findLockedById(id));
        if (question.getPublishStatus() == PublishStatus.PUBLISHED) {
            throw new ResponseStatusException(BAD_REQUEST, "This question is already published.");
        }
        if (TextUtils.isBlankOrSpaces(question.getPrompt())) {
            throw new ResponseStatusException(BAD_REQUEST, "Question is required.");
        }
        validateAudioForPublication(PublishStatus.PUBLISHED, question.getSkill(), question.getAudioText());
        List<LevelCheckQuestionData.Option> options = questionOptions(question);
        validateOptions(options);
        question.setPublishStatus(PublishStatus.PUBLISHED);
        auditQuestion(admin, QUESTION_PUBLISH, question, "Admin published a level check question.");
        return toQuestionDetail(question, options);
    }

    /**
     * Back to a draft (the Undo of Publish): new learners are not asked it any more. A question that is a draft already
     * stays as it is.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LevelCheckQuestionData.Detail unpublishQuestion(SecurityUserDetails admin, Long id) {
        LevelCheckQuestion question = requireQuestion(levelCheckQuestionRepository.findLockedById(id));
        if (question.getPublishStatus() == PublishStatus.PUBLISHED) {
            question.setPublishStatus(PublishStatus.DRAFT);
            auditQuestion(admin, QUESTION_UNPUBLISH, question, "Admin unpublished a level check question.");
        }
        return toQuestionDetail(question);
    }

    private void auditQuestion(SecurityUserDetails admin, String action, LevelCheckQuestion question, String description) {
        auditLogService.record(admin, action, QUESTION_TARGET, question.getId(), description);
    }

    private LevelCheckQuestion write(LevelCheckQuestion question, LevelCheckQuestionData.Request request) {
        validateOptions(request.options());
        String audioText = TextUtils.trimToNull(request.audioText());
        validateAudioForPublication(request.status(), request.skill(), audioText);
        question.setPrompt(TextUtils.stripFormSpaces(request.prompt()));
        question.setHanzi(TextUtils.trimToNull(request.hanzi()));
        question.setPinyin(TextUtils.trimToNull(request.pinyin()));
        question.setAudioText(audioText);
        question.setSkill(request.skill());
        question.setDifficulty(request.difficulty());
        question.setMandarinLevel(request.level());
        question.setPublishStatus(request.status());
        LevelCheckQuestion saved = levelCheckQuestionRepository.save(question);
        // A student part-way through the level check keeps the answers already given when a question is corrected.
        ChildRows.writeByPosition(levelCheckOptionRepository.findByQuestionIdOrderByPositionAscIdAsc(saved.getId()),
                request.options(), LevelCheckOption::new, levelCheckOptionRepository, (option, position, data) -> {
                    option.setQuestion(saved);
                    option.setPosition(position);
                    option.setOptionText(TextUtils.stripFormSpaces(data.text()));
                    option.setCorrect(data.correct());
                    option.setExplanation(TextUtils.trimToNull(data.explanation()));
                });
        return saved;
    }

    private static void validateOptions(List<LevelCheckQuestionData.Option> options) {
        if (options.size() < 2 || options.size() > 6) {
            throw new ResponseStatusException(BAD_REQUEST, "A question needs 2 to 6 answers.");
        }
        if (options.stream().filter(LevelCheckQuestionData.Option::correct).count() != 1) {
            throw new ResponseStatusException(BAD_REQUEST, "Tick exactly one right answer.");
        }
        Set<String> labels = new HashSet<>();
        for (LevelCheckQuestionData.Option option : options) {
            String label = TextUtils.stripFormSpaces(option.text());
            if (label.isEmpty()) {
                throw new ResponseStatusException(BAD_REQUEST, "Every answer needs text.");
            }
            if (!labels.add(label)) {
                throw new ResponseStatusException(BAD_REQUEST, "The answers of a question must all be different.");
            }
        }
    }

    private static void validateAudioForPublication(PublishStatus status, LevelCheckSkill skill, String audioText) {
        if (status == PublishStatus.PUBLISHED && skill == LevelCheckSkill.LISTENING && TextUtils.isBlankOrSpaces(audioText)) {
            throw new ResponseStatusException(BAD_REQUEST, "Every listening question needs the Chinese that is read aloud.");
        }
    }

    private static LearnUnit requireUnit(Optional<LearnUnit> unit) {
        return unit.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "This unit is not on the learning path."));
    }

    private static LevelCheckQuestion requireQuestion(Optional<LevelCheckQuestion> question) {
        return question.orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Level check question not found."));
    }

    private AdminLearningPathResponse.UnitDetail toDetail(LearnUnit unit) {
        List<LearnLesson> lessons = lessonRepository.findByUnitIdOrderByPositionAsc(unit.getId());
        int lessonCount = (int) lessons.stream().filter(lesson -> lesson.getLessonKind().countsAsLesson()).count();
        int bonusCount = (int) lessons.stream().filter(lesson -> lesson.getLessonKind() == LearnLessonKind.BONUS).count();
        return new AdminLearningPathResponse.UnitDetail(
                toRow(unit, lessonCount, bonusCount, completionRepository.countStudentsInUnit(unit.getId())),
                lessons.stream()
                        .map(lesson -> new AdminLearningPathResponse.StopRow(
                                lesson.getId(),
                                lesson.getTitle(),
                                lesson.getLessonKind(),
                                lesson.getMinutes(),
                                lesson.getXpReward()))
                        .toList());
    }

    private static AdminLearningPathResponse.UnitRow toRow(
            LearnUnit unit, int lessonCount, int bonusCount, long studentsStarted) {
        return new AdminLearningPathResponse.UnitRow(
                unit.getId(),
                unit.getPosition(),
                unit.getTitle(),
                unit.getTitleZh(),
                unit.getHskLevel(),
                lessonCount,
                bonusCount,
                studentsStarted,
                unit.getPublishStatus());
    }

    private static LevelCheckQuestionData.Row toQuestionRow(LevelCheckQuestion question) {
        return new LevelCheckQuestionData.Row(
                question.getId(),
                question.getPrompt(),
                question.getSkill(),
                question.getDifficulty(),
                question.getMandarinLevel(),
                question.getPublishStatus());
    }

    private LevelCheckQuestionData.Detail toQuestionDetail(LevelCheckQuestion question) {
        return toQuestionDetail(question, questionOptions(question));
    }

    private List<LevelCheckQuestionData.Option> questionOptions(LevelCheckQuestion question) {
        return levelCheckOptionRepository
                .findByQuestionIdOrderByPositionAscIdAsc(question.getId()).stream()
                .map(option -> new LevelCheckQuestionData.Option(
                        option.getOptionText(), option.isCorrect(), option.getExplanation()))
                .toList();
    }

    private static LevelCheckQuestionData.Detail toQuestionDetail(
            LevelCheckQuestion question, List<LevelCheckQuestionData.Option> options) {
        int answered = question.getAnsweredCount();
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
                answered,
                answered == 0 ? null : RewardRules.accuracyPercent(question.getCorrectCount(), answered),
                options);
    }
}
