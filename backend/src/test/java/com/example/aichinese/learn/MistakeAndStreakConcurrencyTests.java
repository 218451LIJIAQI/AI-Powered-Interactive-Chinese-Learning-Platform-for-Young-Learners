package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.withAfterMethod;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.dto.learn.MistakeReviewCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.MistakeService;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * Two learners' first rows written at the same moment. A waiting mistake and a streak are looked up with a lock and
 * inserted when missing; at MySQL's default REPEATABLE READ that lookup locks the index gap where the row would go, and
 * the newest learners' first rows share that gap, so one learner's insert would wait for the other's request and two
 * such requests would deadlock. Here the first learner's request pauses right after its lookup while the second
 * learner's request runs: the second must finish meanwhile, and both rows must be written.
 */
class MistakeAndStreakConcurrencyTests extends ApiIntegrationTest {

    private static final String FIRST_LESSON = "The four tones · 声调";

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private LearnPathService learnPathService;

    @Autowired
    private MistakeService mistakeService;

    @Autowired
    private XpService xpService;

    @Autowired
    private LearningMistakeRepository mistakeRepository;

    @Autowired
    private DailyStreakRepository streakRepository;

    @Test
    void twoFirstWrongAnswersForTheSameLearnerAndQuestionCountTwiceOnOneMistake() throws Exception {
        Learner student = newLearner();
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        QuestionAnswer wrong = new QuestionAnswer(firstQuestion(lesson).getId(), "wrong");
        boolean secondCorrect = withRowLock(jdbc, "student_profiles",
                pause -> withAfterMethod(mistakeRepository, "findLockedByStudentIdAndItemRef", pause, () ->
                        assertThat(learnPathService.check(student.principal(), lesson.getId(), wrong).correct()).isFalse()),
                () -> learnPathService.check(student.principal(), lesson.getId(), wrong).correct());
        assertThat(secondCorrect).isFalse();
        assertThat(jdbc.queryForList("select wrong_count from learning_mistakes where student_id = ?",
                Integer.class, student.profile().getId())).containsExactly(2);
    }

    @Test
    void twoLearnersFirstWrongPracticeAnswersAtOnceAreBothKept() throws Exception {
        Learner first = newLearner();
        Learner second = newLearner();
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        String itemKey = practiceCatalog.content(PracticeType.LISTENING, set).items().get(0).key();
        PracticeItemAnswer wrong = new PracticeItemAnswer(itemKey, "wrong", null, false);

        withPausedOperation(
                pause -> withAfterMethod(mistakeRepository, "findLockedByStudentIdAndItemRef", pause, () ->
                        practiceService.check(first.principal(), PracticeType.LISTENING, set.getId(), wrong)),
                () -> practiceService.check(second.principal(), PracticeType.LISTENING, set.getId(), wrong));

        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(first.profile().getId())).isEqualTo(1);
        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(second.profile().getId())).isEqualTo(1);
    }

    @Test
    void twoLearnersFirstWrongLessonAnswersAtOnceAreBothKept() throws Exception {
        Learner first = newLearner();
        Learner second = newLearner();
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        QuestionAnswer wrong = new QuestionAnswer(firstQuestion(lesson).getId(), "wrong");

        withPausedOperation(
                pause -> withAfterMethod(mistakeRepository, "findLockedByStudentIdAndItemRef", pause, () ->
                        learnPathService.check(first.principal(), lesson.getId(), wrong)),
                () -> learnPathService.check(second.principal(), lesson.getId(), wrong));

        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(first.profile().getId())).isEqualTo(1);
        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(second.profile().getId())).isEqualTo(1);
    }

    /** Lowering the goal to the XP already earned today reaches it: the bonus, and a learner's first streak day. */
    @Test
    void twoLearnersReachingTheirFirstGoalByLoweringItAtOnceBothStartAStreak() throws Exception {
        Learner first = learnerWithTenXpToday();
        Learner second = learnerWithTenXpToday();

        withPausedOperation(
                pause -> withAfterMethod(streakRepository, "findLockedByStudentId", pause, () ->
                        xpService.changeDailyGoal(first.principal(), 10)),
                () -> xpService.changeDailyGoal(second.principal(), 10));

        assertThat(streakRepository.findByStudentId(first.profile().getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
        assertThat(streakRepository.findByStudentId(second.profile().getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
    }

    /** With a goal of 10 XP, the review's 10 XP reaches it: a learner's first streak day. */
    @Test
    void twoLearnersReachingTheirFirstGoalInAReviewAtOnceBothStartAStreak() throws Exception {
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        LearnQuestion question = firstQuestion(lesson);
        Learner first = learnerWithAMistakeAndAGoalOfTen(lesson, question);
        Learner second = learnerWithAMistakeAndAGoalOfTen(lesson, question);
        MistakeReviewCompleteRequest firstReview = rightAnswerToTheMistake(first, question);
        MistakeReviewCompleteRequest secondReview = rightAnswerToTheMistake(second, question);

        withPausedOperation(
                pause -> withAfterMethod(streakRepository, "findLockedByStudentId", pause, () ->
                        mistakeService.completeReview(first.principal(), firstReview)),
                () -> mistakeService.completeReview(second.principal(), secondReview));

        assertThat(streakRepository.findByStudentId(first.profile().getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
        assertThat(streakRepository.findByStudentId(second.profile().getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
    }

    private record Learner(StudentProfile profile, SecurityUserDetails principal) {
    }

    private Learner newLearner() throws Exception {
        String email = uniqueEmail("first-rows");
        api.registerStudentAccount(email);
        return new Learner(studentProfile(email), principalFor(email));
    }

    /** A new learner with 10 XP today (a flashcard set reviewed), short of the default goal of 20 XP. */
    private Learner learnerWithTenXpToday() throws Exception {
        Learner learner = newLearner();
        PracticeActivityContent cards = practiceSet(PracticeType.FLASHCARDS, "Campus Words");
        List<PracticeItemAnswer> ratings = practiceCatalog.content(PracticeType.FLASHCARDS, cards).items().stream()
                .map(card -> new PracticeItemAnswer(card.key(), "GOT_IT", null, false))
                .toList();
        practiceService.complete(learner.principal(), PracticeType.FLASHCARDS, cards.getId(),
                new PracticeCompleteRequest(ratings, null));
        return learner;
    }

    /** A new learner with a goal of 10 XP (none earned yet) and a waiting mistake on {@code question}. */
    private Learner learnerWithAMistakeAndAGoalOfTen(LearnLesson lesson, LearnQuestion question)
            throws Exception {
        Learner learner = newLearner();
        xpService.changeDailyGoal(learner.principal(), 10);
        learnPathService.check(learner.principal(), lesson.getId(), new QuestionAnswer(question.getId(), "wrong"));
        return learner;
    }

    private MistakeReviewCompleteRequest rightAnswerToTheMistake(Learner learner, LearnQuestion question) {
        Long mistakeId = mistakeService.list(learner.principal(), null, 1).page().items().getFirst().id();
        return new MistakeReviewCompleteRequest(List.of(new QuestionAnswer(mistakeId, question.getAnswer())));
    }

    private LearnQuestion firstQuestion(LearnLesson lesson) {
        return learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).get(0);
    }
}
