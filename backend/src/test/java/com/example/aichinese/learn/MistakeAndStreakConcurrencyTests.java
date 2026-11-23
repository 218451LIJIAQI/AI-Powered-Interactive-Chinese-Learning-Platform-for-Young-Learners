package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.pauseAfterLookup;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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

    @MockitoSpyBean
    private LearningMistakeRepository mistakeRepository;

    @MockitoSpyBean
    private DailyStreakRepository streakRepository;

    @Test
    void twoFirstWrongAnswersForTheSameLearnerAndQuestionCountTwiceOnOneMistake() throws Exception {
        StudentProfile student = newLearner();
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        QuestionAnswer wrong = new QuestionAnswer(firstQuestion(lesson).getId(), "wrong");
        boolean secondCorrect = withRowLock(jdbc, "student_profiles", pause -> {
            pauseAfterLookup(pause).when(mistakeRepository)
                    .findLockedByStudentIdAndItemRef(anyLong(), anyString());
            assertThat(learnPathService.check(student, lesson.getId(), wrong).correct()).isFalse();
        }, () -> learnPathService.check(student, lesson.getId(), wrong).correct());
        assertThat(secondCorrect).isFalse();
        assertThat(jdbc.queryForList("select wrong_count from learning_mistakes where student_id = ?", Integer.class, student.getId()))
                .containsExactly(2);
    }

    @Test
    void twoLearnersFirstWrongPracticeAnswersAtOnceAreBothKept() throws Exception {
        StudentProfile first = newLearner();
        StudentProfile second = newLearner();
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "At the Café");
        String itemKey = practiceCatalog.content(PracticeType.LISTENING, set).items().get(0).key();
        PracticeItemAnswer wrong = new PracticeItemAnswer(itemKey, "wrong", null, false);

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(mistakeRepository)
                    .findLockedByStudentIdAndItemRef(anyLong(), anyString());
            practiceService.check(first, PracticeType.LISTENING, set.getId(), wrong);
        }, () -> practiceService.check(second, PracticeType.LISTENING, set.getId(), wrong));

        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(first.getId())).isEqualTo(1);
        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(second.getId())).isEqualTo(1);
    }

    @Test
    void twoLearnersFirstWrongLessonAnswersAtOnceAreBothKept() throws Exception {
        StudentProfile first = newLearner();
        StudentProfile second = newLearner();
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        QuestionAnswer wrong = new QuestionAnswer(firstQuestion(lesson).getId(), "wrong");

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(mistakeRepository)
                    .findLockedByStudentIdAndItemRef(anyLong(), anyString());
            learnPathService.check(first, lesson.getId(), wrong);
        }, () -> learnPathService.check(second, lesson.getId(), wrong));

        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(first.getId())).isEqualTo(1);
        assertThat(mistakeRepository.countByStudentIdAndClearedAtIsNull(second.getId())).isEqualTo(1);
    }

    /** Lowering the goal to the XP already earned today reaches it: the bonus, and a learner's first streak day. */
    @Test
    void twoLearnersReachingTheirFirstGoalByLoweringItAtOnceBothStartAStreak() throws Exception {
        StudentProfile first = learnerWithTenXpToday();
        StudentProfile second = learnerWithTenXpToday();

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(streakRepository).findLockedByStudentId(anyLong());
            xpService.changeDailyGoal(first, 10);
        }, () -> xpService.changeDailyGoal(second, 10));

        assertThat(streakRepository.findByStudentId(first.getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
        assertThat(streakRepository.findByStudentId(second.getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
    }

    /** With a goal of 10 XP, the review's 10 XP reaches it: a learner's first streak day. */
    @Test
    void twoLearnersReachingTheirFirstGoalInAReviewAtOnceBothStartAStreak() throws Exception {
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        LearnQuestion question = firstQuestion(lesson);
        StudentProfile first = learnerWithAMistakeAndAGoalOfTen(lesson, question);
        StudentProfile second = learnerWithAMistakeAndAGoalOfTen(lesson, question);
        MistakeReviewCompleteRequest firstReview = rightAnswerToTheMistake(first, question);
        MistakeReviewCompleteRequest secondReview = rightAnswerToTheMistake(second, question);

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(streakRepository).findLockedByStudentId(anyLong());
            mistakeService.completeReview(first, firstReview);
        }, () -> mistakeService.completeReview(second, secondReview));

        assertThat(streakRepository.findByStudentId(first.getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
        assertThat(streakRepository.findByStudentId(second.getId()).orElseThrow().getCurrentStreak()).isEqualTo(1);
    }

    private StudentProfile newLearner() throws Exception {
        String email = uniqueEmail("first-rows");
        api.registerStudentAccount(email);
        return studentProfile(email);
    }

    /** A new learner with 10 XP today (a flashcard set reviewed), short of the default goal of 20 XP. */
    private StudentProfile learnerWithTenXpToday() throws Exception {
        StudentProfile learner = newLearner();
        PracticeActivityContent cards = practiceSet(PracticeType.FLASHCARDS, "Campus Words");
        List<PracticeItemAnswer> ratings = practiceCatalog.content(PracticeType.FLASHCARDS, cards).items().stream()
                .map(card -> new PracticeItemAnswer(card.key(), "GOT_IT", null, false))
                .toList();
        practiceService.complete(learner, PracticeType.FLASHCARDS, cards.getId(),
                new PracticeCompleteRequest(ratings, null));
        return learner;
    }

    /** A new learner with a goal of 10 XP (none earned yet) and a waiting mistake on {@code question}. */
    private StudentProfile learnerWithAMistakeAndAGoalOfTen(LearnLesson lesson, LearnQuestion question)
            throws Exception {
        StudentProfile learner = newLearner();
        xpService.changeDailyGoal(learner, 10);
        learnPathService.check(learner, lesson.getId(), new QuestionAnswer(question.getId(), "wrong"));
        return learner;
    }

    private MistakeReviewCompleteRequest rightAnswerToTheMistake(StudentProfile learner, LearnQuestion question) {
        Long mistakeId = mistakeService.list(learner, null, 1).page().items().getFirst().id();
        return new MistakeReviewCompleteRequest(List.of(new QuestionAnswer(mistakeId, question.getAnswer())));
    }

    private LearnQuestion firstQuestion(LearnLesson lesson) {
        return learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).get(0);
    }
}
