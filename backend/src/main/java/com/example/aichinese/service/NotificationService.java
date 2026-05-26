package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.BulkOperationResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
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

/**
 * Writes and queries {@link Notification} rows — the in-app inbox
 * surface used by every persona.
 *
 * <h2>Producer surface</h2>
 * <p>Other services call this layer (instead of touching the
 * repository directly) so the notification dispatch is uniformly
 * audited and side-effects (e.g. real-time push, email fallback) can
 * be added without rewiring every producer. Producers cover all the
 * usual events: teacher approval, refund decisions, payout updates,
 * content moderation actions, teacher feedback, recorded video drops,
 * etc.</p>
 *
 * <h2>Consumer surface</h2>
 * <p>Paginated listing + mark-read / mark-all-read / unread-count
 * endpoints for the receiving persona. The mark-read flow uses a
 * separate transaction (via {@link TransactionTemplate}) so the
 * unread badge updates promptly without blocking on the surrounding
 * page-load transaction.</p>
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final CommunicationMapper communicationMapper;
    private final TransactionTemplate notificationTransactionTemplate;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final LiveClassRepository liveClassRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentLearningAccessService studentLearningAccessService;
    private final LiveLearningAccessService liveLearningAccessService;

    public NotificationService(
            NotificationRepository notificationRepository,
            CommunicationMapper communicationMapper,
            PlatformTransactionManager transactionManager,
            TeacherFeedbackRepository teacherFeedbackRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            LiveClassRepository liveClassRepository,
            StudentProfileRepository studentProfileRepository,
            StudentLearningAccessService studentLearningAccessService,
            LiveLearningAccessService liveLearningAccessService) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository, "notificationRepository");
        this.communicationMapper = Objects.requireNonNull(communicationMapper, "communicationMapper");
        this.notificationTransactionTemplate = new TransactionTemplate(Objects.requireNonNull(transactionManager, "transactionManager"));
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.teacherFeedbackRepository = Objects.requireNonNull(teacherFeedbackRepository, "teacherFeedbackRepository");
        this.recordedLessonVideoRepository = Objects.requireNonNull(recordedLessonVideoRepository, "recordedLessonVideoRepository");
        this.liveClassRepository = Objects.requireNonNull(liveClassRepository, "liveClassRepository");
        this.studentProfileRepository = Objects.requireNonNull(studentProfileRepository, "studentProfileRepository");
        this.studentLearningAccessService = Objects.requireNonNull(studentLearningAccessService, "studentLearningAccessService");
        this.liveLearningAccessService = Objects.requireNonNull(liveLearningAccessService, "liveLearningAccessService");
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
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Notification title must not be blank.");
        }
        if (message == null || message.isBlank()) {
            throw new IllegalArgumentException("Notification message must not be blank.");
        }

        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setTitle(title.strip());
        notification.setMessage(message.strip());
        notification.setNotificationType(notificationType);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setTargetType(targetType == null || targetType.isBlank() ? null : targetType.strip());
        notification.setTargetId(targetId);
        notification.setTargetPath(targetPath == null || targetPath.isBlank() ? null : targetPath.strip());
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
    public List<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser) {
        return getCurrentUserNotifications(currentUser, 0, 20).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        List<Notification> accessibleNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .filter(notification -> isNotificationCurrentlyAccessible(currentUser, notification))
                .toList();
        int startIndex = PaginationUtils.startIndex(safePage, safeSize, accessibleNotifications.size());
        int endIndex = PaginationUtils.endIndex(startIndex, safeSize, accessibleNotifications.size());
        return new PageResponse<>(
                accessibleNotifications.subList(startIndex, endIndex).stream()
                        .map(communicationMapper::toNotificationResponse)
                        .toList(),
                safePage,
                safeSize,
                accessibleNotifications.size(),
                PaginationUtils.totalPages(accessibleNotifications.size(), safeSize));
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(SecurityUserDetails currentUser) {
        return new NotificationUnreadCountResponse(countAccessibleUnreadNotifications(currentUser));
    }

    public long countAccessibleUnreadNotifications(SecurityUserDetails currentUser) {
        return notificationRepository
                .findByUserIdAndNotificationStatusOrderByCreatedAtDesc(currentUser.getUserId(), NotificationStatus.UNREAD)
                .stream()
                .filter(notification -> isNotificationCurrentlyAccessible(currentUser, notification))
                .count();
    }

    public boolean isNotificationCurrentlyAccessible(SecurityUserDetails currentUser, Notification notification) {
        if (notification.getTargetType() == null || notification.getTargetId() == null) {
            return true;
        }
        return switch (notification.getTargetType()) {
            case "TEACHER_FEEDBACK" -> isTeacherFeedbackNotificationAccessible(currentUser, notification);
            case "RECORDED_VIDEO" -> isRecordedVideoNotificationAccessible(notification);
            case "LIVE_CLASS" -> isLiveClassNotificationAccessible(notification);
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
        if (feedback.getLesson() != null) {
            return feedback.getLesson().getContentStatus() == ContentStatus.PUBLISHED
                    && studentLearningAccessService.hasLessonAccess(student, feedback.getLesson());
        }
        if (feedback.getQuizAttempt() != null
                && feedback.getQuizAttempt().getQuiz() != null
                && feedback.getQuizAttempt().getQuiz().getLesson() != null) {
            return feedback.getQuizAttempt().getQuiz().getLesson().getContentStatus() == ContentStatus.PUBLISHED
                    && studentLearningAccessService.hasLessonAccess(student, feedback.getQuizAttempt().getQuiz().getLesson());
        }
        return true;
    }

    private boolean isRecordedVideoNotificationAccessible(Notification notification) {
        StudentProfile student = resolveNotificationStudent(notification);
        RecordedLessonVideo video = recordedLessonVideoRepository.findByIdAndActiveTrue(notification.getTargetId()).orElse(null);
        if (student == null || video == null) {
            return false;
        }
        boolean lessonAccess = video.getLesson() == null
                || (video.getLesson().getContentStatus() == ContentStatus.PUBLISHED
                        && liveLearningAccessService.hasStudentLessonAccess(student, video.getLesson()));
        boolean packageAccess = video.getCoursePackage() == null
                || (video.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED
                        && liveLearningAccessService.hasStudentPackageAccess(student, video.getCoursePackage()));
        return lessonAccess && packageAccess;
    }

    private boolean isLiveClassNotificationAccessible(Notification notification) {
        StudentProfile student = resolveNotificationStudent(notification);
        Long targetId = notification.getTargetId();
        return student != null && targetId != null && liveClassRepository.findById(targetId)
                .map(liveClass -> {
                    boolean lessonAccess = liveClass.getLesson() == null
                            || (liveClass.getLesson().getContentStatus() == ContentStatus.PUBLISHED
                                    && liveLearningAccessService.hasStudentLessonAccess(student, liveClass.getLesson()));
                    boolean packageAccess = liveClass.getCoursePackage() == null
                            || (liveClass.getCoursePackage().getContentStatus() == ContentStatus.PUBLISHED
                                    && liveLearningAccessService.hasStudentPackageAccess(student, liveClass.getCoursePackage()));
                    return lessonAccess && packageAccess;
                })
                .orElse(false);
    }

    private StudentProfile resolveNotificationStudent(Notification notification) {
        if (notification.getUser() != null && notification.getUser().getRoles().stream()
                .anyMatch(role -> role.getName() == RoleName.STUDENT)) {
            return studentProfileRepository.findByUserId(notification.getUser().getId()).orElse(null);
        }
        return notification.getCreatedByUser() == null
                ? null
                : studentProfileRepository.findByUserId(notification.getCreatedByUser().getId()).orElse(null);
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
        List<Notification> accessibleUnreadNotifications = notificationRepository
                .findByUserIdAndNotificationStatusOrderByCreatedAtDesc(currentUser.getUserId(), NotificationStatus.UNREAD)
                .stream()
                .filter(notification -> isNotificationCurrentlyAccessible(currentUser, notification))
                .toList();
        accessibleUnreadNotifications.forEach(notification -> {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(readAt);
        });
        notificationRepository.saveAll(accessibleUnreadNotifications);
        return new BulkOperationResponse(accessibleUnreadNotifications.size());
    }
}
