package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.assertj.core.api.Assertions.within;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.LessonViews.QuizResult;
import com.example.aichinese.repository.StudentBadgeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.service.classes.ClassQuizService;
import com.example.aichinese.service.classes.StudentClassActivityService;
import com.example.aichinese.service.classes.StudentClassService;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * Overlapping class requests from one learner. The first request holds its transaction open after taking the student
 * lock so the second must wait on {@code student_profiles} and recheck enrolment and existing progress after commit.
 */
class ClassProgressConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private ClassQuizService quizzes;

    @Autowired
    private StudentClassService classes;

    @Autowired
    private StudentClassActivityService activities;

    @Autowired
    private StudentBadgeRepository studentBadgeRepository;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void concurrentFirstJoinsAndFirstVideoViewsEachKeepOneRecord() throws Exception {
        String email = uniqueEmail("class-first-join-watch");
        api.registerStudentAccount(email);
        var published = api.publishedClass(api.adminToken(), "First join and view " + uniqueSuffix());
        var learner = principalFor(email);
        long studentId = studentProfile(email).getId();

        Supplier<Boolean> join = () -> {
            classes.join(learner, published.classId());
            return classes.classPage(learner, published.classId()).joined();
        };
        assertThat(whileTheFirstHoldsTheLock(join, join)).containsExactly(true, true);
        assertThat(jdbc.queryForObject("select count(*) from class_enrolments where student_id = ? and course_package_id = ?",
                Long.class, studentId, published.classId())).isOne();

        Supplier<Boolean> watch = () -> {
            classes.markVideoWatched(learner, published.lessonId());
            return classes.lesson(learner, published.lessonId()).video().watched();
        };
        assertThat(whileTheFirstHoldsTheLock(watch, watch)).containsExactly(true, true);
        assertThat(jdbc.queryForObject("select count(*) from video_progress where student_id = ? and lesson_id = ?",
                Long.class, studentId, published.lessonId())).isOne();
    }

    @Test
    void joinWaitingForAnArchiveCannotEnrolANewLearner() throws Exception {
        String email = uniqueEmail("student-join-during-class-archive");
        api.registerStudentAccount(email);
        long classId = api.publishedClass(api.adminToken(), "Archive during join " + uniqueSuffix()).classId();
        var learner = principalFor(email);
        long studentId = studentProfile(email).getId();

        withRowLock(jdbc, "course_packages", new TransactionTemplate(transactions), () -> jdbc.update("""
                update course_packages set status_before_archive = content_status, content_status = 'ARCHIVED',
                       archived_at = current_timestamp where id = ?
                """, classId), () -> assertThatExceptionOfType(ResponseStatusException.class)
                .isThrownBy(() -> classes.join(learner, classId))
                .satisfies(error -> assertThat(error.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)));
        assertThat(jdbc.queryForObject("select count(*) from class_enrolments where student_id = ? and course_package_id = ?",
                Long.class, studentId, classId)).isZero();
    }

    @Test
    void activityCompletionWaitingForALeaveMustRecheckEnrolment() throws Exception {
        String email = uniqueEmail("leave-during-activity");
        String token = api.registerStudent(email);
        var published = api.publishedClass(api.adminToken(), "Leave during activity " + uniqueSuffix());
        api.join(token, published.classId());
        var learner = principalFor(email);
        long studentId = studentProfile(email).getId();
        var flashcards = classes.lesson(learner, published.lessonId()).activities().stream()
                .filter(step -> step.type().equals("FLASHCARDS")).findFirst().orElseThrow();

        whileTheFirstHoldsTheLock(() -> {
            classes.leave(learner, published.classId());
            return true;
        }, () -> {
            assertThatExceptionOfType(ResponseStatusException.class)
                    .isThrownBy(() -> activities.complete(learner, flashcards.type(), flashcards.id()))
                    .satisfies(failure -> {
                        assertThat(failure.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
                        assertThat(failure.getReason()).isEqualTo("Join this class to open its lessons.");
                    });
            return true;
        });

        assertThat(classes.classPage(learner, published.classId()).joined()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from class_activity_completions where student_id = ? and lesson_id = ?",
                Long.class, studentId, published.lessonId())).isZero();
        assertThat(jdbc.queryForObject("select count(*) from lesson_progress where student_id = ? and lesson_id = ?",
                Long.class, studentId, published.lessonId())).isZero();
    }

    @Test
    void anAttemptSubmittedTwiceAtOnceIsSubmittedOnce() throws Exception {
        String email = uniqueEmail("quiz-double-submit");
        String learnerToken = api.registerStudent(email);
        long quizId = api.publishedClass(api.adminToken(), "Double submit " + uniqueSuffix()).quizId();
        long attemptId = api.answerQuiz(learnerToken, quizId, true, "hǎo");
        SecurityUserDetails learner = principalFor(email);

        List<QuizResult> results = whileTheFirstHoldsTheLock(
                () -> quizzes.submit(learner, attemptId), () -> quizzes.submit(learner, attemptId));

        assertThat(results).extracting(QuizResult::attemptId).containsOnly(attemptId);
        assertThat(results).extracting(QuizResult::percent).containsOnly(100);
        // Both requests return the first submission time, rounded on its way through the database.
        assertThat(results.get(1).submittedAt()).isCloseTo(results.get(0).submittedAt(), within(1, ChronoUnit.MILLIS));
    }

    @Test
    void twoQuizzesThatEarnTheFirstQuizStarAtOnceAreBothSubmitted() throws Exception {
        String email = uniqueEmail("quiz-star-race");
        String learnerToken = api.registerStudent(email);
        String adminToken = api.adminToken();
        long firstAttempt = api.answerQuiz(learnerToken,
                api.publishedClass(adminToken, "Quiz Star race A " + uniqueSuffix()).quizId(), true, "hǎo");
        long secondAttempt = api.answerQuiz(learnerToken,
                api.publishedClass(adminToken, "Quiz Star race B " + uniqueSuffix()).quizId(), true, "hǎo");
        SecurityUserDetails learner = principalFor(email);

        List<QuizResult> results = whileTheFirstHoldsTheLock(
                () -> quizzes.submit(learner, firstAttempt), () -> quizzes.submit(learner, secondAttempt));

        assertThat(results).extracting(QuizResult::percent).containsOnly(100);
        assertThat(studentBadgeRepository.existsByStudentIdAndBadgeName(
                studentProfile(email).getId(), RewardBadge.QUIZ_STAR.badgeName())).isTrue();
    }

    /**
     * Releases the first request when the database shows the second waiting on the learner's lock.
     * Returns the results in request order.
     */
    private <T> List<T> whileTheFirstHoldsTheLock(Supplier<T> first, Supplier<T> second)
            throws Exception {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        AtomicReference<T> firstResult = new AtomicReference<>();
        T secondResult = withRowLock(jdbc, "student_profiles", transaction,
                () -> firstResult.set(first.get()), second::get);
        return List.of(firstResult.get(), secondResult);
    }
}
