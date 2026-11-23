package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.MAX_PAGE_SIZE;
import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.NotificationService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * MockMvc coverage of notifications across users: a user sees, marks and opens only their own notifications, and
 * "Mark all as read" can be undone.
 */
class NotificationIntegrationTests extends ApiIntegrationTest {

    private static final String NOTIFICATIONS = "/api/v1/notifications";

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void initialAdminRecipientsIncludeOnlyActiveAccountsWithTheRole() throws Exception {
        String email = uniqueEmail("notice-recipient");
        api.registerStudentAccount(email);
        Long userId = user(email).getId();
        assertThat(userRepository.findActiveIdsByRoleName(RoleName.ADMIN)).doesNotContain(userId);

        jdbc.update("insert into user_roles (user_id, role_id) select ?, id from roles where name = 'ADMIN'", userId);
        assertThat(userRepository.findActiveIdsByRoleName(RoleName.ADMIN)).containsOnlyOnce(userId);

        jdbc.update("update users set active = false where id = ?", userId);
        assertThat(userRepository.findActiveIdsByRoleName(RoleName.ADMIN)).doesNotContain(userId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void broadcastRecipientsAreLookedUpOnlyAfterCommit(boolean commit) throws Exception {
        String email = uniqueEmail("notice-broadcast");
        api.registerStudentAccount(email);
        Long userId = user(email).getId();
        var lookedUp = new AtomicBoolean();
        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            notificationService.safeCreateNotifications(() -> {
                lookedUp.set(true);
                return List.of(userId);
            }, NotificationType.LESSON_UPDATED, "Lesson updated", "New material.", null, null, null);
            assertThat(lookedUp).isFalse();
            if (!commit) {
                status.setRollbackOnly();
            }
        });
        assertThat(lookedUp.get()).isEqualTo(commit);
        assertThat(notificationsOf(email)).hasSize(commit ? 1 : 0);
    }

    @Test
    void notificationsSupportOwnershipAndMarking() throws Exception {
        String adminToken = api.adminToken();
        String studentOneEmail = uniqueEmail("notify-student-one");
        String studentOneToken = api.registerStudent(studentOneEmail);
        String studentTwoToken = api.registerStudent(uniqueEmail("notify-student-two"));
        long notificationId = notify(user(studentOneEmail), 1).getFirst();

        mockMvc.perform(api.authGet(NOTIFICATIONS, adminToken))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.data.items").isArray())
                .andExpect(jsonPath("$.data.size").value(20));
        mockMvc.perform(api.oversizedPageGet(NOTIFICATIONS, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE));

        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.data.unreadCount").value(1));
        mockMvc.perform(api.authGet(NOTIFICATIONS, studentOneToken))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].targetPath").value("/student/practice"))
                .andExpect(jsonPath("$.data.size").value(20));
        mockMvc.perform(api.oversizedPageGet(NOTIFICATIONS, studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE));

        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/" + notificationId + "/read", studentTwoToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/" + notificationId + "/read", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));
        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/read-all", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(NOTIFICATIONS)).andExpect(status().isUnauthorized());
    }

    /**
     * "Mark all as read" answers with the ids it marked, and its Undo marks exactly those unread again; a notification
     * read before stays read, and nobody can mark another user's notifications unread.
     */
    @Test
    void markAllAsReadCanBeUndone() throws Exception {
        String email = uniqueEmail("notify-undo");
        String token = api.registerStudent(email);
        String otherToken = api.registerStudent(uniqueEmail("notify-undo-other"));
        List<Long> ids = notify(user(email), 3);
        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/" + ids.get(0) + "/read", token)).andExpect(status().isOk());

        JsonNode marked = api.data(mockMvc.perform(api.authPatch(NOTIFICATIONS + "/read-all", token))
                .andExpect(status().isOk())
                .andReturn());
        List<Long> markedIds = marked.valueStream().map(JsonNode::asLong).toList();
        assertThat(markedIds).containsExactlyInAnyOrder(ids.get(1), ids.get(2));
        assertThat(notificationsOf(email)).extracting(Notification::getReadAt).doesNotContainNull();

        // Another user's Undo with these ids changes nothing.
        mockMvc.perform(unread(otherToken, Map.of("ids", markedIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", token))
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        // A notice arriving after mark-all is outside its Undo.
        notificationService.safeCreateNotification(user(email), NotificationType.SYSTEM, "New arrival",
                "Sent after mark-all.", null, null, null);
        mockMvc.perform(unread(token, Map.of("ids", markedIds)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notifications marked as unread."))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", token))
                .andExpect(jsonPath("$.data.unreadCount").value(3));
        mockMvc.perform(api.authGet(NOTIFICATIONS + "?status=READ", token))
                .andExpect(jsonPath("$.data.totalItems").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(ids.get(0)));
        assertThat(notificationsOf(email)).filteredOn(notification -> markedIds.contains(notification.getId()))
                .allSatisfy(notification -> assertThat(notification.getReadAt()).isNull());

        // An Undo sent twice, or without ids, changes nothing; a request without the list is refused.
        mockMvc.perform(unread(token, Map.of("ids", markedIds))).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(unread(token, Map.of("ids", List.of()))).andExpect(jsonPath("$.data.length()").value(0));
        mockMvc.perform(unread(token, Map.of())).andExpect(status().isBadRequest());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void overlappingMarksReturnTheChangedIdsOnlyOnce(boolean undo) throws Exception {
        String email = uniqueEmail("notify-overlap");
        api.registerStudentAccount(email);
        User user = user(email);
        var recipient = new SecurityUserDetails(user);
        List<Long> ids = notify(user, 2);
        Supplier<List<Long>> change;
        if (undo) {
            assertThat(notificationService.markAllAsRead(recipient)).containsExactlyElementsOf(ids);
            change = () -> notificationService.markUnread(recipient, ids);
        } else {
            change = () -> notificationService.markAllAsRead(recipient);
        }

        var transaction = new TransactionTemplate(transactions);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        var second = withRowLock(jdbc, "notifications", transaction,
                () -> assertThat(change.get()).containsExactlyElementsOf(ids), change::get);
        assertThat(second).isEmpty();

        assertThat(notificationsOf(email)).hasSize(ids.size()).allSatisfy(notice -> {
            assertThat(notice.getNotificationStatus()).isEqualTo(undo ? NotificationStatus.UNREAD : NotificationStatus.READ);
            assertThat(notice.getReadAt() == null).isEqualTo(undo);
        });
    }

    private MockHttpServletRequestBuilder unread(String token, Map<String, ?> body) throws Exception {
        return api.authPatch(NOTIFICATIONS + "/unread", token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    /** Sends the user {@code count} notifications, as a flow would; returns their ids, oldest first. */
    private List<Long> notify(User user, int count) {
        for (int index = 1; index <= count; index++) {
            notificationService.safeCreateNotification(user, NotificationType.SYSTEM, "Notice " + index,
                    "Something happened.", null, null, "/student/practice");
        }
        return notificationsOf(user.getEmail()).stream()
                .map(Notification::getId)
                .sorted()
                .toList();
    }
}
