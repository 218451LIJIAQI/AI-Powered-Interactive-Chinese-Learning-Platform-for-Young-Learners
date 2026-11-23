package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.AiPracticeCheck;
import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.AiUsageQuotaCounter;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.AiResponseReportReason;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.PracticeCheckType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.value.PronunciationFeedback;
import com.example.aichinese.entity.value.PronunciationFeedback.Issue;
import com.example.aichinese.entity.value.PronunciationFeedback.IssueKind;
import com.example.aichinese.entity.value.PronunciationFeedback.ToneComparison;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * AI Helper history of the demo personas, so a demo run looks like the prototype: Amira's two reports (one open, one
 * resolved with an admin note) and Chen Hao's open one, a few usage-log rows across features and roles, and Amira's
 * three practice checks (results only: no recording or photo is ever kept). Dates are relative to today.
 */
@Component
@Order(5)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoAiSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final AiResponseReportRepository reportRepository;
    private final LessonRepository lessonRepository;
    private final EntityManager entityManager;
    private final DemoClock clock;

    @Override
    @Transactional
    public void run(String... args) {
        User amira = userRepository.findByEmailWithRoles(DemoPersonas.STUDENT.email()).orElse(null);
        if (amira == null || reportRepository.count() > 0) {
            return;
        }
        User chenHao = persona(DemoPersonas.CHEN_HAO);
        User nurAisyah = persona(DemoPersonas.NUR_AISYAH);
        User zhangYi = persona(DemoPersonas.ZHANG_YI);
        User admin = userRepository.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);

        report(amira, AiResponseReportReason.INCORRECT_CONTENT, clock.daysAgo(2, 12, 0),
                "How do I introduce myself in Mandarin?",
                "你可以说“我是叫 Amira”。(Nǐ kěyǐ shuō “wǒ shì jiào Amira”.) That means “My name is Amira”.",
                "The answer mixes 我是 and 我叫.", null, null, null);
        report(chenHao, AiResponseReportReason.UNCLEAR_EXPLANATION, clock.daysAgo(3, 9, 20),
                "When do I use 了?",
                "了 (le) shows a change or a finished action, for example 我吃了 (wǒ chī le).",
                "I still don't know when to leave 了 out.", null, null, null);
        report(amira, AiResponseReportReason.UNCLEAR_EXPLANATION, clock.daysAgo(4, 20, 10),
                "What is the difference between 再见 and 拜拜?",
                "再见 (zàijiàn) and 拜拜 (báibái) both mean goodbye.",
                "It doesn't say when to use which one.", admin,
                "Thanks — the answer was too short. SparkBot now explains both.",
                clock.daysAgo(1, 9, 0));

        if (admin != null) {
            // An admin's AI quiz draft in the lesson editor, while the provider timed out.
            log(admin, AiFeatureKey.QUIZ_DRAFT, AiRequestStatus.FAILED, clock.daysAgo(1, 16, 12),
                    "Provider request failed or timed out.");
        }
        List<AiUsageLog> logs = List.of(
                log(amira, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, clock.daysAgo(0, 9, 42), null),
                log(amira, AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS, clock.daysAgo(0, 10, 5), null),
                log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, clock.daysAgo(0, 11, 0), null),
                log(chenHao, AiFeatureKey.QUIZ_GENERATOR, AiRequestStatus.SUCCESS, clock.daysAgo(1, 13, 5), null),
                log(nurAisyah, AiFeatureKey.STORY_GENERATOR, AiRequestStatus.UNAVAILABLE, clock.daysAgo(1, 11, 30),
                        "AI provider is disabled in the current backend configuration."),
                log(zhangYi, AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS, clock.daysAgo(2, 15, 40), null),
                log(amira, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, clock.daysAgo(2, 11, 58), null),
                log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, clock.daysAgo(3, 18, 0), null),
                log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, clock.daysAgo(4, 15, 30), null),
                log(amira, AiFeatureKey.STORY_GENERATOR, AiRequestStatus.SUCCESS, clock.daysAgo(4, 19, 45), null),
                log(chenHao, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, clock.daysAgo(3, 9, 15), null),
                // The SparkBot requests behind the two demo plans, at the times DemoPlanSeeder says they were built.
                log(amira, AiFeatureKey.LEARNING_PLAN, AiRequestStatus.SUCCESS, DemoPlanSeeder.amiraBuiltAt(clock), null),
                log(chenHao, AiFeatureKey.LEARNING_PLAN, AiRequestStatus.UNAVAILABLE, DemoPlanSeeder.chenHaoBuiltAt(clock),
                        "AI provider is disabled in the current backend configuration."));
        countToday(amira, logs);

        Lesson meetingClassmates = lessonRepository.findByClassAndLessonTitle(
                "Mandarin for Campus Life", DemoClassContent.MEETING_CLASSMATES.title()).orElse(null);
        check(amira, meetingClassmates, PracticeCheckType.RECORDING, "你好", 85,
                new PronunciationFeedback(86, 84, 100,
                        List.of(),
                        List.of(new ToneComparison("你", 3, 3), new ToneComparison("好", 3, 3))),
                clock.daysAgo(0, 11, 0),
                "Clear, natural greeting",
                "Both syllables were clear and well-paced. Keep the smooth rising flow from 你 into 好.",
                List.of("你 nǐ — keep the rising tone before another 3rd tone.", "好 hǎo — let the dipping tone finish cleanly."));
        check(amira, meetingClassmates, PracticeCheckType.RECORDING, "谢谢你", 76,
                new PronunciationFeedback(74, 80, 100,
                        List.of(new Issue("谢", IssueKind.TONE_MISMATCH, 4, 2)),
                        List.of(new ToneComparison("谢", 4, 2), new ToneComparison("你", 3, 3))),
                clock.daysAgo(3, 18, 0),
                "Good start — drop the falling tone on 谢",
                "你 sounded clear; let the first 谢 fall sharply from high to low instead of rising.",
                List.of("谢 xiè — start high and drop firmly on the 4th tone.", "Keep 谢谢 smooth before adding 你."));
        check(amira, meetingClassmates, PracticeCheckType.HANDWRITING, "好", 88, null, clock.daysAgo(4, 15, 30),
                "Neat and balanced",
                "The two parts 女 and 子 are both visible. Keep 女 a little narrower.",
                List.of("Leave a small gap between 女 and 子.", "Make the last stroke of 子 a firm horizontal."));
    }

    private User persona(DemoPersonas.Persona persona) {
        return userRepository.findByEmailWithRoles(persona.email()).orElseThrow();
    }

    private void report(
            User student,
            AiResponseReportReason reason,
            LocalDateTime reportedAt,
            String question,
            String answer,
            String note,
            User reviewer,
            String reviewerNote,
            LocalDateTime reviewedAt) {
        AiResponseReport report = new AiResponseReport();
        report.setStudentUser(student);
        report.setFeatureKey(AiFeatureKey.CHAT_TUTOR);
        report.setReasonCategory(reason);
        report.setDescription(note);
        report.setPromptSnapshot(question);
        report.setAiResponseSnapshot(answer);
        report.setCreatedAt(reportedAt);
        report.setUpdatedAt(reportedAt);
        if (reviewer != null) {
            report.setStatus(AiResponseReportStatus.RESOLVED);
            report.setReviewerUser(reviewer);
            report.setReviewerNote(reviewerNote);
            report.setReviewedAt(reviewedAt);
            report.setUpdatedAt(reviewedAt);
        }
        reportRepository.save(report);
    }

    private AiUsageLog log(User user, AiFeatureKey feature, AiRequestStatus status, LocalDateTime when, String error) {
        AiUsageLog entry = new AiUsageLog();
        entry.setUser(user);
        entry.setUserRole(user.requirePrimaryRole().name());
        entry.setFeatureKey(feature);
        entry.setStatus(status);
        entry.setErrorSummary(error);
        entry.setCreatedAt(when);
        entry.setUpdatedAt(when);
        entityManager.persist(entry);
        return entry;
    }

    /**
     * Today's requests of the student, so the "N of 100 requests today" line matches her log rows: those of today's
     * platform day that reached the provider. A "today" time still ahead of the clock is seeded on the day before.
     */
    private void countToday(User student, List<AiUsageLog> logs) {
        LocalDate today = clock.today();
        long requests = logs.stream()
                .filter(entry -> student.getId().equals(entry.getUser().getId()))
                .filter(entry -> entry.getStatus() != AiRequestStatus.UNAVAILABLE)
                .filter(entry -> today.equals(clock.dayOf(entry.getCreatedAt())))
                .count();
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(student);
        counter.setMinuteStartedAt(clock.now().minusHours(1));
        counter.setUsageDay(today);
        counter.setDayCount((int) requests);
        entityManager.persist(counter);
    }

    private void check(
            User student,
            Lesson lesson,
            PracticeCheckType type,
            String target,
            int score,
            PronunciationFeedback pronunciation,
            LocalDateTime checkedAt,
            String headline,
            String feedback,
            List<String> tips) {
        AiPracticeCheck check = new AiPracticeCheck();
        check.setStudentUser(student);
        check.setLesson(lesson);
        check.setCheckType(type);
        check.setTargetText(target);
        check.setScore(score);
        check.setPronunciation(pronunciation);
        check.setHeadline(headline);
        check.setFeedback(feedback);
        check.setTips(String.join("\n", tips));
        check.setCreatedAt(checkedAt);
        check.setUpdatedAt(checkedAt);
        entityManager.persist(check);
    }
}
