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

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.example.aichinese.entity.Lesson;
import com.example.aichinese.entity.TeacherProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.FlashcardRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.repository.QuizRepository;
import com.example.aichinese.repository.StoryActivityRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.support.ContentReviewTestSupport;
import com.example.aichinese.support.TestUploadFixtures;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * W3 content governance (D4, D5, D6, D13, D14, D16, D23, D24): teacher drafts go through
 * submit / admin approve or reject, students only ever see approved content, edits of published
 * content are re-reviewed, unapproved teachers are locked out, teacher activities are owner-only,
 * AI drafts are never persisted and audit logs can be filtered by date and actor.
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=admin@example.com",
        "DEFAULT_ADMIN_PASSWORD=SuperSecure123!",
        // Own application context (and H2 database) so the learning-stage ordering assertions
        // here never interact with the global quiz lists checked by other test classes.
        "app.test.isolation=content-review-workflow"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ContentReviewWorkflowIntegrationTests {

    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "SuperSecure123!";
    private static final String USER_PASSWORD = "Password123!";
    private static final long BEGINNER_LEVEL_ID = 1L;
    private static final long VOCABULARY_CATEGORY_ID = 3L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TeacherProfileRepository teacherProfileRepository;

    @Autowired
    private LessonRepository lessonRepository;

    @Autowired
    private QuizRepository quizRepository;

    @Autowired
    private StoryActivityRepository storyActivityRepository;

    @Autowired
    private FlashcardRepository flashcardRepository;

    @Autowired
    private AiUsageLogRepository aiUsageLogRepository;

    @Test
    void teacherDraftIsHiddenUntilSubmittedAndApprovedThenStudentsSeeIt() throws Exception {
        String teacherEmail = uniqueEmail("review-flow-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("review-flow-student"));

        String title = "Ordering food " + suffix();
        Long lessonId = createLesson(teacherToken, title, null);
        mockMvc.perform(authGet("/api/v1/teachers/lessons/" + lessonId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"));

        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/courses").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title=='" + title + "')]").doesNotExist());

        Long quizId = createQuiz(teacherToken, lessonId, "Ordering quiz", null);
        mockMvc.perform(authPost(ContentReviewTestSupport.teacherSubmitPath("QUIZ", quizId), teacherToken, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one question before this quiz is reviewed."));
        addQuestion(teacherToken, quizId);

        ContentReviewTestSupport.submit(mockMvc, teacherToken, "LESSON", lessonId);
        ContentReviewTestSupport.submit(mockMvc, teacherToken, "QUIZ", quizId);
        mockMvc.perform(authPut("/api/v1/teachers/lessons/" + lessonId, teacherToken, lessonPayload(title + " edit", null)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(authGet("/api/v1/admin/content-review", adminToken).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type=='LESSON' && @.id==" + lessonId + ")].contentStatus")
                        .value("SUBMITTED"))
                .andExpect(jsonPath("$.data.items[?(@.type=='QUIZ' && @.id==" + quizId + ")].parentTitle")
                        .value(title));
        mockMvc.perform(authGet("/api/v1/admin/content-review/QUIZ/" + quizId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.item.type").value("QUIZ"))
                .andExpect(jsonPath("$.data.detail.questions[0].options[0].optionText").isString());
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());

        ContentReviewTestSupport.approve(mockMvc, adminToken, "LESSON", lessonId);
        ContentReviewTestSupport.approve(mockMvc, adminToken, "QUIZ", quizId);

        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value(title));
        mockMvc.perform(authGet("/api/v1/students/quizzes", studentToken).param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(quizId));
        MvcResult publicDetail = mockMvc.perform(get("/api/v1/public/courses/LESSON-" + lessonId))
                .andExpect(status().isOk())
                .andReturn();
        assertThat(data(publicDetail).path("basicContent").isNull()).isTrue();

        mockMvc.perform(authGet("/api/v1/notifications", teacherToken).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.type=='CONTENT_REVIEW' && @.targetType=='LESSON')]").exists());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken).param("actionType", "CONTENT_APPROVE")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.targetType=='LESSON' && @.targetId==" + lessonId + ")]").exists());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken).param("actionType", "CONTENT_SUBMIT")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.targetType=='QUIZ' && @.targetId==" + quizId + ")]").exists());
    }

    @Test
    void adminRejectionRequiresReasonTeacherSeesItEditsAndResubmits() throws Exception {
        String teacherEmail = uniqueEmail("review-reject-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();

        Long moduleId = createModule(teacherToken, "Travel phrases " + suffix());
        ContentReviewTestSupport.submit(mockMvc, teacherToken, "MODULE", moduleId);

        mockMvc.perform(authPost("/api/v1/admin/content-review/MODULE/" + moduleId + "/reject", adminToken,
                        Map.of("reason", " ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authPost("/api/v1/admin/content-review/MODULE/" + moduleId + "/approve", teacherToken, Map.of()))
                .andExpect(status().isForbidden());

        mockMvc.perform(authPost("/api/v1/admin/content-review/MODULE/" + moduleId + "/reject", adminToken,
                        Map.of("reason", "Add a short description of the travel situations.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Add a short description of the travel situations."));

        mockMvc.perform(authGet("/api/v1/teachers/modules/" + moduleId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Add a short description of the travel situations."));
        mockMvc.perform(authGet("/api/v1/notifications", teacherToken).param("size", "50"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.targetType=='MODULE' && @.targetId==" + moduleId + ")].message")
                        .value(org.hamcrest.Matchers.hasItem(
                                org.hamcrest.Matchers.containsString("Add a short description of the travel situations."))));

        mockMvc.perform(authPut("/api/v1/teachers/modules/" + moduleId, teacherToken, Map.of(
                        "title", "Travel phrases revised",
                        "description", "Airport, hotel and taxi conversations",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("REJECTED"));

        ContentReviewTestSupport.submit(mockMvc, teacherToken, "MODULE", moduleId);
        mockMvc.perform(authGet("/api/v1/teachers/modules/" + moduleId, teacherToken))
                .andExpect(jsonPath("$.data.rejectionReason").doesNotExist());
        ContentReviewTestSupport.approve(mockMvc, adminToken, "MODULE", moduleId);

        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken).param("actionType", "CONTENT_REJECT")
                        .param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.targetType=='MODULE' && @.targetId==" + moduleId + ")]").exists());
    }

    @Test
    void editingPublishedContentSendsItBackForReviewAndHidesItFromStudents() throws Exception {
        String teacherEmail = uniqueEmail("review-edit-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("review-edit-student"));

        String title = "Campus directions " + suffix();
        Long lessonId = createLesson(teacherToken, title, null);
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "LESSON", lessonId);
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(authPut("/api/v1/teachers/lessons/" + lessonId, teacherToken, lessonPayload(title, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("SUBMITTED"));

        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/public/courses").param("keyword", title))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.title=='" + title + "')]").doesNotExist());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("actionType", "CONTENT_RESUBMIT_AFTER_EDIT").param("size", "100"))
                .andExpect(jsonPath("$.data.items[?(@.targetId==" + lessonId + ")]").exists());

        ContentReviewTestSupport.approve(mockMvc, adminToken, "LESSON", lessonId);
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void pendingTeacherIsLimitedToDashboardShellProfileAndVerification() throws Exception {
        String pendingEmail = uniqueEmail("review-pending-teacher");
        registerTeacherWithDocument(pendingEmail);
        String pendingToken = token(pendingEmail);
        String lockedMessage = "Only approved teachers can use this teaching feature.";

        mockMvc.perform(authGet("/api/v1/teachers/students", pendingToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(lockedMessage));
        mockMvc.perform(authGet("/api/v1/teachers/feedback", pendingToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(lockedMessage));
        mockMvc.perform(authGet("/api/v1/teachers/analytics", pendingToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(authPost("/api/v1/teachers/ai/quiz-draft", pendingToken, Map.of("topic", "Greetings")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(lockedMessage));
        mockMvc.perform(authGet("/api/v1/teachers/activities", pendingToken))
                .andExpect(status().isForbidden());
        mockMvc.perform(authPost("/api/v1/teachers/activities/flashcards", pendingToken, flashcardPayload("Blocked")))
                .andExpect(status().isForbidden());
        mockMvc.perform(authGet("/api/v1/teachers/live-classes", pendingToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(authGet("/api/v1/teachers/dashboard", pendingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalStudents").value(0))
                .andExpect(jsonPath("$.data.recentQuizResults").isEmpty());
        mockMvc.perform(authGet("/api/v1/teachers/me", pendingToken))
                .andExpect(status().isOk());
        mockMvc.perform(authGet("/api/v1/teachers/verification-status", pendingToken))
                .andExpect(status().isOk());
    }

    @Test
    void teacherActivitiesAreOwnerOnlyAndReachStudentsOnlyAfterApproval() throws Exception {
        String ownerEmail = uniqueEmail("activity-owner");
        approveTeacher(ownerEmail);
        String ownerToken = token(ownerEmail);
        String otherEmail = uniqueEmail("activity-other");
        approveTeacher(otherEmail);
        String otherToken = token(otherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("activity-student"));

        String title = "Greeting card " + suffix();
        MvcResult created = mockMvc.perform(authPost("/api/v1/teachers/activities/flashcards", ownerToken,
                        flashcardPayload(title)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.learningStage").value("GUIDED"))
                .andReturn();
        Long flashcardId = data(created).path("id").asLong();

        mockMvc.perform(authGet("/api/v1/teachers/activities/flashcards/" + flashcardId, otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authPut("/api/v1/teachers/activities/flashcards/" + flashcardId, otherToken,
                        flashcardPayload("Hijacked")))
                .andExpect(status().isNotFound());
        mockMvc.perform(authDelete("/api/v1/teachers/activities/flashcards/" + flashcardId, otherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(authPost("/api/v1/teachers/activities/flashcards/" + flashcardId + "/submit", otherToken, Map.of()))
                .andExpect(status().isNotFound());
        mockMvc.perform(authGet("/api/v1/teachers/activities", otherToken).param("size", "100"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + flashcardId + " && @.type=='FLASHCARD')]").doesNotExist());
        mockMvc.perform(authGet("/api/v1/teachers/activities", ownerToken).param("size", "100"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + flashcardId + " && @.type=='FLASHCARD')].title").value(title));

        mockMvc.perform(authGet("/api/v1/students/activities/flashcards/" + flashcardId, studentToken))
                .andExpect(status().isNotFound());

        mockMvc.perform(authPost("/api/v1/teachers/activities/flashcards/" + flashcardId + "/submit", ownerToken, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(authDelete("/api/v1/teachers/activities/flashcards/" + flashcardId, ownerToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(authGet("/api/v1/admin/content-review", adminToken).param("type", "FLASHCARD").param("size", "100"))
                .andExpect(jsonPath("$.data.items[?(@.id==" + flashcardId + ")].learningStage").value("GUIDED"));

        mockMvc.perform(authPost("/api/v1/admin/content-review/FLASHCARD/" + flashcardId + "/approve", adminToken, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"));
        mockMvc.perform(authGet("/api/v1/students/activities/flashcards/" + flashcardId, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.learningStage").value("GUIDED"));

        mockMvc.perform(authPut("/api/v1/teachers/activities/flashcards/" + flashcardId, ownerToken,
                        flashcardPayload(title + " v2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(authGet("/api/v1/students/activities/flashcards/" + flashcardId, studentToken))
                .andExpect(status().isNotFound());

        MvcResult draft = mockMvc.perform(authPost("/api/v1/teachers/activities/matching-games", ownerToken, Map.of(
                        "title", "Food pairs " + suffix(),
                        "items", List.of(Map.of("leftText", "米饭", "rightText", "rice")))))
                .andExpect(status().isOk())
                .andReturn();
        Long gameId = data(draft).path("id").asLong();
        mockMvc.perform(authPost("/api/v1/teachers/activities/matching-games/" + gameId + "/submit", ownerToken, Map.of()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A matching game needs at least 2 pairs before it is reviewed."));
        mockMvc.perform(authDelete("/api/v1/teachers/activities/matching-games/" + gameId, ownerToken))
                .andExpect(status().isOk());
    }

    @Test
    void archivingAModuleHidesItsLessonsAndRestoreShowsThemAgain() throws Exception {
        String teacherEmail = uniqueEmail("review-module-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("review-module-student"));

        Long moduleId = createModule(teacherToken, "University life " + suffix());
        Long lessonId = createLesson(teacherToken, "Library visit " + suffix(), moduleId);
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "LESSON", lessonId);
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());

        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "MODULE", moduleId);
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(authPost("/api/v1/admin/content-review/MODULE/" + moduleId + "/archive", adminToken,
                        Map.of("note", "Outdated module")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("ARCHIVED"));
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());
        assertThat(lessonRepository.findById(lessonId).orElseThrow().getContentStatus())
                .isEqualTo(ContentStatus.PUBLISHED);

        mockMvc.perform(authPost("/api/v1/admin/content-review/MODULE/" + moduleId + "/restore", adminToken, Map.of()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"));
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk());
    }

    @Test
    void moderationFollowsD14AndModulesCanBeModerated() throws Exception {
        String teacherEmail = uniqueEmail("review-moderation-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();

        Long moduleId = createModule(teacherToken, "Self-introduction " + suffix());
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "MODULE", moduleId);

        mockMvc.perform(authPost("/api/v1/admin/moderation/hide", adminToken,
                        Map.of("targetType", "MODULE", "targetId", moduleId, "reason", "Temporarily hidden")))
                .andExpect(status().isOk());
        mockMvc.perform(authGet("/api/v1/teachers/modules/" + moduleId, teacherToken))
                .andExpect(jsonPath("$.data.contentStatus").value("ARCHIVED"));

        mockMvc.perform(authPost("/api/v1/admin/moderation/restore", adminToken,
                        Map.of("targetType", "MODULE", "targetId", moduleId, "reason", "Back online")))
                .andExpect(status().isOk());
        mockMvc.perform(authGet("/api/v1/teachers/modules/" + moduleId, teacherToken))
                .andExpect(jsonPath("$.data.contentStatus").value("PUBLISHED"));

        mockMvc.perform(authPost("/api/v1/admin/moderation/request-revision", adminToken,
                        Map.of("targetType", "MODULE", "targetId", moduleId, "reason", "Fix the pinyin tone marks")))
                .andExpect(status().isOk());
        mockMvc.perform(authGet("/api/v1/teachers/modules/" + moduleId, teacherToken))
                .andExpect(jsonPath("$.data.contentStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Fix the pinyin tone marks"));
        ContentReviewTestSupport.submit(mockMvc, teacherToken, "MODULE", moduleId);
    }

    @Test
    void rejectingOrDeactivatingAnApprovedTeacherHidesTheirPublishedContent() throws Exception {
        String teacherEmail = uniqueEmail("review-revoked-teacher");
        TeacherProfile profile = approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("review-revoked-student"));

        Long lessonId = createLesson(teacherToken, "Taxi phrases " + suffix(), null);
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "LESSON", lessonId);
        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isOk());

        mockMvc.perform(authPost("/api/v1/admin/teachers/" + profile.getId() + "/reject", adminToken,
                        Map.of("reason", "Qualification could not be confirmed")))
                .andExpect(status().isOk());

        mockMvc.perform(authGet("/api/v1/students/lessons/" + lessonId, studentToken))
                .andExpect(status().isNotFound());
        Lesson kept = lessonRepository.findById(lessonId).orElseThrow();
        assertThat(kept.getContentStatus()).isEqualTo(ContentStatus.PUBLISHED);
        mockMvc.perform(authGet("/api/v1/teachers/students", teacherToken))
                .andExpect(status().isForbidden());

        String secondEmail = uniqueEmail("review-deactivated-teacher");
        approveTeacher(secondEmail);
        String secondToken = token(secondEmail);
        Long secondLessonId = createLesson(secondToken, "Hotel check-in " + suffix(), null);
        ContentReviewTestSupport.publish(mockMvc, secondToken, adminToken, "LESSON", secondLessonId);
        User secondUser = userRepository.findByEmailWithRoles(secondEmail).orElseThrow();
        mockMvc.perform(authPatch("/api/v1/admin/users/" + secondUser.getId() + "/status", adminToken,
                        Map.of("active", false)))
                .andExpect(status().isOk());
        mockMvc.perform(authGet("/api/v1/students/lessons/" + secondLessonId, studentToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void studentQuizListIsOrderedByLearningStage() throws Exception {
        String teacherEmail = uniqueEmail("review-stage-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        String studentToken = registerStudent(uniqueEmail("review-stage-student"));

        Long lessonId = createLesson(teacherToken, "Numbers practice " + suffix(), null);
        ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "LESSON", lessonId);
        Long independent = createQuiz(teacherToken, lessonId, "Independent check", "INDEPENDENT");
        Long unset = createQuiz(teacherToken, lessonId, "Extra practice", null);
        Long guided = createQuiz(teacherToken, lessonId, "Guided warm-up", "GUIDED");
        Long scaffolded = createQuiz(teacherToken, lessonId, "Scaffolded drill", "SCAFFOLDED");
        for (Long quizId : List.of(independent, unset, guided, scaffolded)) {
            addQuestion(teacherToken, quizId);
            ContentReviewTestSupport.publish(mockMvc, teacherToken, adminToken, "QUIZ", quizId);
        }

        mockMvc.perform(authGet("/api/v1/students/quizzes", studentToken).param("lessonId", String.valueOf(lessonId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(guided))
                .andExpect(jsonPath("$.data.items[0].learningStage").value("GUIDED"))
                .andExpect(jsonPath("$.data.items[1].id").value(scaffolded))
                .andExpect(jsonPath("$.data.items[2].id").value(independent))
                .andExpect(jsonPath("$.data.items[3].id").value(unset));
    }

    @Test
    void teacherAiDraftsReturnUnsavedDraftsAndAreLogged() throws Exception {
        String teacherEmail = uniqueEmail("review-ai-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        long quizzesBefore = quizRepository.count();
        long storiesBefore = storyActivityRepository.count();
        long flashcardsBefore = flashcardRepository.count();
        long usageLogsBefore = aiUsageLogRepository.count();

        mockMvc.perform(authPost("/api/v1/teachers/ai/quiz-draft", teacherToken,
                        Map.of("topic", "Ordering bubble tea", "numberOfQuestions", 3, "languagePreference", "en")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("TEACHER_DRAFT"));
        mockMvc.perform(authPost("/api/v1/teachers/ai/story-draft", teacherToken,
                        Map.of("topic", "First day at university", "languagePreference", "ms")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.usageMode").value("TEACHER_DRAFT"));
        mockMvc.perform(authGet("/api/v1/teachers/ai/availability", teacherToken))
                .andExpect(status().isOk());

        assertThat(quizRepository.count()).isEqualTo(quizzesBefore);
        assertThat(storyActivityRepository.count()).isEqualTo(storiesBefore);
        assertThat(flashcardRepository.count()).isEqualTo(flashcardsBefore);
        assertThat(aiUsageLogRepository.count()).isEqualTo(usageLogsBefore + 2);
        Long teacherUserId = userRepository.findByEmailWithRoles(teacherEmail).orElseThrow().getId();
        assertThat(aiUsageLogRepository.findAll().stream()
                .filter(log -> log.getUser() != null && teacherUserId.equals(log.getUser().getId()))
                .map(log -> log.getUserRole())
                .toList())
                .hasSize(2)
                .containsOnly("TEACHER");

        mockMvc.perform(authPost("/api/v1/ai/quiz-generator", teacherToken, Map.of("topic", "Greetings")))
                .andExpect(status().isForbidden());
    }

    @Test
    void auditLogsCanBeFilteredByDateRangeActionAndActor() throws Exception {
        String teacherEmail = uniqueEmail("review-audit-teacher");
        approveTeacher(teacherEmail);
        String teacherToken = token(teacherEmail);
        String adminToken = adminToken();
        Long moduleId = createModule(teacherToken, "Audit module " + suffix());
        // Dates are taken around the submit so the test also holds if it runs across midnight.
        LocalDate submitStart = LocalDate.now();
        ContentReviewTestSupport.submit(mockMvc, teacherToken, "MODULE", moduleId);
        LocalDate submitEnd = LocalDate.now();
        User teacher = userRepository.findByEmailWithRoles(teacherEmail).orElseThrow();
        String tomorrow = submitEnd.plusDays(1).toString();
        String yesterday = submitStart.minusDays(1).toString();

        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("actionType", "CONTENT_SUBMIT").param("from", submitStart.toString()).param("to", submitEnd.toString()).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.targetId==" + moduleId + ")]").exists());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("actionType", "CONTENT_SUBMIT").param("from", tomorrow).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("to", yesterday).param("actorId", String.valueOf(teacher.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty());
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("actorId", String.valueOf(teacher.getId())).param("size", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actorUserId").value(teacher.getId()));
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken)
                        .param("actor", teacherEmail.substring(0, 18)).param("actionType", "CONTENT_SUBMIT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].actorEmail").value(teacherEmail));
        mockMvc.perform(authGet("/api/v1/admin/audit-logs", adminToken).param("from", tomorrow).param("to", submitEnd.toString()))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private TeacherProfile approveTeacher(String email) throws Exception {
        TeacherProfile profile = registerTeacherWithDocument(email);
        mockMvc.perform(authPost("/api/v1/admin/teachers/" + profile.getId() + "/approve", adminToken(),
                        Map.of("note", "Approved")))
                .andExpect(status().isOk());
        return profile;
    }

    private TeacherProfile registerTeacherWithDocument(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/teacher", Map.of(
                        "fullName", "Teacher " + email.substring(0, 12),
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "bio", "Mandarin teacher for adult beginners")))
                .andExpect(status().isOk());
        MockMultipartFile file = new MockMultipartFile(
                "file", "qualification.pdf", "application/pdf", TestUploadFixtures.minimalPdf());
        mockMvc.perform(multipart("/api/v1/teachers/verification-documents")
                        .file(file)
                        .param("documentType", "QUALIFICATION")
                        .param("description", "Qualification proof")
                        .with(csrf())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token(email)))
                .andExpect(status().isOk());
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return teacherProfileRepository.findDetailedByUserId(user.getId()).orElseThrow();
    }

    private String registerStudent(String email) throws Exception {
        mockMvc.perform(jsonPost("/api/v1/auth/register/student", Map.of(
                        "fullName", "Student " + email.substring(0, 12),
                        "email", email,
                        "password", USER_PASSWORD,
                        "confirmPassword", USER_PASSWORD,
                        "preferredLanguage", "en")))
                .andExpect(status().isOk());
        return token(email);
    }

    private Long createModule(String teacherToken, String title) throws Exception {
        MvcResult result = mockMvc.perform(authPost("/api/v1/teachers/modules", teacherToken, Map.of(
                        "title", title,
                        "description", "Module for adult beginners",
                        "levelId", BEGINNER_LEVEL_ID,
                        "categoryId", VOCABULARY_CATEGORY_ID)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andReturn();
        return data(result).path("id").asLong();
    }

    private Long createLesson(String teacherToken, String title, Long moduleId) throws Exception {
        MvcResult result = mockMvc.perform(authPost("/api/v1/teachers/lessons", teacherToken, lessonPayload(title, moduleId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andReturn();
        return data(result).path("id").asLong();
    }

    private Map<String, Object> lessonPayload(String title, Long moduleId) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("description", "Short lesson summary");
        payload.put("levelId", BEGINNER_LEVEL_ID);
        payload.put("categoryId", VOCABULARY_CATEGORY_ID);
        payload.put("learningType", "RECORDED");
        payload.put("estimatedDurationMinutes", 15);
        payload.put("basicContent", "你好 (nǐ hǎo) means hello.");
        if (moduleId != null) {
            payload.put("moduleId", moduleId);
        }
        return payload;
    }

    private Long createQuiz(String teacherToken, Long lessonId, String title, String learningStage) throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("description", "Check the vocabulary");
        if (learningStage != null) {
            payload.put("learningStage", learningStage);
        }
        MvcResult result = mockMvc.perform(authPost("/api/v1/teachers/lessons/" + lessonId + "/quizzes", teacherToken, payload))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.contentStatus").value("DRAFT"))
                .andReturn();
        return data(result).path("id").asLong();
    }

    private void addQuestion(String teacherToken, Long quizId) throws Exception {
        mockMvc.perform(authPost("/api/v1/teachers/quizzes/" + quizId + "/questions", teacherToken, Map.of(
                        "questionType", "MULTIPLE_CHOICE",
                        "questionText", "What does 你好 mean?",
                        "points", 5,
                        "displayOrder", 1,
                        "options", List.of(
                                Map.of("optionText", "Hello", "correctOption", true, "displayOrder", 1),
                                Map.of("optionText", "Goodbye", "correctOption", false, "displayOrder", 2)))))
                .andExpect(status().isOk());
    }

    private static Map<String, Object> flashcardPayload(String title) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("frontText", "你好");
        payload.put("backText", "Hello");
        payload.put("pinyin", "nǐ hǎo");
        payload.put("hskLevel", 1);
        payload.put("learningStage", "GUIDED");
        return payload;
    }

    private JsonNode data(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
    }

    private String adminToken() throws Exception {
        return token(ADMIN_EMAIL, ADMIN_PASSWORD);
    }

    private String token(String email) throws Exception {
        return token(email, USER_PASSWORD);
    }

    private String token(String email, String password) throws Exception {
        MvcResult result = mockMvc.perform(jsonPost("/api/v1/auth/login", Map.of("email", email, "password", password)))
                .andExpect(status().isOk())
                .andReturn();
        return data(result).path("token").asText();
    }

    private MockHttpServletRequestBuilder authGet(String path, String token) {
        return get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authDelete(String path, String token) {
        return delete(path).with(csrf()).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authPost(String path, String token, Map<String, ?> payload) throws Exception {
        return jsonPost(path, payload).header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authPut(String path, String token, Map<String, ?> payload) throws Exception {
        return put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder authPatch(String path, String token, Map<String, ?> payload) throws Exception {
        return patch(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload))
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private MockHttpServletRequestBuilder jsonPost(String path, Map<String, ?> payload) throws Exception {
        return post(path).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(payload));
    }

    private static String suffix() {
        return UUID.randomUUID().toString().substring(0, 8);
    }

    private static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@example.com";
    }
}
