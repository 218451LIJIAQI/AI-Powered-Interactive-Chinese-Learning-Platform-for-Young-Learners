package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.TeacherStatus;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.ContentModerationRecordRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.NoOpEmailService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        "app.email.enabled=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CommunicationModerationIntegrationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private ContentModerationRecordRepository contentModerationRecordRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private NoOpEmailService noOpEmailService;

    @Test
    void notificationsSupportOwnershipMarkingAndAdminVisibility() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String teacherEmail = uniqueEmail("notify-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createLesson(teacherToken, "Notification Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentOneEmail = uniqueEmail("notify-student-one");
        String studentOneToken = registerStudentAndLogin(studentOneEmail);
        String studentTwoToken = registerStudentAndLogin(uniqueEmail("notify-student-two"));

        submitQuizAttempt(studentOneToken, quizId);

        mockMvc.perform(post("/api/v1/reports")
                .with(csrf())
                .header("Authorization", "Bearer " + studentOneToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", "This lesson explanation is confusing for beginners."))))
                .andExpect(status().isOk());

        StudentProfile studentOneProfile = findStudentProfile(studentOneEmail);

        mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentOneProfile.getId(),
                        "feedbackType", "QUIZ",
                        "message", "Great effort. Review the first-tone example again."))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/notifications")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("ADMIN_TASK"));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(2));

        mockMvc.perform(get("/api/v1/notifications")
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        List<Notification> studentNotifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(studentOneProfile.getUser().getId());
        Long notificationId = studentNotifications.getFirst().getId();

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                .with(csrf())
                .header("Authorization", "Bearer " + studentTwoToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(patch("/api/v1/notifications/" + notificationId + "/read")
                .with(csrf())
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("READ"));

        mockMvc.perform(patch("/api/v1/notifications/read-all")
                .with(csrf())
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.updatedCount").value(1));

        mockMvc.perform(get("/api/v1/notifications/unread-count")
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.unreadCount").value(0));

        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void reportsFlowSupportsStudentAdminAndTeacherViews() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String teacherEmail = uniqueEmail("report-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Reported Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("report-student"));

        Long firstReportId = createReport(studentToken, lessonId, "The lesson content contains an incorrect translation example.");
        Long secondReportId = createReport(studentToken, lessonId, "The lesson instructions are still unclear after reading twice.");

        mockMvc.perform(get("/api/v1/reports/my")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));

        mockMvc.perform(get("/api/v1/reports/my/" + firstReportId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("LESSON"));

        mockMvc.perform(get("/api/v1/admin/reports")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == %s)]", firstReportId).exists())
                .andExpect(jsonPath("$.data[?(@.id == %s)]", secondReportId).exists());

        mockMvc.perform(patch("/api/v1/admin/reports/" + firstReportId + "/dismiss")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Duplicate report."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportStatus").value("DISMISSED"));

        mockMvc.perform(patch("/api/v1/admin/reports/" + secondReportId + "/request-revision")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Please revise the lesson explanation."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teacherRevisionRequested").value(true))
                .andExpect(jsonPath("$.data.reportStatus").value("ACTION_TAKEN"));

        mockMvc.perform(get("/api/v1/teachers/reports")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].teacherRevisionRequested").value(true))
                .andExpect(jsonPath("$.data[0].reporterEmail").doesNotExist());

        mockMvc.perform(get("/api/v1/teachers/reports/" + secondReportId)
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.adminNote").value("Please revise the lesson explanation."));

        mockMvc.perform(post("/api/v1/reports")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", "Teachers should not be allowed to submit student reports."))))
                .andExpect(status().isForbidden());
    }

    @Test
    void reviewsFlowValidatesRatingAndPublicVisibility() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String teacherEmail = uniqueEmail("review-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Review Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("review-student"));

        Long reviewId = createReview(studentToken, lessonId, 4.5, "Helpful lesson with clear vocabulary examples.");

        mockMvc.perform(post("/api/v1/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "rating", 6,
                        "comment", "Too high."))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/teachers/reviews")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetId").value(lessonId));

        mockMvc.perform(get("/api/v1/public/reviews")
                .param("targetType", "LESSON")
                .param("targetId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].reviewStatus").value("VISIBLE"));

        mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/hide")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("HIDDEN"));

        mockMvc.perform(get("/api/v1/public/reviews")
                .param("targetType", "LESSON")
                .param("targetId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(patch("/api/v1/admin/reviews/" + reviewId + "/show")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("VISIBLE"));

        mockMvc.perform(delete("/api/v1/reviews/" + reviewId)
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reviewStatus").value("HIDDEN"));
    }

    @Test
    void teacherFeedbackRequiresLinkedStudentsAndStaysPrivateToOwner() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String teacherEmail = uniqueEmail("feedback-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createLesson(teacherToken, "Feedback Lesson");
        Long quizId = createQuizWithQuestions(teacherToken, lessonId);
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);

        String studentOneEmail = uniqueEmail("feedback-student-one");
        String studentOneToken = registerStudentAndLogin(studentOneEmail);
        String studentTwoToken = registerStudentAndLogin(uniqueEmail("feedback-student-two"));
        String otherTeacherEmail = uniqueEmail("feedback-other-teacher");
        approveTeacherProfile(otherTeacherEmail);
        String otherTeacherToken = loginAndExtractToken(otherTeacherEmail, "Password123!");

        Long quizAttemptId = submitQuizAttempt(studentOneToken, quizId);
        StudentProfile studentOneProfile = findStudentProfile(studentOneEmail);

        MvcResult createResult = mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentOneProfile.getId(),
                        "lessonId", lessonId,
                        "quizAttemptId", quizAttemptId,
                        "feedbackType", "PROGRESS",
                        "message", "You are improving well. Keep practising your reading pace."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentId").value(studentOneProfile.getId()))
                .andReturn();

        Long feedbackId = extractDataId(createResult);

        mockMvc.perform(get("/api/v1/students/feedback")
                .header("Authorization", "Bearer " + studentOneToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].feedbackType").value("PROGRESS"));

        mockMvc.perform(get("/api/v1/students/feedback/" + feedbackId)
                .header("Authorization", "Bearer " + studentTwoToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/teachers/feedback")
                .with(csrf())
                .header("Authorization", "Bearer " + otherTeacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "studentId", studentOneProfile.getId(),
                        "feedbackType", "GENERAL",
                        "message", "This unrelated teacher should be blocked."))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void supportTicketsRespectOwnershipAdminRepliesAndNoOpEmailFallback() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String studentToken = registerStudentAndLogin(uniqueEmail("support-student"));
        String otherStudentToken = registerStudentAndLogin(uniqueEmail("support-other-student"));

        MvcResult createResult = mockMvc.perform(post("/api/v1/support-tickets")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "subject", "Video access issue",
                        "message", "I cannot continue a recorded lesson after refreshing the page."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("OPEN"))
                .andReturn();

        Long ticketId = extractDataId(createResult);

        mockMvc.perform(get("/api/v1/support-tickets/my")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ticketId));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/messages")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "message", "Additional detail: the problem starts after reaching 50 percent progress."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(2));

        mockMvc.perform(get("/api/v1/support-tickets/" + ticketId)
                .header("Authorization", "Bearer " + otherStudentToken))
                .andExpect(status().isNotFound());

        int skippedBefore = noOpEmailService.getSkippedEmailCount();

        mockMvc.perform(get("/api/v1/admin/support-tickets")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(ticketId));

        mockMvc.perform(post("/api/v1/admin/support-tickets/" + ticketId + "/messages")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "message", "Thanks for the report. Please try again after reopening the lesson."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.messages.length()").value(3));

        mockMvc.perform(patch("/api/v1/admin/support-tickets/" + ticketId + "/priority")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("priority", "HIGH"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.priority").value("HIGH"));

        mockMvc.perform(patch("/api/v1/admin/support-tickets/" + ticketId + "/status")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "CLOSED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CLOSED"));

        mockMvc.perform(post("/api/v1/support-tickets/" + ticketId + "/messages")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "message", "This should be blocked while the ticket is closed."))))
                .andExpect(status().isBadRequest());

        assertThat(noOpEmailService.getSkippedEmailCount()).isGreaterThan(skippedBefore);
    }

    @Test
    void moderationAuditAndHealthChecksStayProtectedAndTraceable() throws Exception {
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        String teacherEmail = uniqueEmail("moderation-teacher");
        approveTeacherProfile(teacherEmail);
        String teacherToken = loginAndExtractToken(teacherEmail, "Password123!");
        Long lessonId = createPublishedLesson(teacherToken, adminToken, "Moderation Lesson");
        String studentToken = registerStudentAndLogin(uniqueEmail("moderation-student"));

        mockMvc.perform(post("/api/v1/admin/moderation/request-revision")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", "Please revise the lesson summary to be clearer for young learners."))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.targetType").value("LESSON"));

        mockMvc.perform(post("/api/v1/admin/moderation/hide")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", ""))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get("/api/v1/teachers/moderation-records")
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].targetId").value(lessonId));

        mockMvc.perform(post("/api/v1/admin/moderation/hide")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", "Teachers must not use admin moderation routes."))))
                .andExpect(status().isForbidden());

        assertThat(contentModerationRecordRepository.findAll()).isNotEmpty();
        assertThat(auditLogRepository.findAll())
                .extracting(log -> log.getActionType())
                .contains("MODERATION_REQUEST_REVISION");

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].actionType").exists());

        Long auditLogId = auditLogRepository.findAll().getFirst().getId();
        mockMvc.perform(get("/api/v1/admin/audit-logs/" + auditLogId)
                .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(auditLogId));

        mockMvc.perform(get("/api/v1/admin/audit-logs")
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/health"))
                .andExpect(status().isOk());
    }

    private Long createReport(String studentToken, Long lessonId, String reason) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reports")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "reason", reason))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private Long createReview(String studentToken, Long lessonId, double rating, String comment) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/reviews")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "targetType", "LESSON",
                        "targetId", lessonId,
                        "rating", rating,
                        "comment", comment))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(result);
    }

    private Long submitQuizAttempt(String studentToken, Long quizId) throws Exception {
        MvcResult loadQuizResult = mockMvc.perform(get("/api/v1/students/quizzes/" + quizId)
                .header("Authorization", "Bearer " + studentToken))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode quizData = objectMapper.readTree(loadQuizResult.getResponse().getContentAsString()).path("data");
        Long firstQuestionId = quizData.path("questions").get(0).path("id").asLong();
        Long secondQuestionId = quizData.path("questions").get(1).path("id").asLong();

        MvcResult submitResult = mockMvc.perform(post("/api/v1/students/quizzes/" + quizId + "/attempts")
                .with(csrf())
                .header("Authorization", "Bearer " + studentToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "answers", List.of(
                                Map.of("questionId", firstQuestionId, "answerText", "ma1"),
                                Map.of("questionId", secondQuestionId, "answerText", "FALSE"))))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(submitResult.getResponse().getContentAsString())
                .path("data")
                .path("attemptId")
                .asLong();
    }

    private Long createPublishedLesson(String teacherToken, String adminToken, String title) throws Exception {
        Long lessonId = createLesson(teacherToken, title);
        submitApproveAndPublishLesson(lessonId, teacherToken, adminToken);
        return lessonId;
    }

    private Long createLesson(String teacherToken, String title) throws Exception {
        MvcResult lessonResult = mockMvc.perform(post("/api/v1/teachers/lessons")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", title,
                        "description", "Phase 11 lesson",
                        "levelId", 1,
                        "categoryId", 3,
                        "learningType", "RECORDED",
                        "accessType", "FREE",
                        "price", 0,
                        "estimatedDurationMinutes", 20,
                        "basicContent", "Phase 11 practice content"))))
                .andExpect(status().isOk())
                .andReturn();

        return extractDataId(lessonResult);
    }

    private Long createQuizWithQuestions(String teacherToken, Long lessonId) throws Exception {
        MvcResult quizResult = mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/quizzes")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Phase 11 feedback quiz",
                        "description", "Quiz for teacher feedback relationship checks"))))
                .andExpect(status().isOk())
                .andReturn();

        Long quizId = extractDataId(quizResult);

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "CHOOSE_TONE",
                        "questionText", "Which tone matches ma?",
                        "points", 5,
                        "displayOrder", 1,
                        "explanation", "ma1 is first tone.",
                        "options", List.of(
                                Map.of("optionText", "ma1", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "ma3", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/teachers/quizzes/" + quizId + "/questions")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "questionType", "TRUE_FALSE",
                        "questionText", "ni hao means hello.",
                        "points", 5,
                        "displayOrder", 2,
                        "explanation", "ni hao means hello.",
                        "options", List.of(
                                Map.of("optionText", "TRUE", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "FALSE", "correctOption", false, "displayOrder", 2))))))
                .andExpect(status().isOk());

        return quizId;
    }

    private void submitApproveAndPublishLesson(Long lessonId, String teacherToken, String adminToken) throws Exception {
        mockMvc.perform(post("/api/v1/teachers/lessons/" + lessonId + "/submit")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved"))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/admin/content/LESSON/" + lessonId + "/publish")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Published"))))
                .andExpect(status().isOk());
    }

    private void approveTeacherProfile(String teacherEmail) throws Exception {
        TeacherProfile profile = registerTeacherWithVerificationDocument(teacherEmail);
        String adminToken = loginAndExtractToken("admin@example.com", "SuperSecure123!");
        mockMvc.perform(post("/api/v1/admin/teachers/" + profile.getId() + "/approve")
                .with(csrf())
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("note", "Approved for Phase 11 testing"))))
                .andExpect(status().isOk());

        TeacherProfile refreshed = teacherProfileRepository.findById(profile.getId()).orElseThrow();
        assertThat(refreshed.getTeacherStatus()).isEqualTo(TeacherStatus.APPROVED);
    }

    private TeacherProfile registerTeacherWithVerificationDocument(String email) throws Exception {
        registerTeacher(email);
        String teacherToken = loginAndExtractToken(email, "Password123!");
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "qualification.pdf",
                "application/pdf",
                "verification".getBytes());

        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                .file(file)
                .param("documentType", "QUALIFICATION")
                .param("description", "Qualification proof")
                .with(csrf())
                .header("Authorization", "Bearer " + teacherToken))
                .andExpect(status().isOk());

        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private void registerTeacher(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/teacher")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Teacher " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "bio", "Teacher bio"))))
                .andExpect(status().isOk());
    }

    private String registerStudentAndLogin(String email) throws Exception {
        mockMvc.perform(post("/api/v1/auth/register/student")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "fullName", "Student " + email,
                        "email", email,
                        "password", "Password123!",
                        "confirmPassword", "Password123!",
                        "preferredLanguage", "en"))))
                .andExpect(status().isOk());

        return loginAndExtractToken(email, "Password123!");
    }

    private StudentProfile findStudentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }

    private String loginAndExtractToken(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .with(csrf())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "email", email,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private Long extractDataId(MvcResult result) throws Exception {
        JsonNode node = objectMapper.readTree(result.getResponse().getContentAsString());
        return node.path("data").path("id").asLong();
    }

    private String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
