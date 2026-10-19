package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.CommunicationMapper;
import com.example.aichinese.service.NotificationService;

/**
 * Unit coverage for {@code NotificationService} - pins input validation
 * and trimming on create, the fail-safe {@code safeCreateNotification}
 * path (independent transaction, sanitised log), hiding of notifications
 * whose target is no longer accessible, and the unread-count and
 * mark-all-read contracts across repository batches.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceTests {

    private static final long STUDENT_USER_ID = 1L;
    private static final long SAFE_CREATE_RECIPIENT_ID = 77L;
    private static final long LESSON_ID = 10L;
    private static final long FEEDBACK_ID = 20L;
    private static final String TITLE = "Title";
    private static final String MESSAGE = "Message";
    private static final String STUDENT_PATH = "/student";
    private static final String STUDENT_PROFILE_PATH = "/student/profile";
    private static final String TEACHER_FEEDBACK_TARGET = "TEACHER_FEEDBACK";
    private static final String SECRET_TOKEN = "secret-token";
    private static final String REDACTED_TOKEN = "token=[REDACTED]";
    private static final String SANITIZED_FAILURE_LOG =
            "Notification creation failed for recipient user id " + SAFE_CREATE_RECIPIENT_ID + ": RuntimeException";

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TeacherFeedbackRepository teacherFeedbackRepository = mock(TeacherFeedbackRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);

    @Test
    void createNotificationRejectsInvalidRequiredFieldsBeforePersistence() {
        NotificationService service = service();
        User recipient = user(STUDENT_USER_ID, RoleName.STUDENT);

        assertThatThrownBy(() -> service.createNotification(null, NotificationType.SYSTEM, TITLE, MESSAGE, null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
        assertThatThrownBy(() -> service.createNotification(
                recipient,
                null,
                TITLE,
                MESSAGE,
                null,
                null,
                STUDENT_PATH,
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> service.createNotification(recipient, NotificationType.SYSTEM, " ", MESSAGE, null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> service.createNotification(recipient, NotificationType.SYSTEM, TITLE, "\t", null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void createNotificationTrimsDisplayFieldsAndOptionalTargets() {
        NotificationService service = service();
        User recipient = user(STUDENT_USER_ID, RoleName.STUDENT);
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createNotification(
                recipient,
                NotificationType.SYSTEM,
                "  Title  ",
                "\nMessage\n",
                " TARGET ",
                42L,
                " /student/profile ",
                null);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getTitle()).isEqualTo(TITLE);
        assertThat(saved.getMessage()).isEqualTo(MESSAGE);
        assertThat(saved.getTargetType()).isEqualTo("TARGET");
        assertThat(saved.getTargetPath()).isEqualTo(STUDENT_PROFILE_PATH);
    }

    @Test
    void safeCreateNotificationDoesNotPropagateNotificationFailures(CapturedOutput output) {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("notification store failed token=" + SECRET_TOKEN));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        NotificationService service = new NotificationService(
                notificationRepository,
                mock(CommunicationMapper.class),
                transactionManager,
                mock(TeacherFeedbackRepository.class),
                mock(RecordedLessonVideoRepository.class),
                mock(LiveClassRepository.class),
                mock(StudentProfileRepository.class));

        User recipient = new User();
        recipient.setId(SAFE_CREATE_RECIPIENT_ID);
        recipient.setEmail("student@example.com");
        recipient.setDisplayName("Student");

        assertThatCode(() -> service.safeCreateNotification(
                recipient,
                "Test notification",
                "This notification should fail safely.",
                STUDENT_PROFILE_PATH))
                .doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
        verify(transactionManager).rollback(transactionStatus);
        assertThat(output).contains(SANITIZED_FAILURE_LOG);
        assertThat(output).contains(REDACTED_TOKEN);
        assertThat(output).doesNotContain(SECRET_TOKEN);
        assertThat(output).doesNotContain("at com.example.aichinese");
    }

    @Test
    void teacherFeedbackNotificationIsHiddenWhenLessonIsNoLongerPublished() {
        NotificationService service = service();
        User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);
        StudentProfile student = student(studentUser);
        Lesson lesson = lesson(LESSON_ID);
        lesson.setContentStatus(ContentStatus.ARCHIVED);
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setStudent(student);
        feedback.setLesson(lesson);
        Notification notification = unreadTeacherFeedbackNotification(studentUser);

        when(teacherFeedbackRepository.findByIdAndStudentUserId(FEEDBACK_ID, STUDENT_USER_ID))
                .thenReturn(Optional.of(feedback));
        assertThat(service.isNotificationCurrentlyAccessible(securityUser(studentUser), notification)).isFalse();
    }

    @Test
    void teacherFeedbackNotificationIsVisibleForPublishedLesson() {
        NotificationService service = service();
        User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);
        StudentProfile student = student(studentUser);
        Lesson lesson = lesson(LESSON_ID);
        lesson.setContentStatus(ContentStatus.PUBLISHED);
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setStudent(student);
        feedback.setLesson(lesson);
        Notification notification = unreadTeacherFeedbackNotification(studentUser);

        when(teacherFeedbackRepository.findByIdAndStudentUserId(FEEDBACK_ID, STUDENT_USER_ID))
                .thenReturn(Optional.of(feedback));
        assertThat(service.isNotificationCurrentlyAccessible(securityUser(studentUser), notification)).isTrue();
    }

    @Test
    void unreadCountExcludesInaccessibleTargetedNotifications() {
        NotificationService service = service();
        User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);
        Notification hidden = unreadTeacherFeedbackNotification(studentUser);
        Notification visible = notification(studentUser, null, null, NotificationStatus.UNREAD);

        when(notificationRepository.findUserStatusBatchBefore(
                any(), any(), nullable(LocalDateTime.class), any(), any()))
                .thenReturn(List.of(hidden, visible));
        when(teacherFeedbackRepository.findByIdAndStudentUserId(FEEDBACK_ID, STUDENT_USER_ID))
                .thenReturn(Optional.empty());

        assertThat(service.countAccessibleUnreadNotifications(securityUser(studentUser))).isEqualTo(1);
    }

    @Test
    void markAllAsReadOnlyCountsAndUpdatesAccessibleNotifications() {
        NotificationService service = service();
        User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);
        Notification hidden = unreadTeacherFeedbackNotification(studentUser);
        Notification visible = notification(studentUser, null, null, NotificationStatus.UNREAD);

        when(notificationRepository.findUserStatusBatchBefore(
                any(), any(), nullable(LocalDateTime.class), any(), any()))
                .thenReturn(List.of(hidden, visible));
        when(teacherFeedbackRepository.findByIdAndStudentUserId(FEEDBACK_ID, STUDENT_USER_ID))
                .thenReturn(Optional.empty());

        BulkOperationResponse response = service.markAllAsRead(securityUser(studentUser));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(hidden.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(visible.getNotificationStatus()).isEqualTo(NotificationStatus.READ);
        verify(notificationRepository).saveAll(List.of(visible));
    }

    @Test
    void unreadCountProcessesMoreThanOneRepositoryBatch() {
        NotificationService service = service();
        User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);
        List<Notification> firstBatch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (long id = 300; id > 50; id--) {
            Notification notification = notification(studentUser, null, null, NotificationStatus.UNREAD);
            notification.setId(id);
            notification.setCreatedAt(now.minusSeconds(300 - id));
            firstBatch.add(notification);
        }
        Notification finalNotification = notification(studentUser, null, null, NotificationStatus.UNREAD);
        finalNotification.setId(50L);
        finalNotification.setCreatedAt(now.minusSeconds(250));

        when(notificationRepository.findUserStatusBatchBefore(
                any(), any(), nullable(LocalDateTime.class), any(), any()))
                .thenReturn(firstBatch)
                .thenReturn(List.of(finalNotification));

        assertThat(service.countAccessibleUnreadNotifications(securityUser(studentUser))).isEqualTo(251);
        verify(notificationRepository, times(2))
                .findUserStatusBatchBefore(any(), any(), nullable(LocalDateTime.class), any(), any());
    }

    private NotificationService service() {
        return new NotificationService(
                notificationRepository,
                mock(CommunicationMapper.class),
                mock(PlatformTransactionManager.class),
                teacherFeedbackRepository,
                mock(RecordedLessonVideoRepository.class),
                mock(LiveClassRepository.class),
                studentProfileRepository);
    }

    private static User user(Long id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setEmail("user-" + id + "@example.com");
        user.setDisplayName("User " + id);
        user.setPasswordHash("password");
        user.getRoles().add(role);
        return user;
    }

    private static SecurityUserDetails securityUser(User user) {
        return new SecurityUserDetails(user);
    }

    private static StudentProfile student(User user) {
        StudentProfile student = new StudentProfile();
        student.setId(2L);
        student.setUser(user);
        return student;
    }

    private static Lesson lesson(Long id) {
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTitle("Lesson " + id);
        // Student visibility (D13) also needs an approved, active owning teacher.
        User teacherUser = new User();
        teacherUser.setActive(true);
        com.example.aichinese.entity.TeacherProfile teacher = new com.example.aichinese.entity.TeacherProfile();
        teacher.setUser(teacherUser);
        teacher.setTeacherStatus(com.example.aichinese.entity.enums.TeacherStatus.APPROVED);
        lesson.setTeacher(teacher);
        return lesson;
    }

    private static Notification notification(User user, String targetType, Long targetId, NotificationStatus status) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setNotificationStatus(status);
        return notification;
    }

    private static Notification unreadTeacherFeedbackNotification(User user) {
        return notification(user, TEACHER_FEEDBACK_TARGET, FEEDBACK_ID, NotificationStatus.UNREAD);
    }
}
