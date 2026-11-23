package com.example.aichinese.service;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.PageResponse;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.communication.NotificationUnreadCountResponse;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.util.AfterCommit;
import com.example.aichinese.util.ExceptionLogUtils;
import com.example.aichinese.util.PaginationUtils;

/**
 * In-app notifications (FRNOTIF01-02): writing them, the notifications page, the unread count, and marking them read
 * (one, all) or unread again (the Undo of "Mark all as read").
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    /** Maximum ids per status update, including a single Undo request. */
    private static final int STATUS_BATCH_SIZE = 1000;
    /** Recipients locked and persisted per REQUIRES_NEW transaction during bulk learner delivery. */
    private static final int DELIVERY_BATCH_SIZE = 200;

    private final NotificationRepository notificationRepository;
    private final TransactionTemplate notificationTransactionTemplate;
    private final UserRepository userRepository;

    public NotificationService(
            NotificationRepository notificationRepository,
            PlatformTransactionManager transactionManager,
            UserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.notificationTransactionTemplate = new TransactionTemplate(transactionManager);
        this.notificationTransactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.userRepository = userRepository;
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
            String targetPath) {
        saveAfterCommit(recipient.getId(), draft(notificationType, title, message, targetType, targetId, targetPath));
    }

    /**
     * Best-effort batch notification for multiple recipients: locks each chunk in ascending id order under a single
     * {@code REQUIRES_NEW} transaction per chunk after the caller commits, avoiding N+1 transactions.
     */
    public void safeCreateNotifications(
            Supplier<List<Long>> findRecipientIds,
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath) {
        AfterCommit.run(() -> {
            List<Long> recipientIds;
            try {
                recipientIds = notificationTransactionTemplate.execute(status -> findRecipientIds.get());
            } catch (RuntimeException exception) {
                log.warn("Notification recipient lookup failed for {}:{}: {}",
                        targetType, targetId, ExceptionLogUtils.safeSummary(exception));
                ExceptionLogUtils.debugOrigin(log, exception);
                return;
            }
            deliverInChunks(recipientIds, recipient -> draft(
                    notificationType, title, message, targetType, targetId, targetPath), targetType + ":" + targetId);
        });
    }

    /**
     * Best-effort batch notification when each recipient gets a personalized {@link #draft} (e.g. scheduled reminders):
     * locks each chunk in ascending id order under one {@code REQUIRES_NEW} transaction per chunk after commit.
     */
    public void safeCreatePersonalizedNotifications(
            Map<Long, Notification> notificationsByRecipientId,
            NotificationType notificationType) {
        if (notificationsByRecipientId == null || notificationsByRecipientId.isEmpty()) {
            return;
        }
        Map<Long, Notification> snapshot = Map.copyOf(notificationsByRecipientId);
        AfterCommit.run(() -> deliverInChunks(
                snapshot.keySet(), recipient -> snapshot.get(recipient.getId()), notificationType.name()));
    }

    /** Builds an unsaved notification so callers can supply distinct per-recipient texts to batch delivery. */
    public static Notification draft(NotificationType type, String title, String message,
            String targetType, Long targetId, String targetPath) {
        Notification notification = new Notification();
        notification.setTitle(title);
        notification.setMessage(message);
        notification.setNotificationType(type);
        notification.setTargetType(targetType);
        notification.setTargetId(targetId);
        notification.setTargetPath(targetPath);
        return notification;
    }

    private void deliverInChunks(
            Collection<Long> recipientIds,
            Function<User, Notification> buildNotification,
            String logContext) {
        List<Long> orderedIds = new ArrayList<>(new TreeSet<>(recipientIds));
        for (int start = 0; start < orderedIds.size(); start += DELIVERY_BATCH_SIZE) {
            List<Long> chunk = orderedIds.subList(start, Math.min(start + DELIVERY_BATCH_SIZE, orderedIds.size()));
            runBestEffort(() -> {
                List<Notification> items = new ArrayList<>(chunk.size());
                for (User recipient : userRepository.findWithReadLockByIdInOrderByIdAsc(chunk)) {
                    if (recipient.getDeletedAt() == null && recipient.isActive()) {
                        Notification item = buildNotification.apply(recipient);
                        item.setUser(recipient);
                        items.add(item);
                    }
                }
                if (!items.isEmpty()) {
                    notificationRepository.saveAll(items);
                }
            }, exception -> log.warn("Batch notification creation failed for {} ({} recipients): {}",
                    logContext, chunk.size(), ExceptionLogUtils.safeSummary(exception)));
        }
    }

    private void saveAfterCommit(Long recipientId, Notification notification) {
        AfterCommit.run(() -> runBestEffort(() -> {
            // Account deletion removes notices too; single-account notices still allow inactive recipients (e.g.,
            // reactivation or review updates), whereas batch broadcasts/reminders in deliverInChunks skip inactive accounts.
            User recipient = userRepository.findWithReadLockById(recipientId).orElse(null);
            if (recipient != null && recipient.getDeletedAt() == null) {
                notification.setUser(recipient);
                notificationRepository.save(notification);
            }
        }, exception -> log.warn("Notification creation failed for recipient user id {}: {}",
                recipientId, ExceptionLogUtils.safeSummary(exception))));
    }

    /** Checks the target under its read lock before saving: deletion may have removed these notices since commit. */
    public void notifyActiveAdmins(
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            BooleanSupplier targetStillVisible) {
        saveForActiveAdmins(notificationType, title, message, targetType, targetId, targetPath, null, targetStillVisible);
    }

    /**
     * Notifies every active admin when the target belongs to {@code targetUserId}. Locks {@code targetUserId} together
     * with the admin rows in ascending id order so concurrent account deletion can never deadlock with this callback.
     */
    public void notifyActiveAdminsForUserTarget(
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            Long targetUserId) {
        saveForActiveAdmins(notificationType, title, message, targetType, targetId, targetPath, targetUserId, () -> true);
    }

    private void saveForActiveAdmins(
            NotificationType notificationType,
            String title,
            String message,
            String targetType,
            Long targetId,
            String targetPath,
            Long targetUserId,
            BooleanSupplier targetStillVisible) {
        AfterCommit.run(() -> runBestEffort(() -> {
            // Even a failed recipient lookup must leave the support request or AI report committed.
            List<Long> adminIds = userRepository.findActiveIdsByRoleName(RoleName.ADMIN);
            if (adminIds.isEmpty()) {
                return;
            }
            TreeSet<Long> sortedIds = new TreeSet<>(adminIds);
            if (targetUserId != null) {
                sortedIds.add(targetUserId);
            }
            Map<Long, User> lockedUsers = userRepository.findWithReadLockByIdInOrderByIdAsc(List.copyOf(sortedIds)).stream()
                    .collect(Collectors.toMap(User::getId, Function.identity()));
            if (targetUserId != null) {
                User targetUser = lockedUsers.get(targetUserId);
                if (targetUser == null || targetUser.getDeletedAt() != null) {
                    return;
                }
            }
            if (!targetStillVisible.getAsBoolean()) {
                return;
            }
            List<Notification> items = new ArrayList<>(adminIds.size());
            for (Long adminId : adminIds) {
                User admin = lockedUsers.get(adminId);
                if (admin != null && admin.getDeletedAt() == null && admin.isActive()) {
                    Notification item = draft(
                            notificationType, title, message, targetType, targetId, targetPath);
                    item.setUser(admin);
                    items.add(item);
                }
            }
            if (!items.isEmpty()) {
                notificationRepository.saveAll(items);
            }
        }, exception -> log.warn("Admin notification creation failed for {}:{}: {}",
                targetType, targetId, ExceptionLogUtils.safeSummary(exception))));
    }

    private void runBestEffort(Runnable work, Consumer<Exception> logFailure) {
        try {
            notificationTransactionTemplate.executeWithoutResult(status -> work.run());
        } catch (Exception exception) {
            logFailure.accept(exception);
            ExceptionLogUtils.debugOrigin(log, exception);
        }
    }

    /** Notifications page (FRNOTIF02): optional READ / UNREAD filter, newest first. */
    @Transactional(readOnly = true)
    public PageResponse<NotificationResponse> getCurrentUserNotifications(
            SecurityUserDetails currentUser,
            NotificationStatus status,
            int page,
            int size) {
        return PageResponse.from(
                PaginationUtils.readPage(PaginationUtils.newestFirst(page, size),
                        request -> notificationRepository.findByUserIdAndStatus(currentUser.getUserId(), status, request)),
                NotificationService::toNotificationResponse);
    }

    @Transactional(readOnly = true)
    public NotificationUnreadCountResponse getUnreadCount(SecurityUserDetails currentUser) {
        return new NotificationUnreadCountResponse(
                notificationRepository.countByUserIdAndNotificationStatus(currentUser.getUserId(), NotificationStatus.UNREAD));
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public NotificationResponse markAsRead(SecurityUserDetails currentUser, Long notificationId) {
        Notification notification = notificationRepository.findLockedByIdAndUserId(notificationId, currentUser.getUserId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Notification was not found."));

        if (notification.getNotificationStatus() == NotificationStatus.UNREAD) {
            notification.setNotificationStatus(NotificationStatus.READ);
            notification.setReadAt(LocalDateTime.now());
        }

        return toNotificationResponse(notification);
    }

    /** Marks every unread notification of the user read; returns their ids (for the Undo, {@link #markUnread}). */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Long> markAllAsRead(SecurityUserDetails currentUser) {
        List<Long> marked = notificationRepository.lockUnreadIds(currentUser.getUserId());
        LocalDateTime readAt = LocalDateTime.now();
        for (int start = 0; start < marked.size(); start += STATUS_BATCH_SIZE) {
            int end = Math.min(start + STATUS_BATCH_SIZE, marked.size());
            notificationRepository.markLockedRead(currentUser.getUserId(), marked.subList(start, end), readAt);
        }
        return marked;
    }

    /**
     * Marks the given notifications of the user unread again (the Undo of "Mark all as read"); ids of other users'
     * notifications, unknown ids and notifications already unread are left alone. Returns the ids it marked.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<Long> markUnread(SecurityUserDetails currentUser, Collection<Long> ids) {
        if (ids.size() > STATUS_BATCH_SIZE) {
            throw new ResponseStatusException(BAD_REQUEST,
                    "Too many notifications to mark as unread at once.");
        }
        if (ids.isEmpty()) {
            return List.of();
        }
        List<Long> marked = notificationRepository.lockReadIds(currentUser.getUserId(), ids);
        if (!marked.isEmpty()) {
            notificationRepository.markLockedUnread(currentUser.getUserId(), marked, LocalDateTime.now());
        }
        return marked;
    }

    private static NotificationResponse toNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getTitle(),
                notification.getMessage(),
                notification.getNotificationType().name(),
                notification.getNotificationStatus().name(),
                notification.getTargetPath(),
                notification.getCreatedAt());
    }
}
