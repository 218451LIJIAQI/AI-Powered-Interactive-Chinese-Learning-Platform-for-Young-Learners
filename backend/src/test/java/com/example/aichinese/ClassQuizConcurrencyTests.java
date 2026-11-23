package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static com.example.aichinese.support.LearningApiTestClient.uniqueSuffix;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Callable;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.classes.QuizSubmissionAnswerRequest;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.classes.ClassQuizService;
import com.example.aichinese.service.classes.LessonEditorService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;

import jakarta.persistence.EntityManager;

/** Quiz writes from two learner tabs, or from a learner and an admin publishing changed questions. */
class ClassQuizConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private LessonEditorService lessonEditor;

    @Autowired
    private ClassQuizService quizzes;

    @Autowired
    private QuizAttemptAnswerRepository answers;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void discardRemovesAnswersAlreadyManagedInTheTransaction(@Autowired EntityManager entityManager) throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Discard current answers " + uniqueSuffix());
        String email = uniqueEmail("discard-managed");
        var session = api.startQuiz(api.registerStudent(email), published.quizId());
        long attemptId = session.path("attemptId").asLong();
        var question = session.path("questions").get(0);
        var answer = new QuizSubmissionAnswerRequest(question.path("id").asLong(),
                question.path("options").get(0).path("id").asLong(), null);
        SecurityUserDetails learner = principalFor(email);

        readCommitted().executeWithoutResult(status -> {
            quizzes.saveAnswer(learner, attemptId, answer);
            var saved = answers.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attemptId).getFirst();
            assertThat(entityManager.contains(saved)).isTrue();
            quizzes.discard(learner, attemptId);
            entityManager.flush();
        });

        assertThat(jdbc.queryForObject("select count(*) from quiz_attempts where id = ?", Integer.class, attemptId))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from quiz_attempt_answers where quiz_attempt_id = ?",
                Integer.class, attemptId)).isZero();
    }

    @Test
    void concurrentStartsResumeOneAttempt() throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Two tabs " + uniqueSuffix());
        String email = uniqueEmail("two-starts");
        api.join(api.registerStudent(email), published.classId());
        SecurityUserDetails learner = principalFor(email);

        long resumed = whileFirstWriteCommits("student_profiles", () -> quizzes.start(learner, published.quizId()),
                () -> quizzes.start(learner, published.quizId()).attemptId());

        assertThat(jdbc.queryForList("select id from quiz_attempts where student_id = ? and quiz_id = ?",
                Long.class, studentProfile(email).getId(), published.quizId())).containsExactly(resumed);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void aSecondTabCannotEditOrDiscardAnAttemptBeingSubmitted(boolean discard) throws Exception {
        PublishedClass published = api.publishedClass(api.adminToken(), "Submitting " + uniqueSuffix());
        String email = uniqueEmail("submit-race");
        String token = api.registerStudent(email);
        long attemptId = api.answerQuiz(token, published.quizId(), true, "hǎo");
        SecurityUserDetails learner = principalFor(email);
        long questionId = quizzes.start(learner, published.quizId()).questions().stream()
                .filter(question -> "FILL_BLANK".equals(question.type())).findFirst().orElseThrow().id();

        assertThatThrownBy(() -> whileFirstWriteCommits("student_profiles", () -> quizzes.submit(learner, attemptId), () -> {
            if (discard) {
                quizzes.discard(learner, attemptId);
            } else {
                quizzes.saveAnswer(learner, attemptId, new QuizSubmissionAnswerRequest(questionId, null, "late edit"));
            }
            return null;
        })).isInstanceOfSatisfying(ResponseStatusException.class,
                failure -> assertThat(failure.getStatusCode().value()).isEqualTo(409));
        assertThat(quizzes.result(learner, attemptId).questions())
                .anyMatch(question -> question.questionId().equals(questionId)
                        && question.correct() && "hǎo".equals(question.yourAnswer()));
    }

    /**
     * Submitting takes a share lock on the quiz row while publishing takes a write lock before replacing questions.
     * When a learner's submit holds the quiz lock first, publishing waits for the commit, then re-checks that the quiz
     * is now locked and preserves the answered questions cleanly.
     */
    @Test
    void anAttemptSubmittedWhileChangedQuestionsArePublishedKeepsItsAnswers() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Publish race " + uniqueSuffix());
        String email = uniqueEmail("publish-race-learner");
        String learnerToken = api.registerStudent(email);
        long attemptId = api.answerQuiz(learnerToken, published.quizId(), true, "hǎo");
        editThirdQuestion(adminToken, published.lessonId(), "Which word means bye?");
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        SecurityUserDetails learner = principalFor(email);

        whileFirstWriteCommits("quizzes", () -> quizzes.submit(learner, attemptId), () -> {
            lessonEditor.publish(admin, published.lessonId());
            return null;
        });

        assertThat(answers.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attemptId)).hasSize(5);
        mockMvc.perform(api.authGet("/api/v1/students/classes/quiz-attempts/" + attemptId, learnerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctCount").value(5))
                .andExpect(jsonPath("$.data.questions[2].text").value("Which word means goodbye?"))
                .andExpect(jsonPath("$.data.questions[2].yourAnswer").value("再见"));
    }

    /**
     * When publishing acquires the quiz write lock first and replaces questions (clearing open answers), a concurrent
     * submit waits on the quiz lock and sees the new unanswered questions instead of recording a 0-score submission.
     */
    @Test
    void aSubmitWaitingBehindPublishedQuestionReplacementIsRejectedInsteadOfScoringZero() throws Exception {
        String adminToken = api.adminToken();
        PublishedClass published = api.publishedClass(adminToken, "Publish-first race " + uniqueSuffix());
        String email = uniqueEmail("publish-first-learner");
        String learnerToken = api.registerStudent(email);
        long attemptId = api.answerQuiz(learnerToken, published.quizId(), true, "hǎo");
        editThirdQuestion(adminToken, published.lessonId(), "Which word means see you later?");
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        SecurityUserDetails learner = principalFor(email);

        assertThatThrownBy(() -> whileFirstWriteCommits("quizzes",
                () -> lessonEditor.publish(admin, published.lessonId()),
                () -> quizzes.submit(learner, attemptId)))
                .isInstanceOfSatisfying(ResponseStatusException.class, failure -> {
                    assertThat(failure.getStatusCode().value()).isEqualTo(400);
                    assertThat(failure.getReason()).isEqualTo("Answer every question before you submit.");
                });

        assertThat(jdbc.queryForObject(
                "select count(*) from quiz_attempts where id = ? and submitted = true", Integer.class, attemptId))
                .isZero();
        assertThat(answers.findByQuizAttemptIdOrderByCreatedAtAscIdAsc(attemptId)).isEmpty();
    }

    private void editThirdQuestion(String adminToken, long lessonId, String prompt) throws Exception {
        String lesson = "/api/v1/admin/lessons/" + lessonId;
        long thirdQuestion = api.data(mockMvc.perform(api.authGet(lesson, adminToken)).andReturn())
                .path("quizzes").get(0).path("questions").get(2).path("id").asLong();
        Map<String, Object> changed = new HashMap<>(LearningApiTestClient.quizQuestions(null).get(2));
        changed.put("questionText", prompt);
        mockMvc.perform(api.jsonPut(lesson + "/questions/" + thirdQuestion, adminToken, changed))
                .andExpect(status().isOk());
    }

    private <T> T whileFirstWriteCommits(String table, Runnable first, Callable<T> later) throws Exception {
        return withRowLock(jdbc, table, readCommitted(), first, later);
    }

    private TransactionTemplate readCommitted() {
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }
}
