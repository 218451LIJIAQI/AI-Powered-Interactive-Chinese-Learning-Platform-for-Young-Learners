package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.RecordedLessonVideo;
import com.example.aichinese.entity.TeacherFeedback;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.RecordedLessonVideoRepository;
import com.example.aichinese.repository.TeacherFeedbackRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.PaginationUtils;
import com.example.aichinese.util.TextUtils;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final String TARGET_TEACHER_FEEDBACK = "TEACHER_FEEDBACK";
    private static final String TARGET_RECORDED_VIDEO = "RECORDED_VIDEO";
    private static final String TARGET_LIVE_CLASS = "LIVE_CLASS";
    private static final int NOTIFICATION_BATCH_SIZE = 250;

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate notificationTransactionTemplate;
    private final TeacherFeedbackRepository teacherFeedbackRepository;
    private final RecordedLessonVideoRepository recordedLessonVideoRepository;
    private final LiveClassRepository liveClassRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            PlatformTransactionManager transactionManager,
            TeacherFeedbackRepository teacherFeedbackRepository,
            RecordedLessonVideoRepository recordedLessonVideoRepository,
            LiveClassRepository liveClassRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationTransactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.teacherFeedbackRepository = teacherFeedbackRepository;
        this.recordedLessonVideoRepository = recordedLessonVideoRepository;
        this.liveClassRepository = liveClassRepository;
    }

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
        notification.setTargetType(TextUtils.trimToNull(targetType));
        notification.setTargetId(targetId);
        notification.setTargetPath(TextUtils.trimToNull(targetPath));
        notification.setCreatedByUser(createdByUser);
        return notificationRepository.save(notification);
    }

    public void safeCreateNotification(User recipient, String title, String message, String targetPath) {
        safeCreateNotification(recipient, NotificationType.SYSTEM, title, message, null, null, targetPath, null);
    }

    /**
     * Best-effort notification: written in a transaction of its own after the caller's transaction commits (at once
     * when there is none), so a failure is only logged and never fails or rolls back the caller.
     */
    public void safeCreateNotification(
            User recipient,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            User createdByUser) {
        Runnable committedWrite = () -> {
            try {
                notificationTransactionTemplate.executeWithoutResult(status ->
                        createNotification(recipient, notificationType, title, message, targetType, targetId, targetPath, createdByUser));
            } catch (Exception exception) {
                Long recipientId = recipient != null ? recipient.getId() : null;
                log.warn("Notification creation failed for recipient user id {}: {}", recipientId, ExceptionLogUtils.safeSummary(exception));
                log.debug("Notification creation stack trace for recipient user id {}.", recipientId, exception);
            }
        };

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

    /** Notifications page (FRNOTIF02): optional READ / UNREAD filter, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(
            SecurityUserDetails currentUser,
            NotificationStatus status,
            int page,
            int size) {
        int safePage = PaginationUtils.sanitizePage(page);
        int safeSize = PaginationUtils.sanitizeSize(size);
        long requestedStart = (long) safePage * safeSize;
        AtomicLong accessibleCount = new AtomicLong();
        List<NotificationResponse> items = new ArrayList<>(safeSize);
        forEachAccessibleBatch(currentUser, status, accessible -> {
            for (Notification notification : accessible) {
                if (accessibleCount.getAndIncrement() >= requestedStart && items.size() < safeSize) {
                    items.add(toNotificationResponse(notification));
                }
            }
        });
        return PageResponse.of(items, safePage, safeSize, accessibleCount.get());
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(SecurityUserDetails currentUser) {
        AtomicLong count = new AtomicLong();
        forEachAccessibleBatch(currentUser, NotificationStatus.UNREAD, accessible -> count.addAndGet(accessible.size()));
        return new NotificationUnreadCountResponse(count.get());
    }

    public boolean isNotificationCurrentlyAccessible(SecurityUserDetails currentUser, Notification notification) {
        return accessibilityFilter(currentUser, List.of(notification)).test(notification);
    }

    /**
     * Tells which notifications of one batch the user may still open. The content-visibility gate protects
     * students only: a teacher's notifications about their own class or video (an enrolment, a completed video)
     * stay visible while the lesson is back in review. The targets of the batch are loaded together, one query
     * per target type.
     */
    private Predicate<Notification> accessibilityFilter(SecurityUserDetails currentUser, List<Notification> batch) {
        if (!currentUser.getRoleNames().contains(RoleName.STUDENT.name())) {
            return notification -> true;
        }
        Set<Long> visibleVideoIds = visibleTargetIds(
                batch,
                TARGET_RECORDED_VIDEO,
                recordedLessonVideoRepository::findByIdIn,
                video -> video.isActive() && ContentVisibilityPolicy.isRecordedVideoVisible(video),
                RecordedLessonVideo::getId);
        Set<Long> visibleLiveClassIds = visibleTargetIds(
                batch,
                TARGET_LIVE_CLASS,
                liveClassRepository::findByIdIn,
                ContentVisibilityPolicy::isLiveClassContentVisible,
                LiveClass::getId);
        Set<Long> visibleFeedbackIds = visibleTargetIds(
                batch,
                TARGET_TEACHER_FEEDBACK,
                feedbackIds -> teacherFeedbackRepository
                        .findAll(ownFeedback(feedbackIds, currentUser.getUserId()), Pageable.unpaged())
                        .getContent(),
                NotificationService::isStudentFeedbackCurrentlyAccessible,
                TeacherFeedback::getId);
        return notification -> {
            Long targetId = notification.getTargetId();
            if (notification.getTargetType() == null || targetId == null) {
                return true;
            }
            return switch (notification.getTargetType()) {
                case TARGET_TEACHER_FEEDBACK -> visibleFeedbackIds.contains(targetId);
                case TARGET_RECORDED_VIDEO -> visibleVideoIds.contains(targetId);
                case TARGET_LIVE_CLASS -> visibleLiveClassIds.contains(targetId);
                default -> true;
            };
        };
    }

    /** Ids of the batch targets of one type that exist and pass {@code visible}; a missing target is not visible. */
    private static <T> Set<Long> visibleTargetIds(
            List<Notification> batch,
            String targetType,
            Function<Collection<Long>, List<T>> loadAll,
            Predicate<T> visible,
            Function<T, Long> idOf) {
        Set<Long> targetIds = batch.stream()
                .filter(notification -> targetType.equals(notification.getTargetType()) && notification.getTargetId() != null)
                .map(Notification::getTargetId)
                .collect(Collectors.toSet());
        if (targetIds.isEmpty()) {
            return Set.of();
        }
        return loadAll.apply(targetIds).stream()
                .filter(visible)
                .map(idOf)
                .collect(Collectors.toSet());
    }

    /** The given feedback written to the student with this user id; feedback to anyone else is never returned. */
    private static Specification<TeacherFeedback> ownFeedback(Collection<Long> feedbackIds, Long studentUserId) {
        return (root, query, cb) -> cb.and(
                root.get("id").in(feedbackIds),
                cb.equal(root.get("student").get("user").get("id"), studentUserId));
    }

    /** The feedback was found by the current student's user id, so the student profile is known to exist. */
    private static boolean isStudentFeedbackCurrentlyAccessible(TeacherFeedback feedback) {
        Lesson lesson = feedback.getLesson() != null || feedback.getQuizAttempt() == null
                ? feedback.getLesson()
                : feedback.getQuizAttempt().getQuiz().getLesson();
        return lesson == null || ContentVisibilityPolicy.isLessonVisible(lesson);
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

        return toNotificationResponse(notification);
    }

    @Transactional
    public void markAllAsRead(SecurityUserDetails currentUser) {
        LocalDateTime readAt = LocalDateTime.now();
        forEachAccessibleBatch(currentUser, NotificationStatus.UNREAD, accessible -> {
            accessible.forEach(notification -> {
                notification.setNotificationStatus(NotificationStatus.READ);
                notification.setReadAt(readAt);
            });
            notificationRepository.saveAll(accessible);
        });
    }

    /**
     * Walks the user's notifications (of one status, or all when {@code status} is null) newest first, one batch at
     * a time, and hands each batch's still-accessible notifications to {@code handler}.
     */
    private void forEachAccessibleBatch(
            SecurityUserDetails currentUser,
            NotificationStatus status,
            Consumer<List<Notification>> handler) {
        NotificationCursor cursor = NotificationCursor.initial();
        while (true) {
            List<Notification> batch = loadNotificationBatch(currentUser.getUserId(), status, cursor);
            if (batch.isEmpty()) {
                return;
            }
            handler.accept(batch.stream().filter(accessibilityFilter(currentUser, batch)).toList());
            if (batch.size() < NOTIFICATION_BATCH_SIZE) {
                return;
            }
            cursor = NotificationCursor.before(batch.get(batch.size() - 1));
        }
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

    private static NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType().name(),
                notification.getNotificationStatus().name(),
                notification.getTargetPath(),
                notification.getCreatedAt(),
                notification.getReadAt());
    }

    private static String requireText(String value, String fieldName) {
        String stripped = TextUtils.trimToNull(value);
        if (stripped == null) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
        return stripped;
    }
}
