package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.config.seed.DemoOpsSeeder;
import com.example.aichinese.config.seed.DemoPersonas;
import com.example.aichinese.dto.audit.AuditEntryResponse;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.CoursePackageRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.service.support.SupportService;

/** The ops demo data: the prototype's support requests, the admins' notifications, a deactivated learner, the audit log. */
@SpringBootTest
@ActiveProfiles({"demo", "demo-test"})
class DemoOpsSeederTests {

    @Autowired
    private DemoOpsSeeder demoOpsSeeder;

    @Autowired
    private SupportRequestRepository supportRequestRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private SupportService supportService;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private CoursePackageRepository coursePackageRepository;

    @Autowired
    private AdminUserService adminUserService;

    @Test
    @Transactional
    void seedsTheSupportQueueAndADeactivatedLearnerOnce() {
        Map<Integer, SupportRequestStatus> requests = supportRequestRepository.findAll().stream()
                .collect(Collectors.toMap(SupportRequest::getNumber, SupportRequest::getStatus));
        assertThat(requests).containsOnly(
                entry(1021, SupportRequestStatus.SOLVED),
                entry(1038, SupportRequestStatus.ANSWERED),
                entry(1039, SupportRequestStatus.ANSWERED),
                entry(1041, SupportRequestStatus.OPEN),
                entry(1042, SupportRequestStatus.OPEN));
        assertThat(userRepository.findByEmailWithRoles(DemoPersonas.ZHANG_YUXI.email()).orElseThrow().isActive()).isFalse();
        assertThat(auditLogRepository.count()).isGreaterThanOrEqualTo(10);

        demoOpsSeeder.run();
        assertThat(supportRequestRepository.count()).isEqualTo(requests.size());
        assertThat(supportService.createForVisitor(new VisitorSupportRequest(
                "new-visitor@example.invalid", SupportTopic.ACCOUNT, "A request after the demo fixtures", null)).number())
                .isEqualTo(1043);
    }

    /** The queue shows how long ago each request last changed: when it was solved, answered or sent. */
    @Test
    @Transactional
    void eachSupportRequestWasLastUpdatedByItsLastEvent() {
        assertThat(supportRequestRepository.findAll()).hasSize(5).allSatisfy(request -> {
            LocalDateTime lastEvent = switch (request.getStatus()) {
                case SOLVED -> request.getSolvedAt();
                case ANSWERED -> request.getLastSupportReplyAt();
                case OPEN -> request.getCreatedAt();
            };
            assertThat(request.getUpdatedAt()).as("#" + request.getNumber()).isEqualTo(lastEvent);
        });
    }

    /**
     * The seeded notifications read like the ones the real flows send: the same wording and link, a target that exists,
     * and the time of what they report. Only the kinds the product still sends: support replies reach learners by
     * e-mail, not as notifications.
     */
    @Test
    @Transactional
    void seededNotificationsMatchTheEventsTheyReport() {
        List<Notification> all = notificationRepository.findAll();
        assertThat(all).isNotEmpty().extracting(Notification::getNotificationType)
                .isSubsetOf(NotificationType.SUPPORT_REQUEST, NotificationType.ADMIN_TASK,
                        NotificationType.AI_REPORT_REVIEW, NotificationType.DAILY_GOAL_REMINDER,
                        NotificationType.NEW_PRACTICE_SET, NotificationType.MISTAKES_TO_REVIEW,
                        NotificationType.RANK_REACHED, NotificationType.LESSON_UPDATED);

        List<Notification> supportNotices = all.stream()
                .filter(notification -> notification.getNotificationType() == NotificationType.SUPPORT_REQUEST)
                .toList();
        assertThat(supportNotices).hasSize(2).allSatisfy(notice -> {
            assertThat(notice.getUser().getRoles()).extracting(Role::getName).contains(RoleName.ADMIN);
            assertThat(notice.getTitle()).isEqualTo("New support request");
            assertThat(notice.getTargetPath()).isEqualTo("/admin/support?request=" + notice.getTargetId());
            SupportRequest request = supportRequestRepository.findById(notice.getTargetId()).orElseThrow();
            assertThat(request.getStatus()).isEqualTo(SupportRequestStatus.OPEN);
            assertThat(notice.getCreatedAt()).isEqualTo(request.getCreatedAt());
            assertThat(notice.getMessage())
                    .contains(" sent #" + request.getNumber() + " “" + request.getSubject() + "”. Reply within 2 working days.");
        });
        assertThat(supportNotices).extracting(Notification::getMessage)
                .contains("Amira Hassan sent #1042 “Lesson video won’t play”. Reply within 2 working days.");

        // Amira's inbox (S3.09): six notifications, three unread, each opening a page that exists.
        User amira = userRepository.findByEmailWithRoles(DemoPersonas.STUDENT.email()).orElseThrow();
        List<Notification> inbox = all.stream()
                .filter(notification -> notification.getUser().getId().equals(amira.getId()))
                .toList();
        assertThat(inbox).extracting(Notification::getNotificationType).containsExactlyInAnyOrder(
                NotificationType.AI_REPORT_REVIEW, NotificationType.DAILY_GOAL_REMINDER, NotificationType.NEW_PRACTICE_SET,
                NotificationType.MISTAKES_TO_REVIEW, NotificationType.RANK_REACHED, NotificationType.LESSON_UPDATED);
        assertThat(inbox).filteredOn(notification -> notification.getNotificationStatus() == NotificationStatus.UNREAD)
                .extracting(Notification::getNotificationType)
                .containsExactlyInAnyOrder(NotificationType.DAILY_GOAL_REMINDER, NotificationType.NEW_PRACTICE_SET,
                        NotificationType.MISTAKES_TO_REVIEW);
        assertThat(inbox).allSatisfy(notification -> assertThat(notification.getTargetPath()).startsWith("/student/"));
        assertThat(inbox).filteredOn(notification -> notification.getNotificationType() == NotificationType.RANK_REACHED)
                .singleElement()
                .satisfies(rank -> assertThat(rank.getMessage()).isEqualTo("You're now Rank 3 · Regular. Rank 4 is next."));
    }

    /**
     * The audit log tells of the seeded events at their times: the learners' accounts, the admins' classes (each created,
     * the live ones published, Mandarin Foundations archived) and the deactivation, which the user drawer shows.
     */
    @Test
    @Transactional
    void theAuditLogTellsOfTheSeededEvents() {
        List<AuditLog> entries = auditLogRepository.findAll();
        assertThat(entries).extracting(AuditLog::getActionType).noneMatch(action -> action.contains("TEACHER"));
        List<CoursePackage> classes = coursePackageRepository.findAll();
        assertThat(entries).filteredOn(entry -> "ADMIN_CLASS_CREATE".equals(entry.getActionType())).hasSize(classes.size());
        assertThat(entries).filteredOn(entry -> "ADMIN_CLASS_PUBLISH".equals(entry.getActionType()))
                .hasSize((int) classes.stream().filter(c -> c.getContentStatus() != ContentStatus.DRAFT).count());
        CoursePackage foundations = classes.stream()
                .filter(coursePackage -> coursePackage.getTitle().equals("Mandarin Foundations"))
                .findFirst()
                .orElseThrow();
        assertThat(entries).filteredOn(entry -> "ADMIN_CLASS_ARCHIVE".equals(entry.getActionType()))
                .singleElement()
                .satisfies(entry -> {
                    assertThat(entry.getTargetId()).isEqualTo(foundations.getId());
                    assertThat(entry.getCreatedAt()).isEqualTo(foundations.getArchivedAt());
                });

        User admin = userRepository.findAllByRoleName(RoleName.ADMIN).get(0);
        User zhangYuxi = userRepository.findByEmailWithRoles(DemoPersonas.ZHANG_YUXI.email()).orElseThrow();
        List<AuditEntryResponse> recent = adminUserService
                .get(new SecurityUserDetails(admin), zhangYuxi.getId())
                .recentAudit();
        assertThat(recent).extracting(AuditEntryResponse::actionType)
                .containsExactly("ADMIN_USER_DEACTIVATE", "ACCOUNT_CREATED");
        assertThat(recent.get(1).createdAt()).isEqualTo(zhangYuxi.getCreatedAt());
    }
}
