package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
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

class NotificationServiceTests {

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TeacherFeedbackRepository teacherFeedbackRepository = mock(TeacherFeedbackRepository.class);
    private final StudentProfileRepository studentProfileRepository = mock(StudentProfileRepository.class);
    private final StudentLearningAccessService studentLearningAccessService = mock(StudentLearningAccessService.class);

    @Test
    void safeCreateNotificationDoesNotPropagateNotificationFailures() {
        NotificationRepository notificationRepository = mock(NotificationRepository.class);
        when(notificationRepository.save(any(Notification.class))).thenThrow(new RuntimeException("notification store failed"));
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

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(hidden, visible));
        when(teacherFeedbackRepository.findByIdAndStudentUserId(20L, 1L)).thenReturn(Optional.empty());

        assertThat(service.countAccessibleUnreadNotifications(new SecurityUserDetails(studentUser))).isEqualTo(1);
    }

    @Test
    void markAllAsReadOnlyCountsAndUpdatesAccessibleNotifications() {
        NotificationService service = service();
        User studentUser = user(1L, RoleName.STUDENT);
        Notification hidden = notification(studentUser, "TEACHER_FEEDBACK", 20L, NotificationStatus.UNREAD);
        Notification visible = notification(studentUser, null, null, NotificationStatus.UNREAD);

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L)).thenReturn(List.of(hidden, visible));
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
