package com.example.aichinese.config.seed;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.AiResponseReport;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.SupportMessage;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.SystemSetting;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.SystemSettingRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.PlatformSettingsService;
import com.example.aichinese.service.admin.AuditLogQueryService;
import com.example.aichinese.service.classes.ClassContentSupport;
import com.example.aichinese.service.learn.LearnerReminderJobs;
import com.example.aichinese.service.learn.PracticeSetDirectory;
import com.example.aichinese.service.support.SupportService;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

/**
 * Demo data of the ops module, as in the prototype: support requests (Amira's open #1042 "Lesson video won't play" from
 * the lesson page's Report a problem, a visitor's open #1041, two answered and one solved), the admins' and learners'
 * notifications of these events, and a realistic audit log: the learners' accounts, the admins' classes (published,
 * archived, drafts), the deactivated learner of {@link DemoContentSeeder} and the reviewed AI reports. Dates are
 * relative to the day the demo starts, on the platform clock ({@link DemoClock}).
 */
@Component
@Order(20)
@Profile("demo & !prod & !production")
@RequiredArgsConstructor
public class DemoOpsSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoOpsSeeder.class);
    private static final String VISITOR_EMAIL = "aisyah.k@example.com";
    private static final String OTHER_VISITOR_EMAIL = "daniel.ng@example.com";

    private final UserRepository userRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final CoursePackageRepository coursePackageRepository;
    private final AiResponseReportRepository aiResponseReportRepository;
    private final LessonRepository lessonRepository;
    private final NotificationRepository notificationRepository;
    private final SystemSettingRepository systemSettingRepository;
    private final ClassContentSupport classContent;
    private final PracticeSetDirectory practiceSets;
    private final EntityManager entityManager;
    private final DemoClock clock;

    @Override
    @Transactional
    public void run(String... args) {
        User student = userRepository.findByEmailWithRoles(DemoPersonas.STUDENT.email()).orElse(null);
        if (student == null) {
            return;
        }
        // Seed learner-only notices and reminder watermarks even if no default admin is configured.
        if (!notificationRepository.existsByUserIdAndNotificationType(student.getId(), NotificationType.DAILY_GOAL_REMINDER)) {
            seedLearnerNotifications(student);
        }
        seedReminderWatermarks();

        User admin = userRepository.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);
        if (admin == null) {
            log.warn("No default admin account found in demo profile; skipping demo support requests, AI report notices, and audit log.");
            return;
        }
        if (supportRequestRepository.count() > 0) {
            return;
        }
        User deactivated = userRepository.findByEmailWithRoles(DemoPersonas.ZHANG_YUXI.email()).orElseThrow();
        List<AiResponseReport> reports = aiResponseReportRepository.findAll();

        seedSupport(admin, student);
        seedAiReportNotifications(admin, reports);
        seedAuditLog(admin, student, deactivated, reports);
    }

    /** Prevent LearnerReminderJobs from immediately duplicating Amira's seeded daily-goal and mistakes notices on boot. */
    private void seedReminderWatermarks() {
        LocalDate today = clock.today();
        seedSettingIfAbsent(LearnerReminderJobs.DAILY_GOAL_LAST_RUN_KEY, today.toString());
        seedSettingIfAbsent(LearnerReminderJobs.MISTAKES_LAST_RUN_KEY,
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString());
    }

    private void seedSettingIfAbsent(String key, String value) {
        if (systemSettingRepository.findBySettingKey(key).isEmpty()) {
            SystemSetting setting = new SystemSetting();
            setting.setSettingKey(key);
            setting.setSettingValue(value);
            entityManager.persist(setting);
        }
    }

    // Support

    private void seedSupport(User admin, User student) {
        SupportRequest nameRequest = request(1021, student, SupportRequesterRole.STUDENT, SupportTopic.ACCOUNT,
                "Change my display name", null, clock.daysAgo(16, 10, 20));
        answered(nameRequest, clock.daysAgo(15, 9, 0));
        solved(nameRequest, clock.daysAgo(15, 18, 0));
        save(nameRequest, "Can I show my name as Amira H. instead of my full name?",
                admin, "Yes: open Account, change Name and press Save. It shows everywhere at once.");

        SupportRequest goalRequest = request(1038, student, SupportRequesterRole.STUDENT, SupportTopic.LEARNING,
                "Daily goal didn't update", null, clock.daysAgo(2, 22, 40));
        answered(goalRequest, clock.daysAgo(1, 10, 5));
        save(goalRequest, "I finished a lesson last night, but my daily goal still showed 10 of 20 XP this morning.",
                admin, "Thanks for telling us. You finished the lesson after midnight, so its XP counts for the next day. "
                        + "If it happens again, reply to this email.");

        SupportRequest emailRequest = request(1039, null, SupportRequesterRole.VISITOR, SupportTopic.ACCOUNT,
                "How do I change my email address?", OTHER_VISITOR_EMAIL, clock.daysAgo(1, 13, 20));
        answered(emailRequest, clock.daysAgo(1, 15, 0));
        save(emailRequest, "How do I change my email address? I can't find the setting.",
                admin, "Sign in, open Account and choose Change email. We send a link to the new address to confirm it.");

        SupportRequest visitorRequest = request(1041, null, SupportRequesterRole.VISITOR, SupportTopic.ACCOUNT,
                "Can't sign in after resetting my password", VISITOR_EMAIL, clock.now().minusHours(2));
        save(visitorRequest, "I reset my password, but the new one still doesn't work on the sign-in page.");
        notifyNewRequest(admin, visitorRequest, "A visitor (" + VISITOR_EMAIL + ")");

        // Sent from "Still not loading? Report a problem" under the lesson video (Topic Classes, the message prefilled).
        SupportRequest videoRequest = request(1042, student, SupportRequesterRole.STUDENT, SupportTopic.CLASSES,
                "Lesson video won’t play", null, clock.now().minusMinutes(10));
        save(videoRequest,
                "The video “Meeting a classmate” in the Meeting Classmates lesson doesn’t load. It stays at 0:00. "
                        + "I tried Chrome and Edge.");
        notifyNewRequest(admin, videoRequest, student.getDisplayName());
    }

    /** A request as its requester sent it, not saved yet: see {@link #save(SupportRequest, String)}. */
    private static SupportRequest request(int number, User requester, SupportRequesterRole role, SupportTopic topic,
            String subject, String visitorEmail, LocalDateTime sentAt) {
        SupportRequest request = new SupportRequest();
        request.setNumber(number);
        request.setRequester(requester);
        request.setRequesterEmail(requester == null ? visitorEmail : requester.getEmail());
        request.setRequesterRole(role);
        request.setTopic(topic);
        request.setSubject(subject);
        request.setStatus(SupportRequestStatus.OPEN);
        request.setCreatedAt(sentAt);
        request.setUpdatedAt(sentAt);
        return request;
    }

    /** Support replied, by e-mail to the requester (no e-mail goes out from the demo: it has none configured). */
    private static void answered(SupportRequest request, LocalDateTime at) {
        request.setStatus(SupportRequestStatus.ANSWERED);
        request.setLastSupportReplyAt(at);
        request.setClosesAt(at.plusDays(7));
        request.setUpdatedAt(at);
    }

    private static void solved(SupportRequest request, LocalDateTime at) {
        request.setStatus(SupportRequestStatus.SOLVED);
        request.setSolvedAt(at);
        request.setClosedBy(SupportClosedBy.SUPPORT);
        request.setClosesAt(null);
        request.setUpdatedAt(at);
    }

    /**
     * Saves the request as it stands now, then the requester's message, sent with it. Written once: a request changed
     * after its first save would show as updated when the demo was seeded (BaseEntity stamps every update), not at its
     * last event.
     */
    private void save(SupportRequest request, String body) {
        supportRequestRepository.save(request);
        message(request, request.getRequester(), false, body, request.getCreatedAt());
    }

    /** {@link #save(SupportRequest, String)} for an answered request, then support's reply at the time of the reply. */
    private void save(SupportRequest request, String body, User admin, String reply) {
        save(request, body);
        message(request, admin, true, reply, request.getLastSupportReplyAt());
    }

    private void message(SupportRequest request, User author, boolean fromSupport, String body, LocalDateTime at) {
        SupportMessage message = new SupportMessage();
        message.setRequest(request);
        message.setAuthor(author);
        message.setFromSupport(fromSupport);
        message.setBody(body);
        message.setCreatedAt(at);
        message.setUpdatedAt(at);
        entityManager.persist(message);
    }

    // Notifications

    /** "New support request", worded, targeted and linked as SupportService tells the admins of a request it receives. */
    private void notifyNewRequest(User admin, SupportRequest request, String sender) {
        notify(admin, NotificationType.SUPPORT_REQUEST, "New support request",
                sender + " sent #" + request.getNumber() + " “" + request.getSubject() + "”. Reply within 2 working days.",
                SupportService.TARGET_TYPE, request.getId(), "/admin/support?request=" + request.getId(),
                request.getCreatedAt(), false);
    }

    /**
     * The notifications the real flows send for the demo's AI reports, worded, targeted and linked as
     * AiResponseReportService writes them and stamped with the time of the event.
     */
    private void seedAiReportNotifications(User admin, List<AiResponseReport> reports) {
        for (AiResponseReport report : reports) {
            boolean waiting = report.getStatus() == AiResponseReportStatus.PENDING_REVIEW;
            notify(admin, NotificationType.ADMIN_TASK, "AI report needs review",
                    report.getStudentUser().getDisplayName() + " reported an AI answer ("
                            + report.getReasonCategory().name().replace('_', ' ').toLowerCase(Locale.ROOT) + ").",
                    AiResponseReportService.TARGET_TYPE, report.getId(), AiResponseReportService.adminReportPath(report),
                    report.getCreatedAt(), !waiting);
            if (report.getStatus() == AiResponseReportStatus.RESOLVED) {
                notify(report.getStudentUser(), NotificationType.AI_REPORT_REVIEW, "An admin replied to your AI report",
                        "Your report was resolved. Read the admin's note in AI Helper › Ask SparkBot › My reports.",
                        AiResponseReportService.TARGET_TYPE, report.getId(), AiResponseReportService.STUDENT_REPORTS_PATH,
                        report.getReviewedAt(), true);
            }
        }
    }

    /**
     * Amira's inbox as in the prototype (S3.09): with the AI report reply of {@link #seedAiReportNotifications}, six
     * notifications, three unread, worded, targeted and linked as LearnerNotifications writes them and stamped with the
     * time of their event: today's daily-goal reminder (her 20 of 30 XP), the new listening set At the Café, the weekly
     * mistakes reminder (her six mistakes), Rank 3 (reached two days ago, DemoLearnSeeder) and an update of the
     * Greetings lesson of her class Mandarin Foundations three weeks ago.
     */
    private void seedLearnerNotifications(User student) {
        LocalDateTime todayStart = clock.at(clock.today(), LocalTime.MIDNIGHT);
        LocalDateTime goalReminderAt = clock.now().minusMinutes(30);
        if (goalReminderAt.isBefore(todayStart)) {
            goalReminderAt = todayStart;
        }
        notify(student, NotificationType.DAILY_GOAL_REMINDER, "Daily goal reminder",
                "You're 10 XP away from today's goal.", null, null, "/student/home",
                goalReminderAt, false);
        practiceSets.sets(PracticeType.LISTENING, ContentStatus.PUBLISHED).stream()
                .filter(set -> "At the Café".equals(set.getTitle()))
                .findFirst()
                .ifPresent(set -> notify(student, NotificationType.NEW_PRACTICE_SET, "New in Practice",
                        "A new listening set is ready: “At the Café”.", PracticeType.LISTENING.referenceType(),
                        set.getId(), "/student/practice/listening?set=" + set.getId(), clock.daysAgo(0, 9, 30), false));
        notify(student, NotificationType.MISTAKES_TO_REVIEW, "Mistakes to review",
                "You have 6 mistakes saved. Fixing them takes about 3 minutes.", null, null,
                "/student/practice/mistakes", clock.daysAgo(1, 10, 0), false);
        notify(student, NotificationType.RANK_REACHED, "You reached Rank 3",
                "You're now Rank 3 · Regular. Rank 4 is next.", "RANK", 3L, "/student/progress",
                clock.daysAgo(2, 18, 30), true);
        lessonRepository.findByClassAndLessonTitle("Mandarin Foundations", DemoClassContent.GREETINGS.title())
                .ifPresent(greetings -> {
                    CoursePackage foundations = classContent.requireClassOfLesson(greetings);
                    notify(student, NotificationType.LESSON_UPDATED, "Lesson updated",
                            "“" + greetings.getTitle() + "” in " + foundations.getTitle() + " was updated with new content.",
                            "LESSON", greetings.getId(),
                            "/student/classes/" + foundations.getId() + "/lessons/" + greetings.getId(),
                            clock.daysAgo(21, 10, 0), true);
                });
    }

    private void notify(User recipient, NotificationType type, String title, String message, String targetType,
            Long targetId, String path, LocalDateTime at, boolean read) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTargetPath(path);
        notification.setNotificationStatus(read ? NotificationStatus.READ : NotificationStatus.UNREAD);
        notification.setReadAt(read ? at.plusHours(1) : null);
        notification.setCreatedAt(at);
        notification.setUpdatedAt(read ? notification.getReadAt() : at);
        entityManager.persist(notification);
    }

    // Audit log

    /** Entries as the real actions write them, about the seeded records and at the times of their events. */
    private void seedAuditLog(User admin, User student, User deactivated, List<AiResponseReport> reports) {
        Stream<User> others = DemoPersonas.OTHER_STUDENTS.stream()
                .map(persona -> userRepository.findByEmailWithRoles(persona.email()).orElseThrow());
        Stream.concat(Stream.of(student), Stream.concat(others, Stream.of(deactivated)))
                .forEach(learner -> audit(learner, "ACCOUNT_CREATED", AuditLogQueryService.TARGET_USER, learner.getId(),
                        null, null, "User created a learner account.", learner.getCreatedAt()));
        audit(admin, "ADMIN_USER_DEACTIVATE", AuditLogQueryService.TARGET_USER, deactivated.getId(),
                deactivated.getDisplayName(), "Shared the account with another person, against the Terms of Service.",
                "Admin deactivated the account: signed out, sign-in blocked.", clock.daysAgo(6, 9, 15));
        List<CoursePackage> classes = coursePackageRepository.findAll();
        for (CoursePackage coursePackage : classes) {
            audit(admin, "ADMIN_CLASS_CREATE", "CoursePackage", coursePackage.getId(), coursePackage.getTitle(), null,
                    "Admin created a class as a draft.", coursePackage.getCreatedAt());
            if (coursePackage.getContentStatus() != ContentStatus.DRAFT) {
                audit(admin, "ADMIN_CLASS_PUBLISH", "CoursePackage", coursePackage.getId(), coursePackage.getTitle(), null,
                        "Admin published a class.", coursePackage.getCreatedAt().plusDays(2));
            }
            if (coursePackage.getContentStatus() == ContentStatus.ARCHIVED) {
                audit(admin, "ADMIN_CLASS_ARCHIVE", "CoursePackage", coursePackage.getId(), coursePackage.getTitle(), null,
                        "Admin archived a class.", coursePackage.getArchivedAt());
            }
        }
        reports.stream()
                .filter(report -> report.getStatus() == AiResponseReportStatus.RESOLVED)
                .forEach(report -> audit(report.getReviewerUser(), AiResponseReportService.REVIEW_RESOLVED_ACTION,
                        AiResponseReportService.TARGET_TYPE, report.getId(), null, null,
                        "Admin resolved an AI report with a note to the student.", report.getReviewedAt()));
        audit(admin, PlatformSettingsService.SETTINGS_UPDATED_ACTION, "SYSTEM_SETTING", null, null, null,
                "Admin updated the AI notice, sensitive-word filter and AI safety check settings.",
                clock.now().minusHours(4));
    }

    private void audit(User actor, String action, String targetType, Long targetId, String targetLabel, String reason,
            String description, LocalDateTime at) {
        AuditLog entry = new AuditLog();
        entry.setActorUser(actor);
        entry.setActorEmail(actor.getEmail());
        entry.setActionType(action);
        entry.setTargetType(targetType);
        entry.setTargetId(targetId);
        entry.setTargetLabel(targetLabel);
        entry.setReason(reason);
        entry.setDescription(description);
        entry.setCreatedAt(at);
        entry.setUpdatedAt(at);
        entityManager.persist(entry);
    }
}
