package com.example.aichinese.config.seed;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.SupportMessage;
import com.example.aichinese.entity.SupportRequest;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.TeacherVerificationDocument;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.entity.enums.TeacherVerificationDocumentType;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.RoleRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.TeacherVerificationDocumentRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.FileStorageService;

/**
 * Demo data of the ops module, as in the prototype: Help & support requests (Amira's #1038 answered and #1021 solved,
 * Li Wei's #1040 answered and #1033 solved, a visitor's open #1041), two teacher applications in review (Mei Lin with
 * three documents, Farah Aziz with two), a deactivated student, notifications for every role and a realistic audit
 * log. Dates are relative to the day the demo starts.
 */
@Component
@Order(20)
@Profile("demo & !prod & !production")
public class DemoOpsSeeder implements CommandLineRunner {

    static final DemoPersonas.Persona APPLICANT_FARAH = new DemoPersonas.Persona("farah.aziz@fyp.local", "Farah Aziz");
    static final DemoPersonas.Persona DEACTIVATED_STUDENT =
            new DemoPersonas.Persona("zhang.yuxi@fyp.local", "Zhang Yuxi");
    static final String VISITOR_EMAIL = "aisyah.k@example.com";

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeacherProfileRepository teacherProfileRepository;
    private final TeacherVerificationDocumentRepository documentRepository;
    private final SupportRequestRepository supportRequestRepository;
    private final SupportMessageRepository supportMessageRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;
    private final FileStorageService fileStorageService;
    private final PasswordEncoder passwordEncoder;

    @Value("${DEMO_TEACHER_PASSWORD:DemoTeacher123!}")
    private String teacherPassword;

    @Value("${DEMO_STUDENT_PASSWORD:DemoStudent123!}")
    private String studentPassword;

    public DemoOpsSeeder(
            UserRepository userRepository,
            RoleRepository roleRepository,
            StudentProfileRepository studentProfileRepository,
            TeacherProfileRepository teacherProfileRepository,
            TeacherVerificationDocumentRepository documentRepository,
            SupportRequestRepository supportRequestRepository,
            SupportMessageRepository supportMessageRepository,
            NotificationRepository notificationRepository,
            AuditLogRepository auditLogRepository,
            FileStorageService fileStorageService,
            PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teacherProfileRepository = teacherProfileRepository;
        this.documentRepository = documentRepository;
        this.supportRequestRepository = supportRequestRepository;
        this.supportMessageRepository = supportMessageRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
        this.fileStorageService = fileStorageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(String... args) {
        User admin = userRepository.findAllByRoleName(RoleName.ADMIN).stream().findFirst().orElse(null);
        User student = userRepository.findByEmailWithRoles(DemoPersonas.STUDENT.email()).orElse(null);
        User teacher = userRepository.findByEmailWithRoles(DemoPersonas.TEACHER.email()).orElse(null);
        if (admin == null || student == null || teacher == null || userRepository.existsByEmailIgnoreCase(APPLICANT_FARAH.email())) {
            return;
        }

        TeacherProfile meiLin = teacherProfileRepository.findDetailedByUserId(
                userRepository.findByEmailWithRoles(DemoPersonas.APPLICANT.email()).orElseThrow().getId()).orElseThrow();
        submitApplication(meiLin, daysAgo(4, 9, 0), List.of(
                TeacherVerificationDocumentType.CERTIFICATE,
                TeacherVerificationDocumentType.IDENTITY,
                TeacherVerificationDocumentType.QUALIFICATION));
        TeacherProfile farah = createApplicant();
        submitApplication(farah, daysAgo(5, 16, 0), List.of(
                TeacherVerificationDocumentType.CERTIFICATE,
                TeacherVerificationDocumentType.IDENTITY));
        User deactivated = createDeactivatedStudent(admin);

        seedSupport(admin, student, teacher);
        seedNotifications(admin, student, teacher, meiLin.getUser(), farah.getUser());
        seedAuditLog(admin, teacher, meiLin.getUser(), farah.getUser(), deactivated);
    }

    // ----- teacher applications -----

    private TeacherProfile createApplicant() {
        User user = new User();
        user.setEmail(APPLICANT_FARAH.email());
        user.setDisplayName(APPLICANT_FARAH.displayName());
        user.setPasswordHash(passwordEncoder.encode(teacherPassword));
        user.setPreferredLanguage("en");
        user.setRoles(Set.of(roleRepository.findByName(RoleName.TEACHER).orElseThrow()));
        user.setCreatedAt(daysAgo(5, 15, 30));
        user = userRepository.save(user);

        TeacherProfile profile = new TeacherProfile();
        profile.setUser(user);
        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setBio("Pre-service Mandarin teacher finishing a teaching degree, focusing on beginner speaking.");
        return teacherProfileRepository.save(profile);
    }

    private void submitApplication(
            TeacherProfile profile, LocalDateTime submittedAt, List<TeacherVerificationDocumentType> types) {
        String owner = profile.getUser().getDisplayName();
        String prefix = owner.replace(" ", "");
        for (TeacherVerificationDocumentType type : types) {
            String label = switch (type) {
                case CERTIFICATE -> "Teaching certificate";
                case IDENTITY -> "Identity document";
                default -> "Degree certificate";
            };
            String suffix = switch (type) {
                case CERTIFICATE -> "TeachingCertificate";
                case IDENTITY -> "IC";
                default -> "Degree";
            };
            TeacherVerificationDocument document = new TeacherVerificationDocument();
            document.setTeacher(profile);
            document.setUploadedFile(fileStorageService.storeVerificationDocument(
                    DemoDocumentFile.pdf(prefix + "_" + suffix + ".pdf",
                            List.of("MandarinSpark demo document", label, owner)),
                    profile.getUser()));
            document.setDocumentType(type);
            document.setSubmittedAt(submittedAt.minusMinutes(10));
            document.setCurrentVersion(true);
            documentRepository.save(document);
        }
        profile.setTeacherStatus(TeacherStatus.PENDING);
        profile.setVerificationSubmittedAt(submittedAt);
        profile.setStandardsAcceptedAt(submittedAt);
        teacherProfileRepository.save(profile);
    }

    private User createDeactivatedStudent(User admin) {
        User user = new User();
        user.setEmail(DEACTIVATED_STUDENT.email());
        user.setDisplayName(DEACTIVATED_STUDENT.displayName());
        user.setPasswordHash(passwordEncoder.encode(studentPassword));
        user.setPreferredLanguage("en");
        user.setActive(false);
        user.setRoles(Set.of(roleRepository.findByName(RoleName.STUDENT).orElseThrow()));
        user.setCreatedAt(daysAgo(20, 11, 0));
        user = userRepository.save(user);
        StudentProfile profile = new StudentProfile();
        profile.setUser(user);
        studentProfileRepository.save(profile);
        return user;
    }

    // ----- support -----

    private void seedSupport(User admin, User student, User teacher) {
        SupportRequest linkRequest = request(1038, student, SupportRequesterRole.STUDENT, SupportTopic.CLASSES,
                "Live session link didn't open", daysAgo(2, 15, 52));
        message(linkRequest, student, false,
                "The Join button for “Greetings 问候” didn't open Zoom. It just kept loading.",
                daysAgo(2, 15, 52));
        answer(linkRequest, admin,
                "Thanks for telling us. The join link opens 15 minutes before the start, so it couldn't open at "
                        + "3:30 PM. It worked for everyone at 3:45 PM. If it happens again, use “Copy link” "
                        + "in the session details and reply here.",
                daysAgo(1, 10, 5));

        SupportRequest nameRequest = request(1021, student, SupportRequesterRole.STUDENT, SupportTopic.ACCOUNT,
                "Change my display name", daysAgo(16, 10, 20));
        message(nameRequest, student, false, "Can I show my name as Amira H. instead of my full name?", daysAgo(16, 10, 20));
        answer(nameRequest, admin, "Yes: open Account, change Name and press Save. It shows everywhere at once.",
                daysAgo(15, 9, 0));
        solve(nameRequest, SupportClosedBy.REQUESTER, daysAgo(15, 18, 0));

        SupportRequest lengthRequest = request(1040, teacher, SupportRequesterRole.TEACHER, SupportTopic.CLASSES,
                "Can a live session be longer than 60 minutes?", daysAgo(3, 14, 10));
        message(lengthRequest, teacher, false,
                "I'd like to run a 90-minute Tones Clinic. Is there a time limit for live sessions?", daysAgo(3, 14, 10));
        answer(lengthRequest, admin,
                "Yes. A session can run up to 120 minutes. Open it in Live sessions › Edit and change the end "
                        + "time; booked students are told about the new time automatically.",
                daysAgo(2, 9, 30));

        SupportRequest reorderRequest = request(1033, teacher, SupportRequesterRole.TEACHER, SupportTopic.CLASSES,
                "Reorder lessons after publishing", daysAgo(20, 11, 0));
        message(reorderRequest, teacher, false,
                "Can I change the order of the lessons in a class that is already published?", daysAgo(20, 11, 0));
        answer(reorderRequest, admin,
                "Yes. Reordering is an edit: an admin reviews it while students keep the current order.", daysAgo(19, 9, 0));
        solve(reorderRequest, SupportClosedBy.REQUESTER, daysAgo(19, 12, 0));

        SupportRequest visitorRequest = request(1041, null, SupportRequesterRole.VISITOR, SupportTopic.ACCOUNT,
                "Can't sign in after resetting my password", LocalDateTime.now().minusHours(2));
        message(visitorRequest, null, false,
                "I reset my password, but the new one still doesn't work on the sign-in page.",
                LocalDateTime.now().minusHours(2));
    }

    private SupportRequest request(int number, User requester, SupportRequesterRole role, SupportTopic topic,
            String subject, LocalDateTime createdAt) {
        SupportRequest request = new SupportRequest();
        request.setNumber(number);
        request.setRequester(requester);
        request.setRequesterEmail(requester == null ? VISITOR_EMAIL : requester.getEmail());
        request.setRequesterRole(role);
        request.setTopic(topic);
        request.setSubject(subject);
        request.setStatus(SupportRequestStatus.OPEN);
        request.setCreatedAt(createdAt);
        request.setUpdatedAt(createdAt);
        return supportRequestRepository.save(request);
    }

    private void message(SupportRequest request, User author, boolean fromSupport, String body, LocalDateTime at) {
        SupportMessage message = new SupportMessage();
        message.setRequest(request);
        message.setAuthor(author);
        message.setFromSupport(fromSupport);
        message.setBody(body);
        message.setCreatedAt(at);
        message.setUpdatedAt(at);
        supportMessageRepository.save(message);
    }

    private void answer(SupportRequest request, User admin, String body, LocalDateTime at) {
        message(request, admin, true, body, at);
        request.setStatus(SupportRequestStatus.ANSWERED);
        request.setLastSupportReplyAt(at);
        request.setClosesAt(at.plusDays(7));
        request.setUpdatedAt(at);
        supportRequestRepository.save(request);
    }

    private void solve(SupportRequest request, SupportClosedBy closedBy, LocalDateTime at) {
        request.setStatus(SupportRequestStatus.SOLVED);
        request.setSolvedAt(at);
        request.setClosedBy(closedBy);
        request.setClosesAt(null);
        request.setUpdatedAt(at);
        supportRequestRepository.save(request);
    }

    // ----- notifications -----

    private void seedNotifications(User admin, User student, User teacher, User meiLin, User farah) {
        notify(student, NotificationType.LIVE_CLASS_REMINDER, "Live session starts soon",
                "“Greetings 问候” begins in 30 minutes.", "/student/classes/live",
                LocalDateTime.now().minusMinutes(20), false);
        notify(student, NotificationType.TEACHER_FEEDBACK, "New feedback from Li Wei",
                "Li Wei left feedback on your “Lesson quiz: Greetings”.", "/student/classes",
                LocalDateTime.now().minusHours(3), false);
        notify(student, NotificationType.LIVE_CLASS_REMINDER, "Live session cancelled",
                "Li Wei cancelled “Finding Places 问路”. Your booking was cancelled.",
                "/student/classes/live", daysAgo(1, 10, 0), false);
        notify(student, NotificationType.SUPPORT_REQUEST, "Support replied to your request",
                admin.getDisplayName() + " answered #1038 “Live session link didn't open”. Reply, or mark it solved.",
                supportPath("/student", 1038), daysAgo(1, 10, 5), true);
        notify(student, NotificationType.AI_REPORT_REVIEW, "Your AI report was reviewed",
                "An admin checked the SparkBot answer you reported and resolved it.", "/student/ai/ask",
                daysAgo(1, 9, 0), true);
        notify(student, NotificationType.LIVE_CLASS_REMINDER, "Live session rescheduled",
                "“Tones Clinic” now starts one hour later.", "/student/classes/live", daysAgo(2, 17, 0), true);

        notify(teacher, NotificationType.LIVE_CLASS_REMINDER, "New booking",
                "Chen Hao booked “Greetings 问候”.", "/teacher/live-sessions", daysAgo(1, 16, 0), false);
        notify(teacher, NotificationType.SUPPORT_REQUEST, "Support replied to your request",
                admin.getDisplayName() + " answered #1040 “Can a live session be longer than 60 minutes?”.",
                supportPath("/teacher", 1040), daysAgo(2, 9, 30), true);
        notify(teacher, NotificationType.CLASS_ENROLMENT, "New student",
                "Nur Aisyah joined “Mandarin for Beginners”. You can follow her progress in Students.",
                "/teacher/students", daysAgo(2, 10, 0), true);
        notify(teacher, NotificationType.CONTENT_REVIEW, "Changes requested: University Life",
                "An admin asked for changes to your revision of “University Life”.", "/teacher/classes",
                daysAgo(3, 15, 30), true);

        notify(meiLin, NotificationType.TEACHER_APPROVAL, "Application received",
                "Your documents are with an admin. You get a notification with the decision.", "/teacher/profile",
                daysAgo(4, 9, 0), false);

        notify(admin, NotificationType.SUPPORT_REQUEST, "New support request",
                "A visitor (" + VISITOR_EMAIL + ") sent #1041 “Can't sign in after resetting my password”. "
                        + "Reply within 2 working days.",
                "/admin/support", LocalDateTime.now().minusHours(2), false);
        notify(admin, NotificationType.CONTENT_REVIEW, "Revision submitted for review",
                "Li Wei submitted a revision of “Ordering Food at the Canteen”.", "/admin/content",
                LocalDateTime.now().minusHours(5), false);
        notify(admin, NotificationType.AI_REPORT_REVIEW, "New AI answer report",
                "A student reported incorrect content about a self-introduction phrase.", "/admin/ai",
                daysAgo(2, 12, 0), false);
        notify(admin, NotificationType.TEACHER_APPROVAL, "New teacher application",
                meiLin.getDisplayName() + " submitted verification documents.", "/admin/users/applications",
                daysAgo(4, 9, 0), false);
        notify(admin, NotificationType.TEACHER_APPROVAL, "New teacher application",
                farah.getDisplayName() + " submitted verification documents.", "/admin/users/applications",
                daysAgo(5, 16, 0), true);
    }

    private void notify(User recipient, NotificationType type, String title, String message, String path,
            LocalDateTime at, boolean read) {
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setNotificationType(type);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setTargetPath(path);
        notification.setNotificationStatus(read ? NotificationStatus.READ : NotificationStatus.UNREAD);
        notification.setReadAt(read ? at.plusHours(1) : null);
        notification.setCreatedAt(at);
        notification.setUpdatedAt(at);
        notificationRepository.save(notification);
    }

    private String supportPath(String root, int number) {
        return supportRequestRepository.findAll().stream()
                .filter(request -> request.getNumber() == number)
                .findFirst()
                .map(request -> root + "/support/" + request.getId())
                .orElse(root + "/support");
    }

    // ----- audit log -----

    private void seedAuditLog(User admin, User teacher, User meiLin, User farah, User deactivated) {
        TeacherProfile teacherProfile = teacherProfileRepository.findByUserId(teacher.getId()).orElseThrow();
        audit(admin, "ADMIN_TEACHER_APPROVE", "TeacherProfile", teacherProfile.getId(), teacher.getDisplayName(), null,
                "Admin approved the teacher application.", daysAgo(60, 12, 0));
        audit(admin, "ADMIN_USER_DEACTIVATE", "User", deactivated.getId(), deactivated.getDisplayName(),
                "Shared the account with another person, against the Terms of Service.",
                "Admin deactivated the account: signed out, sign-in blocked.", daysAgo(6, 9, 15));
        audit(farah, "TEACHER_APPLICATION_SUBMITTED", "TeacherProfile",
                teacherProfileRepository.findByUserId(farah.getId()).orElseThrow().getId(), farah.getDisplayName(), null,
                "Teacher submitted the application.", daysAgo(5, 16, 0));
        audit(meiLin, "TEACHER_APPLICATION_SUBMITTED", "TeacherProfile",
                teacherProfileRepository.findByUserId(meiLin.getId()).orElseThrow().getId(), meiLin.getDisplayName(), null,
                "Teacher submitted the application.", daysAgo(4, 9, 0));
        audit(teacher, "CONTENT_SUBMIT", "Lesson", null, "University Life (revision)", null,
                "Teacher submitted a lesson revision for review.", daysAgo(4, 16, 0));
        audit(admin, "CONTENT_REJECT", "Lesson", null, "University Life (revision)",
                "Add pinyin under the new words in the short story.", "Admin requested changes.", daysAgo(3, 15, 30));
        audit(admin, "AI_RESPONSE_REPORT_REVIEW", "AiResponseReport", null, "Report from Amira Hassan · Chat", null,
                "Admin resolved an AI answer report.", daysAgo(1, 9, 0));
        audit(teacher, "CONTENT_SUBMIT", "CoursePackage", null, "Chinese Culture and Festivals", null,
                "Teacher submitted a new class for review.", LocalDateTime.now().minusHours(6));
        audit(teacher, "CONTENT_SUBMIT", "Lesson", null, "Ordering Food at the Canteen (revision)", null,
                "Teacher submitted a lesson revision for review.", LocalDateTime.now().minusHours(5));
        audit(admin, "ADMIN_PLATFORM_SETTINGS_UPDATED", "SystemSetting", null, "AI settings", null,
                "Admin updated the AI settings.", LocalDateTime.now().minusHours(4));
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
        auditLogRepository.save(entry);
    }

    /** A wall-clock time {@code days} back; one later today than now moves to the day before. */
    private static LocalDateTime daysAgo(int days, int hour, int minute) {
        LocalDateTime time = LocalDate.now().minusDays(days).atTime(LocalTime.of(hour, minute));
        return time.isAfter(LocalDateTime.now()) ? time.minusDays(1) : time;
    }
}
