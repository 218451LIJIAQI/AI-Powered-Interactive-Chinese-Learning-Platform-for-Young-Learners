package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.reset;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.aichinese.dto.learn.LessonCompleteRequest;
import com.example.aichinese.dto.learn.PracticeCompleteRequest;
import com.example.aichinese.dto.learn.PracticeItemAnswer;
import com.example.aichinese.dto.learn.QuestionAnswer;
import com.example.aichinese.dto.learn.XpResult;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;

class LearningRewardsConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private LearnPathService learnPathService;

    @Autowired
    private PracticeService practiceService;

    @MockitoSpyBean
    private XpService xpService;

    @Test
    void overlappingLessonCompletionsAwardXpOnce() throws Exception {
        StudentProfile student = newStudent();
        LearnLesson lesson = learnLesson("The four tones · 声调");
        List<QuestionAnswer> answers = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId()).stream()
                .map(question -> new QuestionAnswer(question.getId(), question.getAnswer()))
                .toList();
        LessonCompleteRequest request = new LessonCompleteRequest(answers);

        assertSerializedOnProfileLock(student, () -> learnPathService.complete(student, lesson.getId(), request).xp());
    }

    @Test
    void overlappingPracticeCompletionsAwardXpOnce() throws Exception {
        StudentProfile student = newStudent();
        PracticeType type = PracticeType.LISTENING;
        PracticeActivityContent set = practiceSet(type, "Asking the Way");
        List<PracticeItemAnswer> answers = practiceCatalog.content(type, set).items().stream()
                .map(item -> new PracticeItemAnswer(item.key(), item.gradable().answer(), null, false))
                .toList();
        PracticeCompleteRequest request = new PracticeCompleteRequest(answers, null);

        assertSerializedOnProfileLock(student, () -> practiceService.complete(student, type, set.getId(), request).xp());
    }

    /** Practice reads its set before locking the profile; READ_COMMITTED must still see the first completion. */
    @Test
    void delayedPracticeCompletionAfterPreLockReadAwardsXpOnce() throws Exception {
        StudentProfile student = newStudent();
        PracticeType type = PracticeType.LISTENING;
        PracticeActivityContent set = practiceSet(type, "Asking the Way");
        List<PracticeItemAnswer> answers = practiceCatalog.content(type, set).items().stream()
                .map(item -> new PracticeItemAnswer(item.key(), item.gradable().answer(), null, false))
                .toList();
        PracticeCompleteRequest request = new PracticeCompleteRequest(answers, null);

        AtomicReference<XpResult> delayedResult = new AtomicReference<>();
        AtomicReference<XpResult> firstResult = new AtomicReference<>();
        try {
            withPausedOperation(pause -> {
                Thread delayed = Thread.currentThread();
                doAnswer(invocation -> {
                    if (Thread.currentThread() == delayed) {
                        pause.run();
                    }
                    return invocation.callRealMethod();
                }).when(xpService).begin(any(StudentProfile.class));
                delayedResult.set(practiceService.complete(student, type, set.getId(), request).xp());
            }, () -> firstResult.set(practiceService.complete(student, type, set.getId(), request).xp()));
        } finally {
            reset(xpService);
        }

        assertSingleAward(student, firstResult.get(), delayedResult.get());
    }

    private StudentProfile newStudent() throws Exception {
        String email = uniqueEmail("xp-overlap");
        api.registerStudentAccount(email);
        return studentProfile(email);
    }

    /** Holds the first completion's student_profiles row lock until the competing call blocks on it in InnoDB. */
    private void assertSerializedOnProfileLock(StudentProfile student, Supplier<XpResult> complete) throws Exception {
        AtomicReference<XpResult> firstResult = new AtomicReference<>();
        XpResult waitingResult;
        try {
            waitingResult = withRowLock(jdbc, "student_profiles", pause -> {
                Thread first = Thread.currentThread();
                doAnswer(invocation -> {
                    Object session = invocation.callRealMethod();
                    if (Thread.currentThread() == first) {
                        pause.run();
                    }
                    return session;
                }).when(xpService).begin(any(StudentProfile.class));
                firstResult.set(complete.get());
            }, complete::get);
        } finally {
            reset(xpService);
        }

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
