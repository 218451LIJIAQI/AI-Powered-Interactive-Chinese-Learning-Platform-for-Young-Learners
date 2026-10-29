package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
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
import com.example.aichinese.repository.AiPracticeCheckRepository;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.AiUsageQuotaCounterRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.UserRepository;

/**
 * AI Helper history of the demo personas, so a demo run looks like the prototype: Amira's two reports (one open, one
 * resolved with an admin note) and Chen Hao's open one, a few usage-log rows across features and roles, and Amira's
 * three practice checks (results only: no recording or photo is ever kept). Dates are relative to today.
 */
@Component
@Order(5)
@Profile("demo & !prod & !production")
public class DemoAiSeeder implements CommandLineRunner {

    private static final String DEMO_PROVIDER = "openai";
    private static final String DEMO_MODEL = "gpt-5.6-luna";

    private final UserRepository userRepository;
    private final AiResponseReportRepository reportRepository;
    private final AiUsageLogRepository usageLogRepository;
    private final AiUsageQuotaCounterRepository quotaCounterRepository;
    private final AiPracticeCheckRepository practiceCheckRepository;
    private final LessonRepository lessonRepository;

    public DemoAiSeeder(
            UserRepository userRepository,
            AiResponseReportRepository reportRepository,
            AiUsageLogRepository usageLogRepository,
            AiUsageQuotaCounterRepository quotaCounterRepository,
            AiPracticeCheckRepository practiceCheckRepository,
            LessonRepository lessonRepository) {
        this.userRepository = userRepository;
        this.reportRepository = reportRepository;
        this.usageLogRepository = usageLogRepository;
        this.quotaCounterRepository = quotaCounterRepository;
        this.practiceCheckRepository = practiceCheckRepository;
        this.lessonRepository = lessonRepository;
    }

    @Override
    @Transactional
    public void run(String... args) {
        User amira = persona(DemoPersonas.STUDENT);
        if (amira == null || reportRepository.count() > 0) {
            return;
        }
        User chenHao = persona(DemoPersonas.CHEN_HAO);
        User nurAisyah = persona(DemoPersonas.NUR_AISYAH);
        User zhangYi = persona(DemoPersonas.ZHANG_YI);
        User liWei = persona(DemoPersonas.TEACHER);
        User admin = userRepository.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);
        LocalDate today = LocalDate.now();

        report(amira, AiResponseReportReason.INCORRECT_CONTENT, at(today.minusDays(2), 12, 0),
                "How do I introduce myself in Mandarin?",
                "你可以说“我是叫 Aina”。(Nǐ kěyǐ shuō “wǒ shì jiào Aina”.) That means “My name is Aina”.",
                "The answer mixes 我是 and 我叫.", null, null, null);
        if (chenHao != null) {
            report(chenHao, AiResponseReportReason.UNCLEAR_EXPLANATION, at(today.minusDays(3), 9, 20),
                    "When do I use 了?",
                    "了 (le) shows a change or a finished action, for example 我吃了 (wǒ chī le).",
                    "I still don't know when to leave 了 out.", null, null, null);
        }
        report(amira, AiResponseReportReason.UNCLEAR_EXPLANATION, at(today.minusDays(4), 20, 10),
                "What is the difference between 再见 and 拜拜?",
                "再见 (zàijiàn) and 拜拜 (báibái) both mean goodbye.",
                "It doesn't say when to use which one.", admin,
                "Thanks — the answer was too short. We added this case to the safety check so SparkBot explains both.",
                at(today.minusDays(1), 9, 0));

        log(amira, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, at(today, 9, 42), null);
        log(amira, AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS, at(today, 10, 5), null);
        log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, at(today, 11, 0), null);
        log(chenHao, AiFeatureKey.QUIZ_GENERATOR, AiRequestStatus.SUCCESS, at(today.minusDays(1), 13, 5), null);
        log(nurAisyah, AiFeatureKey.STORY_GENERATOR, AiRequestStatus.UNAVAILABLE, at(today.minusDays(1), 11, 30),
                "AI provider is disabled in the current backend configuration.");
        log(liWei, AiFeatureKey.TEACHER_DRAFT, AiRequestStatus.FAILED, at(today.minusDays(1), 16, 12),
                "Provider request failed or timed out.");
        log(zhangYi, AiFeatureKey.XIAOMEI_CONVERSATION, AiRequestStatus.SUCCESS, at(today.minusDays(2), 15, 40), null);
        log(amira, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, at(today.minusDays(2), 11, 58), null);
        log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, at(today.minusDays(3), 18, 0), null);
        log(amira, AiFeatureKey.PRACTICE_CHECK, AiRequestStatus.SUCCESS, at(today.minusDays(4), 15, 30), null);
        log(amira, AiFeatureKey.STORY_GENERATOR, AiRequestStatus.SUCCESS, at(today.minusDays(4), 19, 45), null);
        log(chenHao, AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, at(today.minusDays(3), 9, 15), null);
        countToday(amira, 3, today);

        Lesson greetings = lessonRepository.findAll().stream()
                .filter(lesson -> lesson.getRevisionOf() == null
                        && DemoClassContent.GREETINGS.title().equals(lesson.getTitle()))
                .findFirst()
                .orElse(null);
        check(amira, greetings, PracticeCheckType.RECORDING, "你好", 82, 76, 88, 84, at(today, 11, 0),
                "Clear and friendly — keep tone 3 low",
                "Both syllables were easy to understand. 你 sounded a little high.",
                List.of("你 nǐ — the 3rd tone dips low before it rises.", "好 hǎo — well done, nice and clear."));
        check(amira, greetings, PracticeCheckType.RECORDING, "你好吗？", 74, 65, 80, 78, at(today.minusDays(3), 18, 0),
                "Good start — the question needs a lift",
                "你好 was clear. 吗 came out flat, so it did not sound like a question yet.",
                List.of("吗 ma — a light, neutral tone at the end of a question.", "Say 你好 as one phrase, then add 吗."));
        check(amira, greetings, PracticeCheckType.HANDWRITING, "好", 88, null, null, null, at(today.minusDays(4), 15, 30),
                "Neat and balanced",
                "The two parts 女 and 子 are both visible. Keep 女 a little narrower.",
                List.of("Leave a small gap between 女 and 子.", "Make the last stroke of 子 a firm horizontal."));
    }

    private User persona(DemoPersonas.Persona persona) {
        return userRepository.findByEmailWithRoles(persona.email()).orElse(null);
    }

    /** A wall-clock time on {@code day}; one later today than now moves to the day before. */
    private static LocalDateTime at(LocalDate day, int hour, int minute) {
        LocalDateTime time = LocalDateTime.of(day, LocalTime.of(hour, minute));
        return time.isAfter(LocalDateTime.now()) ? time.minusDays(1) : time;
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
        report.setUpdatedAt(reviewedAt == null ? reportedAt : reviewedAt);
        if (reviewerNote != null) {
            report.setStatus(AiResponseReportStatus.RESOLVED);
            report.setReviewerUser(reviewer);
            report.setReviewerNote(reviewerNote);
            report.setReviewedAt(reviewedAt);
        }
        reportRepository.save(report);
    }

    private void log(User user, AiFeatureKey feature, AiRequestStatus status, LocalDateTime when, String error) {
        if (user == null) {
            return;
        }
        AiUsageLog entry = new AiUsageLog();
        entry.setUser(user);
        entry.setUserRole(user.getRoles().stream().map(role -> role.getName().name()).sorted().findFirst().orElse("USER"));
        entry.setFeatureKey(feature);
        entry.setLanguagePreference("en");
        entry.setStatus(status.name());
        entry.setProviderName(DEMO_PROVIDER);
        entry.setModelName(DEMO_MODEL);
        entry.setErrorSummary(error);
        entry.setCreatedAt(when);
        entry.setUpdatedAt(when);
        usageLogRepository.save(entry);
    }

    /** Today's requests of the student, so the "N of 100 requests today" line matches her log rows. */
    private void countToday(User student, int requests, LocalDate today) {
        AiUsageQuotaCounter counter = new AiUsageQuotaCounter();
        counter.setUser(student);
        counter.setMinuteStartedAt(LocalDateTime.now().minusHours(1));
        counter.setMinuteCount(0);
        counter.setUsageDay(today);
        counter.setDayCount(requests);
        quotaCounterRepository.save(counter);
    }

    private void check(
            User student,
            Lesson lesson,
            PracticeCheckType type,
            String target,
            int score,
            Integer tones,
            Integer clarity,
            Integer pace,
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
        check.setTonesScore(tones);
        check.setClarityScore(clarity);
        check.setPaceScore(pace);
        check.setHeadline(headline);
        check.setFeedback(feedback);
        check.setTips(String.join("\n", tips));
        check.setCreatedAt(checkedAt);
        check.setUpdatedAt(checkedAt);
        practiceCheckRepository.save(check);
    }
}
