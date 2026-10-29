package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.MAX_PAGE_SIZE;
import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.enums.NotificationStatus;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * MockMvc coverage of notifications across users: a user sees, marks and opens only their own notifications, and a
 * notification pointing at another student's data is hidden.
 */
class NotificationIntegrationTests extends ApiIntegrationTest {

    private static final String LESSON_CONTENT = "Phase 11 practice content";
    private static final String NOTIFICATIONS = "/api/v1/notifications";
    private static final String TEACHER_FEEDBACK = "/api/v1/teachers/feedback";

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void notificationsSupportOwnershipMarkingAndAdminVisibility() throws Exception {
        String adminToken = api.adminToken();
        String teacherToken = api.approvedTeacher(uniqueEmail("notify-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Notification Lesson", LESSON_CONTENT);
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);

        String studentOneEmail = uniqueEmail("notify-student-one");
        String studentOneToken = api.registerStudent(studentOneEmail);
        String studentTwoToken = api.registerStudent(uniqueEmail("notify-student-two"));

        submitQuizAttempt(studentOneToken, quizId);

        StudentProfile studentOneProfile = studentProfile(studentOneEmail);

        Long classId = api.classOfLesson(lessonId);
        createTeacherFeedback(teacherToken, Map.of(
                "studentId", studentOneProfile.getId(),
                "classId", classId,
                "type", "CORRECTIVE",
                "message", "Great effort. Review the first-tone example again."));

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
                .andExpect(jsonPath("$.data.items[0].targetPath").value("/student/classes/" + classId + "/feedback"))
                .andExpect(jsonPath("$.data.size").value(20));

        mockMvc.perform(api.oversizedPageGet(NOTIFICATIONS, studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.size").value(MAX_PAGE_SIZE));

        Long notificationId = notificationsOf(studentOneProfile.getUser().getId()).getFirst().getId();

        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/" + notificationId + "/read", studentTwoToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/" + notificationId + "/read", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mockMvc.perform(api.authPatch(NOTIFICATIONS + "/read-all", studentOneToken))
                .andExpect(status().isOk());

        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get(NOTIFICATIONS))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void notificationPointingAtAnotherStudentsFeedbackIsHidden() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("foreign-feedback-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Foreign Feedback Lesson", LESSON_CONTENT);
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String ownerEmail = uniqueEmail("foreign-feedback-owner");
        String ownerToken = api.registerStudent(ownerEmail);
        String otherEmail = uniqueEmail("foreign-feedback-other");
        String otherToken = api.registerStudent(otherEmail);
        submitQuizAttempt(ownerToken, quizId);
        StudentProfile owner = studentProfile(ownerEmail);
        createTeacherFeedback(teacherToken, Map.of(
                "studentId", owner.getId(),
                "classId", api.classOfLesson(lessonId),
                "type", "ENCOURAGING",
                "message", "Feedback for the owner only."));
        Notification ownerNotification = notificationsOf(owner.getUser().getId()).getFirst();
        assertThat(ownerNotification.getTargetType()).isEqualTo("TEACHER_FEEDBACK");

        // No flow creates such a notification; it is stored directly to check the student-only feedback filter.
        Notification misdirected = new Notification();
        misdirected.setUser(user(otherEmail));
        misdirected.setNotificationType(NotificationType.TEACHER_FEEDBACK);
        misdirected.setNotificationStatus(NotificationStatus.UNREAD);
        misdirected.setTitle("New teacher feedback");
        misdirected.setMessage("Feedback written for another student.");
        misdirected.setTargetType("TEACHER_FEEDBACK");
        misdirected.setTargetId(ownerNotification.getTargetId());
        notificationRepository.save(misdirected);

        mockMvc.perform(api.authGet(NOTIFICATIONS, ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(1));
        mockMvc.perform(api.authGet(NOTIFICATIONS, otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items.length()").value(0))
                .andExpect(jsonPath("$.data.totalItems").value(0));
        mockMvc.perform(api.authGet(NOTIFICATIONS + "/unread-count", otherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));
    }

    /** A half-right attempt on the published greeting quiz; returns the attempt id. */
    private Long submitQuizAttempt(String studentToken, Long quizId) throws Exception {
        return api.data(api.submitQuiz(studentToken, quizId, true, "wrong")).path("attemptId").asLong();
    }

    private List<Notification> notificationsOf(Long userId) {
        return notificationRepository.findUserBatchBefore(userId, null, null, Pageable.unpaged());
    }

    private void createTeacherFeedback(String teacherToken, Map<String, Object> body) throws Exception {
        mockMvc.perform(api.jsonPost(TEACHER_FEEDBACK, teacherToken, body))
                .andExpect(status().isOk());
    }
}
