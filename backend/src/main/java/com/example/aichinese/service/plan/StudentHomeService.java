package com.example.aichinese.service.plan;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.learn.LearnPathResponse;
import com.example.aichinese.dto.plan.HomeView;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.LearningPlan;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.LearnLessonKind;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.repository.LearnQuestionRepository;
import com.example.aichinese.repository.LearningPlanRepository;
import com.example.aichinese.repository.LearningPlanStepRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.LearnPathModel.StopState;
import com.example.aichinese.service.learn.XpService;

/**
 * The student Home in one request. The next lesson is the next open stop of the Learn path, which the plan always
 * follows (its Learn steps are path units in order); its key phrase is a sentence from the lesson itself.
 */
@Service
public class StudentHomeService {

    private final StudentLearningAccessService accessService;
    private final LearningPlanRepository planRepository;
    private final LearningPlanStepRepository stepRepository;
    private final PlanViewAssembler assembler;
    private final XpService xpService;
    private final LearnQuestionRepository questionRepository;
    private final EmailConfirmationService emailConfirmationService;

    public StudentHomeService(
            StudentLearningAccessService accessService,
            LearningPlanRepository planRepository,
            LearningPlanStepRepository stepRepository,
            PlanViewAssembler assembler,
            XpService xpService,
            LearnQuestionRepository questionRepository,
            EmailConfirmationService emailConfirmationService) {
        this.accessService = accessService;
        this.planRepository = planRepository;
        this.stepRepository = stepRepository;
        this.assembler = assembler;
        this.xpService = xpService;
        this.questionRepository = questionRepository;
        this.emailConfirmationService = emailConfirmationService;
    }

    @Transactional(readOnly = true)
    public HomeView home(SecurityUserDetails currentUser) {
        StudentProfile student = accessService.requireStudent(currentUser);
        User user = student.getUser();
        PlanViewAssembler.Live live = assembler.live(student, currentUser);
        Optional<LearningPlan> plan = planRepository.findFirstByStudentIdAndActiveTrueOrderByVersionDesc(student.getId());
        PlanViews.Summary summary = plan.map(found -> assembler.summary(student, found, live)).orElse(null);
        boolean quickReviewUnit = plan.isPresent() && live.catalogue().path().nextLesson() != null
                && stepRepository.findByPlanIdOrderByPositionAsc(plan.get().getId()).stream()
                        .anyMatch(step -> step.isQuickReview() && step.getStepKind() == PlanStepKind.LEARN_UNIT
                                && step.getRefId().equals(live.catalogue().path().nextLesson().unitId()));
        int totalXp = student.getTotalPoints() == null ? 0 : student.getTotalPoints();
        return new HomeView(
                firstName(user.getDisplayName()),
                xpService.today(student),
                totalXp == 0 && live.catalogue().path().completedLessons() == 0,
                emailConfirmationService.status(user),
                student.getPlanSetupState(),
                next(live, quickReviewUnit),
                live.liveToday(),
                summary);
    }

    private HomeView.NextLesson next(PlanViewAssembler.Live live, boolean quickReview) {
        LearnPathResponse.NextLesson next = live.catalogue().path().nextLesson();
        if (next == null) {
            return checkpoint(live.catalogue().path());
        }
        Optional<PlanCatalogue.Unit> unit = live.catalogue().unit(next.unitId());
        List<HomeView.Stop> stops = unit.map(found -> found.lessons().stream()
                        .map(stop -> new HomeView.Stop(stop.id(), stop.position(), stop.state(), stop.id().equals(next.id())))
                        .toList())
                .orElse(List.of());
        int completed = (int) stops.stream().filter(stop -> stop.state() == StopState.DONE).count();
        HomeView.Phrase phrase = keyPhrase(next.id());
        return new HomeView.NextLesson(
                next.id(),
                next.title(),
                next.kind(),
                next.unitNumber(),
                next.unitTitle(),
                next.unitTitleZh(),
                next.position(),
                next.unitLessonCount(),
                completed,
                next.minutes(),
                next.xpReward(),
                firstHanzi(phrase == null ? next.unitTitleZh() : phrase.hanzi()),
                phrase,
                stops,
                quickReview,
                unit.map(PlanCatalogue.Unit::remainingMinutes).orElse(next.minutes()));
    }

    /**
     * Every lesson of a level is done and its checkpoint is open: the checkpoint is next (about a minute a question).
     * Null when the whole path is finished.
     */
    private static HomeView.NextLesson checkpoint(LearnPathResponse path) {
        for (LearnPathResponse.Level level : path.levels()) {
            LearnPathResponse.Checkpoint checkpoint = level.checkpoint();
            if (checkpoint == null || checkpoint.state() != StopState.AVAILABLE || level.units().isEmpty()) {
                continue;
            }
            LearnPathResponse.Unit unit = level.units().get(level.units().size() - 1);
            List<HomeView.Stop> stops = unit.lessons().stream()
                    .map(stop -> new HomeView.Stop(stop.id(), stop.position(), stop.state(), false))
                    .toList();
            int minutes = Math.max(5, checkpoint.questionCount());
            return new HomeView.NextLesson(checkpoint.id(), checkpoint.title(), LearnLessonKind.CHECKPOINT,
                    unit.number(), unit.title(), unit.titleZh(), unit.lessonCount() + 1, unit.lessonCount(),
                    unit.completedCount(), minutes, checkpoint.xpReward(), firstHanzi(unit.titleZh()), null, stops,
                    false, minutes);
        }
        return null;
    }

    /** The shortest full sentence the lesson teaches (with its meaning), else its first word with a meaning. */
    HomeView.Phrase keyPhrase(Long lessonId) {
        List<LearnQuestion> questions = questionRepository.findByLessonIdOrderByPositionAsc(lessonId).stream()
                .filter(question -> question.getHanzi() != null && !question.getHanzi().isBlank())
                .filter(question -> question.getMeaning() != null && !question.getMeaning().isBlank())
                .toList();
        return questions.stream()
                .filter(question -> isSentence(question.getHanzi()))
                .min(Comparator.comparingInt((LearnQuestion question) -> question.getHanzi().length()))
                .or(() -> questions.stream().findFirst())
                .map(question -> new HomeView.Phrase(question.getHanzi(), question.getMeaning()))
                .orElse(null);
    }

    private static boolean isSentence(String hanzi) {
        String text = hanzi.strip();
        return text.endsWith("。") || text.endsWith("！") || text.endsWith("？");
    }

    static String firstHanzi(String text) {
        if (text == null) {
            return null;
        }
        return text.codePoints()
                .filter(codePoint -> Character.UnicodeScript.of(codePoint) == Character.UnicodeScript.HAN)
                .mapToObj(Character::toString)
                .findFirst()
                .orElse(null);
    }

    static String firstName(String displayName) {
        if (displayName == null || displayName.isBlank()) {
            return "";
        }
        return displayName.strip().split("\\s+")[0];
    }
}
