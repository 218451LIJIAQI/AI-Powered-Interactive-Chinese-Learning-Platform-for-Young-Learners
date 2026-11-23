package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.LongStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;

/**
 * Unit coverage for {@code NotificationService}: the fail-safe {@code safeCreateNotification} path (independent
 * transaction, sanitised log), notifying the active admins, and "Mark all as read" with its Undo (the ids it marked,
 * marked unread again) across repository batches.
 */
@ExtendWith({ OutputCaptureExtension.class, MockitoExtension.class })
class NotificationServiceTests {

    private static final long USER_ID = 1L;
    private static final int BATCH = 1000;

    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserRepository userRepository = mock(UserRepository.class);
    private final SecurityUserDetails learner = new SecurityUserDetails(user(USER_ID, RoleName.STUDENT, true));

    private final PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
    private final TransactionStatus transactionStatus = mock(TransactionStatus.class);
    private final NotificationService service = new NotificationService(notificationRepository, transactionManager, userRepository);

    @Captor
    private ArgumentCaptor<Iterable<Notification>> savedBatch;

    @BeforeEach
    void prepareTransaction() {
        when(transactionManager.getTransaction(any(TransactionDefinition.class))).thenReturn(transactionStatus);
    }

    @Test
    void safeCreateNotificationDoesNotPropagateNotificationFailures(CapturedOutput output) {
        when(notificationRepository.save(any(Notification.class)))
                .thenThrow(new RuntimeException("notification store failed token=secret-token"));
        User recipient = user(77L, RoleName.ADMIN, true);
        when(userRepository.findWithReadLockById(recipient.getId())).thenReturn(Optional.of(recipient));

        assertThatCode(() -> service.safeCreateNotification(
                recipient, NotificationType.SYSTEM, "Test notification", "This notification should fail safely.",
                null, null, "/admin"))
                .doesNotThrowAnyException();

        ArgumentCaptor<TransactionDefinition> definition = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definition.capture());
        assertThat(definition.getValue().getPropagationBehavior())
                .isEqualTo(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        verify(notificationRepository).save(any(Notification.class));
        verify(transactionManager).rollback(transactionStatus);
        assertThat(output)
                .contains("Notification creation failed for recipient user id 77: RuntimeException")
                .contains("token=[REDACTED]")
                .doesNotContain("secret-token")
                .doesNotContain("at com.example.aichinese");
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void onlyActiveAdminsAreNotifiedWhileTheTargetIsVisible(boolean targetVisible) {
        User active = user(2L, RoleName.ADMIN, true);
        User deactivated = user(4L, RoleName.ADMIN, false);
        when(userRepository.findActiveIdsByRoleName(RoleName.ADMIN))
                .thenReturn(List.of(active.getId(), deactivated.getId()));
        when(userRepository.findWithReadLockByIdInOrderByIdAsc(List.of(2L, 4L)))
                .thenReturn(List.of(active, deactivated));

        service.notifyActiveAdmins(NotificationType.SUPPORT_REQUEST, "New support request", "#1042",
                "SupportRequest", 9L, "/admin/support?request=9", () -> targetVisible);

        if (targetVisible) {
            verify(notificationRepository).saveAll(savedBatch.capture());
            assertThat(savedBatch.getValue()).extracting(Notification::getUser).containsExactly(active);
        } else {
            verify(notificationRepository, never()).saveAll(any());
        }
    }

    @Test
    void failedAdminLookupRollsBackOnlyTheNotification(CapturedOutput output) {
        when(userRepository.findActiveIdsByRoleName(RoleName.ADMIN))
                .thenThrow(new IllegalStateException("Recipient lookup unavailable"));

        assertThatCode(() -> service.notifyActiveAdmins(NotificationType.SUPPORT_REQUEST,
                "New support request", "#1042", "SupportRequest", 9L, "/admin/support?request=9", () -> true))
                .doesNotThrowAnyException();

        verify(transactionManager).rollback(transactionStatus);
        verifyNoInteractions(notificationRepository);
        assertThat(output).contains("Admin notification creation failed for SupportRequest:9");
    }

    @Test
    void notifyActiveAdminsForUserTargetLocksUsersInAscendingIdOrder() {
        User targetStudent = user(3L, RoleName.STUDENT, true);
        User adminHigh = user(10L, RoleName.ADMIN, true);
        User adminLow = user(1L, RoleName.ADMIN, true);
        when(userRepository.findActiveIdsByRoleName(RoleName.ADMIN)).thenReturn(List.of(10L, 1L));
        when(userRepository.findWithReadLockByIdInOrderByIdAsc(List.of(1L, 3L, 10L)))
                .thenReturn(List.of(adminLow, targetStudent, adminHigh));

        service.notifyActiveAdminsForUserTarget(NotificationType.ADMIN_TASK, "AI report needs review", "Report",
                "AI_RESPONSE_REPORT", 55L, "/admin/ai?report=55", 3L);

        verify(userRepository).findWithReadLockByIdInOrderByIdAsc(List.of(1L, 3L, 10L));
        verify(notificationRepository).saveAll(savedBatch.capture());
        assertThat(savedBatch.getValue()).extracting(Notification::getUser).containsExactly(adminHigh, adminLow);
    }

    @Test
    void notifyActiveAdminsForUserTargetSkipsWhenTargetUserWasDeleted() {
        User deletedStudent = user(3L, RoleName.STUDENT, false);
        deletedStudent.setDeletedAt(LocalDateTime.now().minusMinutes(1));
        User admin = user(1L, RoleName.ADMIN, true);
        when(userRepository.findActiveIdsByRoleName(RoleName.ADMIN)).thenReturn(List.of(1L));
        when(userRepository.findWithReadLockByIdInOrderByIdAsc(List.of(1L, 3L)))
                .thenReturn(List.of(admin, deletedStudent));

        service.notifyActiveAdminsForUserTarget(NotificationType.ADMIN_TASK, "AI report needs review", "Report",
                "AI_RESPONSE_REPORT", 55L, "/admin/ai?report=55", 3L);

        verify(notificationRepository, never()).saveAll(any());
    }

    @Test
    void safeCreatePersonalizedNotificationsBatchesActiveRecipientsWithDistinctMessages() {
        User first = user(11L, RoleName.STUDENT, true);
        User second = user(12L, RoleName.STUDENT, true);
        User inactive = user(13L, RoleName.STUDENT, false);
        when(userRepository.findWithReadLockByIdInOrderByIdAsc(List.of(11L, 12L, 13L)))
                .thenReturn(List.of(first, second, inactive));
        Map<Long, Notification> drafts = Map.of(
                11L, NotificationService.draft(NotificationType.DAILY_GOAL_REMINDER, "Daily goal reminder",
                        "You are 10 XP away from today's goal.", null, null, "/student/learn"),
                12L, NotificationService.draft(NotificationType.DAILY_GOAL_REMINDER, "Daily goal reminder",
                        "You are 5 XP away from today's goal.", null, null, "/student/learn"),
                13L, NotificationService.draft(NotificationType.DAILY_GOAL_REMINDER, "Daily goal reminder",
                        "Inactive learner should be skipped.", null, null, "/student/learn"));

        service.safeCreatePersonalizedNotifications(drafts, NotificationType.DAILY_GOAL_REMINDER);

        verify(notificationRepository).saveAll(savedBatch.capture());
        assertThat(savedBatch.getValue())
                .extracting(n -> n.getUser().getId(), Notification::getMessage)
                .containsExactlyInAnyOrder(
                        tuple(11L, "You are 10 XP away from today's goal."),
                        tuple(12L, "You are 5 XP away from today's goal."));
    }

    /** Every unread notification is marked read, batch after batch, and their ids come back for the Undo. */
    @Test
    void markAllAsReadMarksEveryBatchAndReturnsTheIds() {
        List<Long> ids = LongStream.rangeClosed(1, BATCH + 3).boxed().toList();
        when(notificationRepository.lockUnreadIds(USER_ID)).thenReturn(ids);

        List<Long> marked = service.markAllAsRead(learner);

        assertThat(marked).containsExactlyElementsOf(ids);
        ArgumentCaptor<LocalDateTime> readAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(notificationRepository).markLockedRead(eq(USER_ID), eq(ids.subList(0, BATCH)), readAt.capture());
        verify(notificationRepository).markLockedRead(eq(USER_ID), eq(ids.subList(BATCH, ids.size())), readAt.capture());
        assertThat(readAt.getAllValues()).doesNotContainNull().containsOnly(readAt.getValue());
    }

    @Test
    void markAllAsReadWithNothingUnreadReturnsNoIds() {
        when(notificationRepository.lockUnreadIds(USER_ID)).thenReturn(List.of());

        assertThat(service.markAllAsRead(learner)).isEmpty();
        verify(notificationRepository, never()).markLockedRead(any(), any(), any());
    }

    /** The Undo marks the user's own read notifications unread again; it never reaches another user's. */
    @Test
    void markUnreadOnlyTouchesTheCallersReadNotifications() {
        List<Long> requested = List.of(5L, 6L, 999L);
        when(notificationRepository.lockReadIds(USER_ID, requested)).thenReturn(List.of(5L));

        List<Long> restored = service.markUnread(learner, requested);

        assertThat(restored).containsExactly(5L);
        verify(notificationRepository).markLockedUnread(eq(USER_ID), eq(List.of(5L)), any(LocalDateTime.class));
    }

    @Test
    void markUnreadOfNothingChangesNothing() {
        assertThat(service.markUnread(learner, List.of())).isEmpty();
        verifyNoInteractions(notificationRepository);
    }

    @Test
    void markUnreadRefusesMoreIdsThanOnePageOfUndo() {
        List<Long> ids = LongStream.rangeClosed(1, 1001).boxed().toList();

        assertThatThrownBy(() -> service.markUnread(learner, ids))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Too many notifications to mark as unread at once.");
    }

    @Test
    void unreadCountIsTheUsersUnreadNotifications() {
        when(notificationRepository.countByUserIdAndNotificationStatus(USER_ID, NotificationStatus.UNREAD)).thenReturn(4L);

        assertThat(service.getUnreadCount(learner).unreadCount()).isEqualTo(4L);
    }

    private static User user(long id, RoleName roleName, boolean active) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setEmail("user" + id + "@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("User " + id);
        user.setActive(active);
        user.setRoles(Set.of(role));
        return user;
    }
}
