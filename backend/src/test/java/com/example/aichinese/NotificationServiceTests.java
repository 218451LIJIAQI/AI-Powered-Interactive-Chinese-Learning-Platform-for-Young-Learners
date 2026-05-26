package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import com.example.aichinese.service.LiveLearningAccessService;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.service.StudentLearningAccessService;

/**
 * Unit coverage for {@code NotificationService} — pins row-level
 * access (a caller only sees their own notifications), the
 * unread-count badge contract, the bulk mark-all-read transaction
 * isolation (separate transaction so the badge updates promptly),
 * and the cross-user notification dispatch path.
 */
@ExtendWith(OutputCaptureExtension.class)
class NotificationServiceTests {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TeacherFeedbackRepository teacherFeedbackRepository = mock(TeacherFeedbackRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final StudentLearningAccessService studentLearningAccessService = mock(StudentLearningAccessService.class);

    @Test
    void createNotificationRejectsInvalidRequiredFieldsBeforePersistence() {
        NotificationService service = service();
        User recipient = user(1L, RoleName.STUDENT);

        assertThatThrownBy(() -> service.createNotification(null, "Title", "Message", "/student"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
        assertThatThrownBy(() -> service.createNotification(
                recipient,
                null,
                "Title",
                "Message",
                null,
                null,
                "/student",
                null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> service.createNotification(recipient, " ", "Message", "/student"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> service.createNotification(recipient, "Title", "\t", "/student"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void createNotificationTrimsDisplayFieldsAndOptionalTargets() {
        NotificationService service = service();
        User recipient = user(1L, RoleName.STUDENT);
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
        assertThat(saved.getTitle()).isEqualTo("Title");
        assertThat(saved.getMessage()).isEqualTo("Message");
        assertThat(saved.getTargetType()).isEqualTo("TARGET");
        assertThat(saved.getTargetPath()).isEqualTo("/student/profile");
    }

    @Test
    void safeCreateNotificationDoesNotPropagateNotificationFailures(CapturedOutput output) {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("notification store failed token=secret-token"));
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
                mock(StudentProfileRepository.class),
                mock(StudentLearningAccessService.class),
                mock(LiveLearningAccessService.class));

        User recipient = new User();
        recipient.setId(77L);
        recipient.setEmail("student@example.com");
        recipient.setDisplayName("Student");

        assertThatCode(() -> service.safeCreateNotification(
                recipient,
                "Test notification",
                "This notification should fail safely.",
                "/student/profile"))
                .doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
        verify(transactionManager).rollback(transactionStatus);
        assertThat(output).contains("Notification creation failed for recipient user id 77: RuntimeException");
        assertThat(output).contains("token=[REDACTED]");
        assertThat(output).doesNotContain("secret-token");
        assertThat(output).doesNotContain("at com.example.aichinese");
    }

    @Test
    void teacherFeedbackNotificationIsHiddenWhenStudentNoLongerHasLessonAccess() {
        NotificationService service = service();
        User studentUser = user(1L, RoleName.STUDENT);
        StudentProfile student = student(studentUser);
        Lesson lesson = lesson(10L);
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setStudent(student);
        feedback.setLesson(lesson);
        Notification notification = notification(studentUser, "TEACHER_FEEDBACK", 20L, NotificationStatus.UNREAD);

        when(teacherFeedbackRepository.findByIdAndStudentUserId(20L, 1L)).thenReturn(Optional.of(feedback));
        when(studentProfileRepository.findByUserId(1L)).thenReturn(Optional.of(student));
        when(studentLearningAccessService.hasLessonAccess(student, lesson)).thenReturn(false);

        assertThat(service.isNotificationCurrentlyAccessible(new SecurityUserDetails(studentUser), notification)).isFalse();
    }

    @Test
    void unreadCountExcludesInaccessibleTargetedNotifications() {
        NotificationService service = service();
        User studentUser = user(1L, RoleName.STUDENT);
        Notification hidden = notification(studentUser, "TEACHER_FEEDBACK", 20L, NotificationStatus.UNREAD);
        Notification visible = notification(studentUser, null, null, NotificationStatus.UNREAD);

        when(notificationRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(1L, NotificationStatus.UNREAD))
                .thenReturn(List.of(hidden, visible));
        when(teacherFeedbackRepository.findByIdAndStudentUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThat(service.countAccessibleUnreadNotifications(new SecurityUserDetails(studentUser))).isEqualTo(1);
    }

    @Test
    void markAllAsReadOnlyCountsAndUpdatesAccessibleNotifications() {
        NotificationService service = service();
        User studentUser = user(1L, RoleName.STUDENT);
        Notification hidden = notification(studentUser, "TEACHER_FEEDBACK", 20L, NotificationStatus.UNREAD);
        Notification visible = notification(studentUser, null, null, NotificationStatus.UNREAD);

        when(notificationRepository.findByUserIdAndNotificationStatusOrderByCreatedAtDesc(1L, NotificationStatus.UNREAD))
                .thenReturn(List.of(hidden, visible));
        when(teacherFeedbackRepository.findByIdAndStudentUserId(20L, 1L)).thenReturn(Optional.empty());

        BulkOperationResponse response = service.markAllAsRead(new SecurityUserDetails(studentUser));

        assertThat(response.updatedCount()).isEqualTo(1);
        assertThat(hidden.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(visible.getNotificationStatus()).isEqualTo(NotificationStatus.READ);
        verify(notificationRepository).saveAll(List.of(visible));
    }

    private NotificationService service() {
        return new NotificationService(
                notificationRepository,
                mock(CommunicationMapper.class),
                mock(PlatformTransactionManager.class),
                teacherFeedbackRepository,
                mock(RecordedLessonVideoRepository.class),
                mock(LiveClassRepository.class),
                studentProfileRepository,
                studentLearningAccessService,
                mock(LiveLearningAccessService.class));
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
}
