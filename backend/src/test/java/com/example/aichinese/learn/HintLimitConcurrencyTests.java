package com.example.aichinese.learn;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.junit.jupiter.api.AutoClose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.SecurityProperties;
import com.example.aichinese.dto.learn.HintDto;
import com.example.aichinese.dto.learn.LessonPlayResponse;
import com.example.aichinese.dto.learn.PracticeSetResponse;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.service.learn.LearnPathService;
import com.example.aichinese.service.learn.PracticeCatalog;
import com.example.aichinese.service.learn.PracticeService;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * A student's hint requests are serialised on their profile row: two requests at once for different items of one run
 * cannot both read a count under the level's hints per set. The earlier request holds its transaction open after its
 * hint was given; the later one must wait for it, then see its hint and be refused.
 */
class HintLimitConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private LearnPathService learnPathService;

    @Autowired
    private PracticeService practiceService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private SecurityProperties security;

    @AutoClose
    private HintRules rules;
    private StudentProfile student;

    @BeforeEach
    void oneHintASet() throws Exception {
        rules = HintRules.saved(mockMvc, api);
        rules.setHsk1(1);
        String email = uniqueEmail("hint-race");
        api.registerStudentAccount(email);
        student = studentProfile(email);
    }

    @Test
    void twoLessonHintsAtOnceStayWithinTheHintsPerSet() throws Exception {
        LearnLesson lesson = learnLesson("The four tones · 声调");
        List<LearnQuestion> questions = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId());
        LessonPlayResponse run = learnPathService.lesson(student, lesson.getId());
        Function<LearnQuestion, HintDto.QuestionRequest> request = question ->
                new HintDto.QuestionRequest(question.getId(), run.runStartedAt(), run.runSignature());

        race(() -> learnPathService.hint(student, lesson.getId(), request.apply(questions.get(0))),
                () -> learnPathService.hint(student, lesson.getId(), request.apply(questions.get(1))));

        assertThat(hintedItems("LESSON#" + lesson.getId() + "#%")).isEqualTo(1);
    }

    @Test
    void aHintDeliveredBeforeTheUpgradeStillCountsInTheOpenLesson() throws Exception {
        LearnLesson lesson = learnLesson("The four tones · 声调");
        List<LearnQuestion> questions = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId());
        LocalDateTime opened = LocalDateTime.now().minusMinutes(1).withNano(0);
        String scope = "LESSON#" + lesson.getId() + "#";
        jdbc.update("""
                insert into served_hints (student_id, item_ref, step, created_at, updated_at)
                values (?, ?, 1, ?, ?)
                """, student.getId(), "LESSON#" + lesson.getId() + "#" + questions.getFirst().getId(),
                Timestamp.valueOf(opened), Timestamp.valueOf(opened));

        var second = new HintDto.QuestionRequest(questions.get(1).getId(), opened, preUpgradeSignature(scope, opened));
        assertThatThrownBy(() -> learnPathService.hint(student, lesson.getId(), second))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        error -> assertThat(error.getReason()).isEqualTo("There are no hints left in this set."));
        LessonPlayResponse next = learnPathService.lesson(student, lesson.getId());
        assertThat(learnPathService.hint(student, lesson.getId(), new HintDto.QuestionRequest(
                questions.get(1).getId(), next.runStartedAt(), next.runSignature()))).isNotNull();
    }

    /** Produce the wire format shipped before V9 for the test learner's actual ID and lesson. */
    private String preUpgradeSignature(String scope, LocalDateTime start) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(security.secret().getBytes(StandardCharsets.UTF_8), mac.getAlgorithm()));
        byte[] key = mac.doFinal("MandarinSpark hint run v1".getBytes(StandardCharsets.UTF_8));
        mac.init(new SecretKeySpec(key, mac.getAlgorithm()));
        String payload = student.getId() + "\n" + scope + "\n" + start.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void twoPracticeHintsAtOnceStayWithinTheHintsPerSet() throws Exception {
        PracticeActivityContent set = practiceSet(PracticeType.LISTENING, "Asking the Way");
        List<PracticeCatalog.Item> items = practiceCatalog.content(PracticeType.LISTENING, set).items();
        PracticeSetResponse run = practiceService.set(student, PracticeType.LISTENING, set.getId());
        Function<PracticeCatalog.Item, HintDto.Request> request = item ->
                new HintDto.Request(item.key(), run.runStartedAt(), run.runSignature());

        race(() -> practiceService.hint(student, PracticeType.LISTENING, set.getId(), request.apply(items.get(0))),
                () -> practiceService.hint(student, PracticeType.LISTENING, set.getId(), request.apply(items.get(1))));

        assertThat(hintedItems("PRACTICE#LISTENING#" + set.getId() + "#%")).isEqualTo(1);
    }

    /**
     * The earlier hint is given and its transaction kept open; the later one starts meanwhile. With the student's
     * row locked the later one waits until the earlier commits, then counts its hint and is refused; without the lock
     * it would count none and be given too.
     */
    private void race(Supplier<HintDto.Step> earlier, Supplier<HintDto.Step> later) throws Exception {
        var transaction = new TransactionTemplate(transactionManager);
        withRowLock(jdbc, "student_profiles", transaction, () -> assertThat(earlier.get()).isNotNull(),
                () -> assertThatThrownBy(later::get).isInstanceOfSatisfying(ResponseStatusException.class, refused -> {
                    assertThat(refused.getStatusCode().value()).isEqualTo(400);
                    assertThat(refused.getReason()).isEqualTo("There are no hints left in this set.");
                }));
    }

    /** The different items of the student's run that got a hint. */
    private long hintedItems(String setRefPrefix) {
        return jdbc.queryForObject(
                "select count(distinct item_ref) from served_hints where student_id = ? and item_ref like ?",
                Long.class, student.getId(), setRefPrefix);
    }

}
