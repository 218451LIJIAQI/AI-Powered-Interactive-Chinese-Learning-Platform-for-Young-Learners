package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.withAfterMethod;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.dto.learn.LessonCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.dto.learn.XpResult;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;

class LearningRewardsConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private LearnPathService learnPathService;

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private XpService xpService;

    @Autowired
    private StudentLearningAccessService accessService;

    @Test
    void overlappingLessonCompletionsAwardXpOnce() throws Exception {
        Learner student = newStudent();
        LearnLesson lesson = learnLesson("The four tones · 声调");
        List<QuestionAnswer> answers = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).stream()
                .map(question -> new QuestionAnswer(question.getId(), question.getAnswer()))
                .toList();
        LessonCompleteRequest request = new LessonCompleteRequest(answers);

        assertSerializedOnProfileLock(student.profile(),
                () -> learnPathService.complete(student.principal(), lesson.getId(), request).xp());
    }

    @Test
    void overlappingPracticeCompletionsAwardXpOnce() throws Exception {
        Learner student = newStudent();
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "Asking the Way");
        PracticeCompleteRequest request = listeningCompleteRequest(set);

        assertSerializedOnProfileLock(student.profile(),
                () -> practiceService.complete(student.principal(), PracticeType.LISTENING, set.getId(), request).xp());
    }

    /** Practice reads its set before locking the profile; READ_COMMITTED must still see the first completion. */
    @Test
    void delayedPracticeCompletionAfterPreLockReadAwardsXpOnce() throws Exception {
        Learner student = newStudent();
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "Asking the Way");
        PracticeCompleteRequest request = listeningCompleteRequest(set);

        AtomicReference<XpResult> delayedResult = new AtomicReference<>();
        AtomicReference<XpResult> firstResult = new AtomicReference<>();
        withPausedOperation(
                pause -> withAfterMethod(accessService, "requireStudent", pause, () -> delayedResult.set(
                        practiceService.complete(student.principal(), PracticeType.LISTENING, set.getId(), request).xp())),
                () -> firstResult.set(
                        practiceService.complete(student.principal(), PracticeType.LISTENING, set.getId(), request).xp()));

        assertSingleAward(student.profile(), firstResult.get(), delayedResult.get());
    }

    private PracticeCompleteRequest listeningCompleteRequest(PracticeActivityContent set) {
        List<PracticeItemAnswer> answers = practiceCatalog.content(PracticeType.LISTENING, set).items().stream()
                .map(item -> new PracticeItemAnswer(item.key(), item.gradable().answer(), null, false))
                .toList();
        return new PracticeCompleteRequest(answers, null);
    }

    private record Learner(StudentProfile profile, SecurityUserDetails principal) {
    }

    private Learner newStudent() throws Exception {
        String email = uniqueEmail("xp-overlap");
        api.registerStudentAccount(email);
        return new Learner(studentProfile(email), principalFor(email));
    }

    /** Holds the first completion's student_profiles row lock until the competing call blocks on it in InnoDB. */
    private void assertSerializedOnProfileLock(StudentProfile student, Supplier<XpResult> complete) throws Exception {
        AtomicReference<XpResult> firstResult = new AtomicReference<>();
        XpResult waitingResult = withRowLock(jdbc, "student_profiles",
                pause -> withAfterMethod(xpService, "begin", pause, () -> firstResult.set(complete.get())),
                complete::get);

        assertSingleAward(student, firstResult.get(), waitingResult);
    }

    private void assertSingleAward(StudentProfile student, XpResult first, XpResult second) {
        assertThat(first.xpEarned()).isPositive();
        assertThat(second.xpEarned()).isZero();
        assertThat(second.bonusXp()).isZero();
        assertThat(second.todayXp()).isEqualTo(first.todayXp());
        int expectedTotal = first.xpEarned() + first.bonusXp();
        assertThat(jdbc.queryForObject("select total_points from student_profiles where id = ?",
                Integer.class, student.getId())).isEqualTo(expectedTotal);
        assertThat(jdbc.queryForObject("select count(*) from points_transactions where student_id = ? and source_type <> 'DAILY_GOAL'",
                Long.class, student.getId())).isEqualTo(1L);
    }
}
