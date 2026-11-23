package com.example.aichinese.service.plan;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.learn.StopState;
import com.example.aichinese.dto.learn.UnitState;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.LearningPlanStep;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.account.EmailConfirmationService;
import com.example.aichinese.service.learn.XpService;

import lombok.RequiredArgsConstructor;

/**
 * The student Home in one request. The next lesson is the next open stop of the Learn path (a level checkpoint once
 * every lesson of its level is done), which the plan always follows (its Learn steps are path units in order), and it
 * says whether it is in the plan (the path goes on past the plan's last unit); its key phrase is a sentence from the
 * lesson itself.
 */
@Service
@RequiredArgsConstructor
public class StudentHomeService {

    private static final Pattern NAME_PART = Pattern.compile("[^\\s\\uFEFF]+", Pattern.UNICODE_CHARACTER_CLASS);
    private final StudentLearningAccessService accessService;
    private final LearningPlanRepository planRepository;
    private final PlanViewAssembler assembler;
    private final XpService xpService;
    private final LearnQuestionRepository questionRepository;
    private final EmailConfirmationService emailConfirmationService;

    @Transactional(readOnly = true)
    public HomeView home(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        User user = student.getUser();
        Optional<LearningPlan> plan = planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(student.getId());
        PlanViewAssembler.Live live = assembler.live(student, plan.orElse(null));
        PlanViews.Summary summary = plan.map(found -> assembler.summary(student, found, live)).orElse(null);
        return new HomeView(
                firstName(user.getDisplayName()),
                xpService.today(student),
                student.getTotalPoints() == 0 && live.catalogue().path().completedLessons() == 0,
                emailConfirmationService.status(user),
                student.getPlanSetupState(),
                next(live),
                summary);
    }

    private HomeView.NextLesson next(PlanViewAssembler.Live live) {
        LearnPathResponse.NextLesson next = live.catalogue().path().nextLesson();
        if (next == null) {
            return checkpoint(live.catalogue(), live.planSteps());
        }
        // The tile says "in your plan" only for a unit the plan has.
        Optional<LearningPlanStep> planned = live.planSteps().stream()
                .filter(step -> step.getStepKind() == PlanStepKind.LEARN_UNIT && step.getRefId().equals(next.unitId()))
                .findFirst();
        // The next lesson comes from the same path as the catalogue, so its unit is always there.
        PlanCatalogue.Unit unit = live.catalogue().unit(next.unitId()).orElseThrow();
        // The unit's lessons, numbered as the path numbers them; an optional bonus chest is not one of them.
        List<HomeView.Stop> stops = unit.lessons().stream()
                .filter(stop -> stop.kind() != LearnLessonKind.BONUS)
                .map(stop -> new HomeView.Stop(stop.id(), stop.position(), stop.state(), stop.id().equals(next.id())))
                .toList();
        HomeView.Phrase phrase = keyPhrase(next.id());
        return new HomeView.NextLesson(
                next.id(),
                next.title(),
                next.kind(),
                unit.level(),
                next.unitNumber(),
                next.unitTitle(),
                next.unitTitleZh(),
                next.minutes(),
                next.xpReward(),
                firstHanzi(phrase == null ? next.unitTitleZh() : phrase.hanzi()),
                phrase,
                stops,
                planned.map(LearningPlanStep::isQuickReview).orElse(false),
                unit.remainingMinutes(),
                planned.isPresent());
    }

    /**
     * Every lesson of a level is done and its checkpoint is open: the checkpoint is next, as on the plan, shown with its
     * level (the level's units as the mini path). It is in the plan while the plan has a unit behind it to do. Null
     * when the whole path is finished.
     */
    private static HomeView.NextLesson checkpoint(PlanCatalogue.Catalogue catalogue, List<LearningPlanStep> planSteps) {
        return PlanViewAssembler.openCheckpoint(catalogue.path()).map(open -> {
            LearnPathResponse.Unit unit = open.lastUnit();
            LearnPathResponse.Checkpoint checkpoint = open.checkpoint();
            List<HomeView.Stop> stops = open.level().units().stream()
                    .map(levelUnit -> new HomeView.Stop(levelUnit.id(), levelUnit.number(), stopState(levelUnit.state()),
                            false))
                    .toList();
            return new HomeView.NextLesson(checkpoint.id(), checkpoint.title(), LearnLessonKind.CHECKPOINT,
                    open.level().level(), unit.number(), unit.title(), unit.titleZh(), open.minutes(),
                    checkpoint.xpReward(), firstHanzi(unit.titleZh()), null, stops, false, open.minutes(),
                    PlanViewAssembler.hasUnitNotDone(planSteps, catalogue));
        }).orElse(null);
    }

    /** A unit of the mini path as a stop: done, open or locked. */
    private static StopState stopState(UnitState state) {
        return switch (state) {
            case DONE -> StopState.DONE;
            case ACTIVE -> StopState.AVAILABLE;
            case LOCKED -> StopState.LOCKED;
        };
    }

    /** The shortest full sentence the lesson teaches (with its meaning), else its first word with a meaning. */
    private HomeView.Phrase keyPhrase(Long lessonId) {
        List<HomeView.Phrase> phrases = questionRepository.findPhrasesByLessonIdOrderByPositionAscIdAsc(lessonId).stream()
                .filter(phrase -> phrase.hanzi() != null && !phrase.hanzi().isBlank())
                .filter(phrase -> phrase.meaning() != null && !phrase.meaning().isBlank())
                .toList();
        return phrases.stream()
                .filter(phrase -> isSentence(phrase.hanzi()))
                .min(Comparator.comparingInt(phrase -> phrase.hanzi().length()))
                .or(() -> phrases.stream().findFirst())
                .orElse(null);
    }

    private static boolean isSentence(String hanzi) {
        String text = hanzi.strip();
        return text.endsWith("。") || text.endsWith("！") || text.endsWith("？");
    }

    private static String firstHanzi(String text) {
        return text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .mapToObj(Character::toString)
                .findFirst()
                .orElse(null);
    }

    private static String firstName(String displayName) {
        var words = NAME_PART.matcher(displayName);
        return words.find() ? words.group() : "";
    }
}
