package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.CoursePackage;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.PaginationUtils;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String TARGET_TEACHER_FEEDBACK = "TEACHER_FEEDBACK";
    private static final String TARGET_RECORDED_VIDEO = "RECORDED_VIDEO";
    private static final String TARGET_LIVE_CLASS = "LIVE_CLASS";
    private static final int NOTIFICATION_BATCH_SIZE = 250;

    private final NotificationRepository notificationRepository;
    private final CommunicationMapper communicationMapper;
    private final TransactionTemplate notificationTransactionTemplate;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final LiveClassRepository liveClassRepository;
    private final StudentProfileRepository studentProfileRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            CommunicationMapper communicationMapper,
            PlatformTransactionManager transactionManager,
            TeacherFeedbackRepository teacherFeedbackRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            LiveClassRepository liveClassRepository,
            StudentProfileRepository studentProfileRepository) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
        this.notificationTransactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
    }

    @Transactional
    public void createNotification(User recipient, String title, String message, String targetPath) {
        createNotification(recipient, NotificationType.SYSTEM, title, message, null, null, targetPath, null);
    }

    @Transactional
    public Notification createNotification(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        if (recipient == null) {
            throw new IllegalArgumentException("Notification recipient must not be null.");
        }
        if (notificationType == null) {
            throw new IllegalArgumentException("Notification type must not be null.");
        }
        String normalizedTitle = requireText(title, "Notification title");
        String normalizedMessage = requireText(message, "Notification message");

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setTitle(normalizedTitle);
        notification.setMessage(normalizedMessage);
        notification.setNotificationType(notificationType);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setTargetType(optionalText(targetType));
        notification.setTargetId(targetId);
        notification.setTargetPath(optionalText(targetPath));
        notification.setCreatedByUser(createdByUser);
        notification.setReadAt(null);
        return notificationRepository.save(notification);
    }

    public void safeCreateNotification(User recipient, String title, String message, String targetPath) {
        safeCreateNotification(recipient, NotificationType.SYSTEM, title, message, null, null, targetPath, null);
    }

    public void safeCreateNotification(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        Runnable committedWrite = () -> createNotificationInIndependentTransaction(
                recipient,
                notificationType,
                title,
                message,
                targetType,
                targetId,
                targetPath,
                createdByUser);

        if (TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    committedWrite.run();
                }
            });
            return;
        }

        committedWrite.run();
    }

    private void createNotificationInIndependentTransaction(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        try {
            notificationTransactionTemplate.executeWithoutResult(status ->
                    createNotification(recipient, notificationType, title, message, targetType, targetId, targetPath, createdByUser));
        } catch (Exception exception) {
            Long recipientId = recipient != null ? recipient.getId() : null;
            log.warn("Notification creation failed for recipient user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
            log.debug("Notification creation stack trace for recipient user id {}.", recipientId, exception);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser, int page, int size) {
        return getCurrentUserNotifications(currentUser, null, page, size);
    }

    /** Notifications page (D20): optional READ / UNREAD filter, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(
            SecurityUserDetails currentUser,
            NotificationStatus status,
            int page,
            int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        long requestedStart = (long) safePage * safeSize;
        long accessibleCount = 0;
        List<NotificationResponse> items = new ArrayList<>(safeSize);
        NotificationCursor cursor = NotificationCursor.initial();
        while (true) {
            List<Notification> batch = loadNotificationBatch(currentUser.getUserId(), status, cursor);
            if (batch.isEmpty()) {
                break;
            }
            for (Notification notification : batch) {
                if (!isNotificationCurrentlyAccessible(currentUser, notification)) {
                    continue;
                }
                if (accessibleCount >= requestedStart && items.size() < safeSize) {
                    items.add(communicationMapper.toNotificationResponse(notification));
                }
                accessibleCount++;
            }
            cursor = NotificationCursor.before(batch.get(batch.size() - 1));
            if (batch.size() < NOTIFICATION_BATCH_SIZE) {
                break;
            }
        }
        return PageResponse.of(items, safePage, safeSize, accessibleCount);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(SecurityUserDetails currentUser) {
        return new NotificationUnreadCountResponse(countAccessibleUnreadNotifications(currentUser));
    }

    public long countAccessibleUnreadNotifications(SecurityUserDetails currentUser) {
        long count = 0;
        NotificationCursor cursor = NotificationCursor.initial();
        while (true) {
            List<Notification> batch = loadNotificationBatch(
                    currentUser.getUserId(), NotificationStatus.UNREAD, cursor);
            if (batch.isEmpty()) {
                break;
            }
            count += batch.stream()
                    .filter(notification -> isNotificationCurrentlyAccessible(currentUser, notification))
                    .count();
            cursor = NotificationCursor.before(batch.get(batch.size() - 1));
            if (batch.size() < NOTIFICATION_BATCH_SIZE) {
                break;
            }
        }
        return count;
    }

    public List<Notification> findRecentAccessibleNotifications(SecurityUserDetails currentUser, int limit) {
        int safeLimit = Math.min(Math.max(limit, 0), PaginationUtils.DEFAULT_MAX_PAGE_SIZE);
        if (safeLimit == 0) {
            return List.of();
        }
        List<Notification> result = new ArrayList<>(safeLimit);
        NotificationCursor cursor = NotificationCursor.initial();
        while (result.size() < safeLimit) {
            List<Notification> batch = loadNotificationBatch(currentUser.getUserId(), null, cursor);
            if (batch.isEmpty()) {
                break;
            }
            for (Notification notification : batch) {
                if (isNotificationCurrentlyAccessible(currentUser, notification)) {
                    result.add(notification);
                    if (result.size() == safeLimit) {
                        break;
                    }
                }
            }
            cursor = NotificationCursor.before(batch.get(batch.size() - 1));
            if (batch.size() < NOTIFICATION_BATCH_SIZE) {
                break;
            }
        }
        return List.copyOf(result);
    }

    public boolean isNotificationCurrentlyAccessible(SecurityUserDetails currentUser, Notification notification) {
        if (notification.getTargetType() == null || notification.getTargetId() == null) {
            return true;
        }
        return switch (notification.getTargetType()) {
            case TARGET_TEACHER_FEEDBACK -> isTeacherFeedbackNotificationAccessible(currentUser, notification);
            case TARGET_RECORDED_VIDEO -> isRecordedVideoNotificationAccessible(notification);
            case TARGET_LIVE_CLASS -> isLiveClassNotificationAccessible(notification);
            default -> true;
        };
    }

    private boolean isTeacherFeedbackNotificationAccessible(SecurityUserDetails currentUser, Notification notification) {
        return teacherFeedbackRepository.findByIdAndStudentUserId(notification.getTargetId(), currentUser.getUserId())
                .map(feedback -> isStudentFeedbackCurrentlyAccessible(currentUser, feedback))
                .orElse(false);
    }

    private boolean isStudentFeedbackCurrentlyAccessible(SecurityUserDetails currentUser, TeacherFeedback feedback) {
        StudentProfile student = studentProfileRepository.findByUserId(currentUser.getUserId()).orElse(null);
        if (student == null) {
            return false;
        }
        Lesson feedbackLesson = feedback.getLesson();
        if (feedbackLesson != null) {
            return isPublishedLesson(feedbackLesson);
        }
        Lesson quizLesson = feedback.getQuizAttempt() == null || feedback.getQuizAttempt().getQuiz() == null
                ? null
                : feedback.getQuizAttempt().getQuiz().getLesson();
        if (quizLesson != null) {
            return isPublishedLesson(quizLesson);
        }
        return true;
    }

    private boolean isRecordedVideoNotificationAccessible(Notification notification) {
        StudentProfile student = resolveNotificationStudent(notification);
        RecordedLessonVideo video = recordedLessonVideoRepository.findByIdAndActiveTrue(notification.getTargetId()).orElse(null);
        if (student == null || video == null) {
            return false;
        }
        return isPublishedOrAbsent(video.getLesson())
                && isPublishedOrAbsent(video.getCoursePackage());
    }

    private boolean isLiveClassNotificationAccessible(Notification notification) {
        StudentProfile student = resolveNotificationStudent(notification);
        Long targetId = notification.getTargetId();
        return student != null && targetId != null && liveClassRepository.findById(targetId)
                .map(liveClass -> isPublishedOrAbsent(liveClass.getLesson())
                        && isPublishedOrAbsent(liveClass.getCoursePackage()))
                .orElse(false);
    }

    private static boolean isPublishedLesson(Lesson lesson) {
        return ContentVisibilityPolicy.isLessonVisible(lesson);
    }

    private static boolean isPublishedOrAbsent(Lesson lesson) {
        return lesson == null || ContentVisibilityPolicy.isLessonVisible(lesson);
    }

    private static boolean isPublishedOrAbsent(CoursePackage coursePackage) {
        return coursePackage == null || ContentVisibilityPolicy.isPackageVisible(coursePackage);
    }

    private StudentProfile resolveNotificationStudent(Notification notification) {
        if (hasRole(notification.getUser(), RoleName.STUDENT)) {
            return studentProfileRepository.findByUserId(notification.getUser().getId()).orElse(null);
        }
        return notification.getCreatedByUser() == null
                ? null
                : studentProfileRepository.findByUserId(notification.getCreatedByUser().getId()).orElse(null);
    }

    private static boolean hasRole(User user, RoleName roleName) {
        return user != null && user.getRoles().stream()
                .anyMatch(role -> role.getName() == roleName);
    }

    @Transactional
    public NotificationResponse markAsRead(SecurityUserDetails currentUser, Long notificationId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification was not found."));
        if (!isNotificationCurrentlyAccessible(currentUser, notification)) {
            throw new ResponseStatusException(NOT_FOUND, "Notification was not found.");
        }

        if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
        }

        return communicationMapper.toNotificationResponse(notification);
    }

    @Transactional
    public BulkOperationResponse markAllAsRead(SecurityUserDetails currentUser) {
        LocalDateTime readAt = LocalDateTime.now();
        long updatedCount = 0;
        NotificationCursor cursor = NotificationCursor.initial();
        while (true) {
            List<Notification> batch = loadNotificationBatch(
                    currentUser.getUserId(), NotificationStatus.UNREAD, cursor);
            if (batch.isEmpty()) {
                break;
            }
            List<Notification> accessible = batch.stream()
                    .filter(notification -> isNotificationCurrentlyAccessible(currentUser, notification))
                    .toList();
            accessible.forEach(notification -> {
                notification.setNotificationStatus(NotificationStatus.READ);
                notification.setReadAt(readAt);
            });
            notificationRepository.saveAll(accessible);
            updatedCount += accessible.size();
            cursor = NotificationCursor.before(batch.get(batch.size() - 1));
            if (batch.size() < NOTIFICATION_BATCH_SIZE) {
                break;
            }
        }
        return new BulkOperationResponse(updatedCount);
    }

    private List<Notification> loadNotificationBatch(
            Long userId, NotificationStatus status, NotificationCursor cursor) {
        PageRequest page = PageRequest.of(0, NOTIFICATION_BATCH_SIZE);
        if (status == null) {
            return notificationRepository.findUserBatchBefore(
                    userId, cursor.createdAt(), cursor.id(), page);
        }
        return notificationRepository.findUserStatusBatchBefore(
                userId, status, cursor.createdAt(), cursor.id(), page);
    }

    private record NotificationCursor(LocalDateTime createdAt, Long id) {
        static NotificationCursor initial() {
            return new NotificationCursor(null, Long.MAX_VALUE);
        }

        static NotificationCursor before(Notification notification) {
            return new NotificationCursor(notification.getCreatedAt(), notification.getId());
        }
    }

    private static String requireText(String value, String fieldName) {
        String stripped = optionalText(value);
        if (stripped == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return stripped;
    }

    private static String optionalText(String value) {
        if (value == null) {
            return null;
        }
        String stripped = value.strip();
        return stripped.isEmpty() ? null : stripped;
    }
}
