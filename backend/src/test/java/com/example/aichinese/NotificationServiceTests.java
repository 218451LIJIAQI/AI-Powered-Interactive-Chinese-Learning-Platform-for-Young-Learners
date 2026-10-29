package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.stubbing.OngoingStubbing;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
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
    private static final long TEACHER_USER_ID = 77L;
    private static final long LESSON_ID = 10L;
    private static final long FEEDBACK_ID = 20L;
    private static final long LIVE_CLASS_ID = 33L;
    private static final String TITLE = "Title";
    private static final String MESSAGE = "Message";
    private static final String STUDENT_PATH = "/student";
    private static final String STUDENT_PROFILE_PATH = "/student/profile";
    private static final String LIVE_CLASS_TARGET = "LIVE_CLASS";
    private static final String RECORDED_VIDEO_TARGET = "RECORDED_VIDEO";

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final TeacherFeedbackRepository teacherFeedbackRepository = mock(TeacherFeedbackRepository.class);
    private final LiveClassRepository liveClassRepository = mock(LiveClassRepository.class);
    private final RecordedLessonVideoRepository recordedLessonVideoRepository = mock(RecordedLessonVideoRepository.class);
    private final User studentUser = user(STUDENT_USER_ID, RoleName.STUDENT);

    @Test
    void createNotificationRejectsInvalidRequiredFieldsBeforePersistence() {
        NotificationService service = service();

        assertThatThrownBy(() -> service.createNotification(null, NotificationType.SYSTEM, TITLE, MESSAGE, null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("recipient");
        assertThatThrownBy(() -> service.createNotification(studentUser, null, TITLE, MESSAGE, null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("type");
        assertThatThrownBy(() -> service.createNotification(studentUser, NotificationType.SYSTEM, " ", MESSAGE, null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("title");
        assertThatThrownBy(() -> service.createNotification(studentUser, NotificationType.SYSTEM, TITLE, "\t", null, null, STUDENT_PATH, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("message");
    }

    @Test
    void createNotificationTrimsDisplayFieldsAndOptionalTargets() {
        NotificationService service = service();
        when(notificationRepository.save(any(Notification.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.createNotification(
                studentUser, NotificationType.SYSTEM, "  Title  ", "\nMessage\n", " TARGET ", 42L, " /student/profile ", null);

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
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("notification store failed token=secret-token"));
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        TransactionStatus transactionStatus = mock(TransactionStatus.class);
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
        NotificationService service = service(transactionManager);

        User recipient = new User();
        recipient.setId(TEACHER_USER_ID);
        recipient.setEmail("student@example.com");
        recipient.setDisplayName("Student");

        assertThatCode(() -> service.safeCreateNotification(
                recipient, "Test notification", "This notification should fail safely.", STUDENT_PROFILE_PATH))
                .doesNotThrowAnyException();

        verify(notificationRepository).save(any(Notification.class));
        verify(transactionManager).rollback(transactionStatus);
        assertThat(output)
                .contains("Notification creation failed for recipient user id " + TEACHER_USER_ID + ": RuntimeException")
                .contains("token=[REDACTED]")
                .doesNotContain("secret-token")
                .doesNotContain("at com.example.aichinese");
    }

    @Test
    void teacherFeedbackNotificationIsHiddenWhenLessonIsNoLongerPublished() {
        stubOwnFeedback(feedback(student(studentUser), lesson(LESSON_ID, ContentStatus.ARCHIVED)));

        assertThat(service().isNotificationCurrentlyAccessible(
                new SecurityUserDetails(studentUser), unreadTeacherFeedbackNotification(studentUser))).isFalse();
    }

    @Test
    void teacherFeedbackNotificationIsVisibleForPublishedLesson() {
        stubOwnFeedback(feedback(student(studentUser), lesson(LESSON_ID, ContentStatus.PUBLISHED)));

        assertThat(service().isNotificationCurrentlyAccessible(
                new SecurityUserDetails(studentUser), unreadTeacherFeedbackNotification(studentUser))).isTrue();
    }

    @Test
    void teacherKeepsOwnLiveClassNotificationWhenTheClassIsNotStudentVisible() {
        NotificationService service = service();
        User teacherUser = user(TEACHER_USER_ID, RoleName.TEACHER);
        when(liveClassRepository.findByIdIn(anyCollection()))
                .thenReturn(List.of(liveClass(LIVE_CLASS_ID, lesson(LESSON_ID, ContentStatus.SUBMITTED))));
        // A student enrolled in the teacher's class while its lesson is back in review.
        Notification teacherNotification = unreadNotification(teacherUser, LIVE_CLASS_TARGET, LIVE_CLASS_ID);

        assertThat(service.isNotificationCurrentlyAccessible(new SecurityUserDetails(teacherUser), teacherNotification)).isTrue();
        // The class is not looked up for a teacher recipient.
        verify(liveClassRepository, never()).findByIdIn(anyCollection());

        // The same class is hidden from a student, so the teacher result above is not just a missing class.
        Notification studentNotification = unreadNotification(studentUser, LIVE_CLASS_TARGET, LIVE_CLASS_ID);
        assertThat(service.isNotificationCurrentlyAccessible(new SecurityUserDetails(studentUser), studentNotification)).isFalse();
    }

    /** FRNOTIF02: a student opens a live-class notification only while the class's lesson and course are visible. */
    @Test
    void studentLiveClassNotificationFollowsTheClassContentVisibility() {
        NotificationService service = service();
        Lesson publishedLesson = lesson(LESSON_ID, ContentStatus.PUBLISHED);
        LiveClass liveClass = liveClass(LIVE_CLASS_ID, publishedLesson);
        when(liveClassRepository.findByIdIn(anyCollection())).thenReturn(List.of(liveClass));
        Notification notification = unreadNotification(studentUser, LIVE_CLASS_TARGET, LIVE_CLASS_ID);

        assertThat(service.isNotificationCurrentlyAccessible(new SecurityUserDetails(studentUser), notification)).isTrue();

        // The class is also linked to a course that is not published (yet), which hides it again.
        CoursePackage draftCourse = new CoursePackage();
        draftCourse.setContentStatus(ContentStatus.DRAFT);
        draftCourse.setTeacher(publishedLesson.getTeacher());
        liveClass.setCoursePackage(draftCourse);
        assertThat(service.isNotificationCurrentlyAccessible(new SecurityUserDetails(studentUser), notification)).isFalse();
    }

    /**
     * FRNOTIF02: a student opens a recorded-video notification only while the video is active and its lesson is
     * visible; a deleted video hides it too. The video targets of a batch are loaded in one query.
     */
    @Test
    void studentRecordedVideoNotificationsAreHiddenWhenTheVideoIsNoLongerVisible() {
        Lesson publishedLesson = lesson(LESSON_ID, ContentStatus.PUBLISHED);
        when(recordedLessonVideoRepository.findByIdIn(anyCollection())).thenReturn(List.of(
                recordedVideo(40L, publishedLesson, true),
                recordedVideo(41L, publishedLesson, false),
                recordedVideo(42L, lesson(LESSON_ID + 1, ContentStatus.SUBMITTED), true)));
        Notification visible = unreadNotification(studentUser, RECORDED_VIDEO_TARGET, 40L);
        stubUnreadBatches(List.of(
                visible,
                unreadNotification(studentUser, RECORDED_VIDEO_TARGET, 41L),
                unreadNotification(studentUser, RECORDED_VIDEO_TARGET, 42L),
                // A deleted video.
                unreadNotification(studentUser, RECORDED_VIDEO_TARGET, 43L)));

        service().markAllAsRead(new SecurityUserDetails(studentUser));

        verify(notificationRepository).saveAll(List.of(visible));
        verify(recordedLessonVideoRepository, times(1)).findByIdIn(anyCollection());
    }

    @Test
    void liveClassTargetsOfABatchAreLoadedTogetherAndHiddenWhileTheirLessonIsNotPublished() {
        stubUnreadBatches(List.of(
                unreadNotification(studentUser, LIVE_CLASS_TARGET, 30L),
                unreadNotification(studentUser, LIVE_CLASS_TARGET, 31L),
                // A deleted class.
                unreadNotification(studentUser, LIVE_CLASS_TARGET, 32L)));
        when(liveClassRepository.findByIdIn(anyCollection())).thenReturn(List.of(
                liveClass(30L, lesson(LESSON_ID, ContentStatus.PUBLISHED)),
                liveClass(31L, lesson(LESSON_ID + 1, ContentStatus.SUBMITTED))));

        assertThat(service().getUnreadCount(new SecurityUserDetails(studentUser)).unreadCount()).isEqualTo(1);
        verify(liveClassRepository, times(1)).findByIdIn(anyCollection());
    }

    @Test
    void unreadCountExcludesInaccessibleTargetedNotifications() {
        stubUnreadBatches(List.of(
                unreadTeacherFeedbackNotification(studentUser), unreadNotification(studentUser, null, null)));
        stubOwnFeedback();

        assertThat(service().getUnreadCount(new SecurityUserDetails(studentUser)).unreadCount()).isEqualTo(1);
    }

    @Test
    void markAllAsReadOnlyUpdatesAccessibleNotifications() {
        Notification hidden = unreadTeacherFeedbackNotification(studentUser);
        Notification visible = unreadNotification(studentUser, null, null);
        stubUnreadBatches(List.of(hidden, visible));
        stubOwnFeedback();

        service().markAllAsRead(new SecurityUserDetails(studentUser));

        assertThat(hidden.getNotificationStatus()).isEqualTo(NotificationStatus.UNREAD);
        assertThat(visible.getNotificationStatus()).isEqualTo(NotificationStatus.READ);
        verify(notificationRepository).saveAll(List.of(visible));
    }

    @Test
    void unreadCountProcessesMoreThanOneRepositoryBatch() {
        List<Notification> firstBatch = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        for (long id = 300; id > 50; id--) {
            Notification notification = unreadNotification(studentUser, null, null);
            notification.setId(id);
            notification.setCreatedAt(now.minusSeconds(300 - id));
            firstBatch.add(notification);
        }
        Notification finalNotification = unreadNotification(studentUser, null, null);
        finalNotification.setId(50L);
        finalNotification.setCreatedAt(now.minusSeconds(250));
        stubUnreadBatches(firstBatch, List.of(finalNotification));

        assertThat(service().getUnreadCount(new SecurityUserDetails(studentUser)).unreadCount()).isEqualTo(251);
        verify(notificationRepository, times(2))
                .findUserStatusBatchBefore(any(), any(), nullable(LocalDateTime.class), any(), any());
    }

    private NotificationService service() {
        return service(mock(PlatformTransactionManager.class));
    }

    private NotificationService service(PlatformTransactionManager transactionManager) {
        return new NotificationService(
                notificationRepository,
                transactionManager,
                teacherFeedbackRepository,
                recordedLessonVideoRepository,
                liveClassRepository);
    }

    /** The unread notifications of the user, as the successive repository batches return them. */
    @SafeVarargs
    private void stubUnreadBatches(List<Notification> firstBatch, List<Notification>... laterBatches) {
        OngoingStubbing<List<Notification>> stubbing = when(notificationRepository.findUserStatusBatchBefore(
                any(), any(), nullable(LocalDateTime.class), any(), any())).thenReturn(firstBatch);
        for (List<Notification> batch : laterBatches) {
            stubbing = stubbing.thenReturn(batch);
        }
    }

    /** The feedback targets of a batch come from one query limited to the student's own feedback. */
    private void stubOwnFeedback(TeacherFeedback... feedback) {
        when(teacherFeedbackRepository.findAll(
                ArgumentMatchers.<Specification<TeacherFeedback>>any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(feedback)));
    }

    private static TeacherFeedback feedback(StudentProfile student, Lesson lesson) {
        TeacherFeedback feedback = new TeacherFeedback();
        feedback.setId(FEEDBACK_ID);
        feedback.setStudent(student);
        feedback.setLesson(lesson);
        return feedback;
    }

    private static LiveClass liveClass(Long id, Lesson lesson) {
        LiveClass liveClass = new LiveClass();
        liveClass.setId(id);
        liveClass.setLesson(lesson);
        return liveClass;
    }

    private static RecordedLessonVideo recordedVideo(Long id, Lesson lesson, boolean active) {
        RecordedLessonVideo video = new RecordedLessonVideo();
        video.setId(id);
        video.setLesson(lesson);
        video.setActive(active);
        return video;
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

    /** A lesson of an approved, active teacher (student visibility needs both, FRTVER04). */
    private static Lesson lesson(Long id, ContentStatus status) {
        User teacherUser = new User();
        teacherUser.setActive(true);
        TeacherProfile teacher = new TeacherProfile();
        teacher.setUser(teacherUser);
        teacher.setTeacherStatus(TeacherStatus.APPROVED);
        Lesson lesson = new Lesson();
        lesson.setId(id);
        lesson.setTitle("Lesson " + id);
        lesson.setTeacher(teacher);
        lesson.setContentStatus(status);
        return lesson;
    }

    private static Notification unreadNotification(User user, String targetType, Long targetId) {
        Notification notification = new Notification();
        notification.setUser(user);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        return notification;
    }

    private static Notification unreadTeacherFeedbackNotification(User user) {
        return unreadNotification(user, "TEACHER_FEEDBACK", FEEDBACK_ID);
    }
}
