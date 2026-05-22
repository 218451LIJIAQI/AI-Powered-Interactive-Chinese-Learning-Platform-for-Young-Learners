package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;

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
import com.example.aichinese.util.PaginationUtils;

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
        this.notificationRepository = notificationRepository;
        this.communicationMapper = communicationMapper;
        this.notificationTransactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.teacherFeedbackRepository = teacherFeedbackRepository;
        this.recordedLessonVideoRepository = recordedLessonVideoRepository;
        this.liveClassRepository = liveClassRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.studentLearningAccessService = studentLearningAccessService;
        this.liveLearningAccessService = liveLearningAccessService;
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
        Notification notification = new Notification();
        notification.setUser(recipient);
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(notificationType);
        notification.setNotificationStatus(NotificationStatus.UNREAD);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTargetPath(targetPath);
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
            log.warn("Notification creation failed for recipient user id {}.", recipient != null ? recipient.getId() : null, exception);
        }
    }

    @Transactional(readOnly = true)
    public java.util.List<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser) {
        return getCurrentUserNotifications(currentUser, 0, 20).items();
    }

    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(SecurityUserDetails currentUser, int page, int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        java.util.List<Notification> accessibleNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
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
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .filter(notification -> notification.getNotificationStatus() == NotificationStatus.UNREAD)
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
        java.util.List<Notification> accessibleUnreadNotifications = notificationRepository
                .findByUserIdOrderByCreatedAtDesc(currentUser.getUserId()).stream()
                .filter(notification -> notification.getNotificationStatus() == NotificationStatus.UNREAD)
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
