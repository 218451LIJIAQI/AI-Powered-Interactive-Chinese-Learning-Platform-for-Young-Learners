package com.example.aichinese;

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

import com.example.aichinese.dto.audit.AuditCategory;
import com.example.aichinese.entity.AuditLog;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.entity.enums.NotificationType;
import com.example.aichinese.repository.AuditLogRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * Teacher-made classes from the teacher's and the admin's side: building a class, the review of a new class with its
 * first lesson, revisions of published lessons and class details (students keep the published version until the
 * change is approved), deleting drafts, and the admin Teacher classes archive.
 */
class ClassAuthoringAndReviewIntegrationTests extends ApiIntegrationTest {

    private static final String TEACHER = "/api/v1/teachers";
    private static final String REVIEWS = "/api/v1/admin/class-reviews";
    private static final String PUBLIC_CLASSES = "/api/v1/public/classes";

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Test
    void aNewClassGoesLiveWhenItsFirstLessonIsApproved() throws Exception {
        String adminToken = api.adminToken();
        String teacherEmail = uniqueEmail("class-author");
        String teacherToken = api.approvedTeacher(teacherEmail);
        String otherTeacherToken = api.approvedTeacher(uniqueEmail("class-other-author"));

        // Only approved teachers make classes.
        mockMvc.perform(api.authGet(TEACHER + "/classes", api.registerTeacher(uniqueEmail("class-pending"))))
                .andExpect(status().isForbidden());

        Long classId = api.draftClass(teacherToken, "Market Chinese");
        Long unitId = api.unit(teacherToken, classId, "At the market");
        Long lessonId = api.draftLesson(teacherToken, unitId, "Asking prices");

        // Drafts are private: not in the catalogue, not open to other teachers.
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + classId)).andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(TEACHER + "/lessons/" + lessonId, otherTeacherToken)).andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(TEACHER + "/classes/" + classId, otherTeacherToken)).andExpect(status().isNotFound());

        // The server applies the submit checklist.
        mockMvc.perform(api.authPost(TEACHER + "/lessons/" + lessonId + "/submit", teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Add at least one practice activity before you submit."));

        api.readyLesson(teacherToken, lessonId, "Numbers and prices");
        long waitingBefore = reviewQueueCount(adminToken);
        mockMvc.perform(api.authPost(TEACHER + "/lessons/" + lessonId + "/submit", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.classStatus").value("SUBMITTED"));
        assertThat(reviewQueueCount(adminToken)).isEqualTo(waitingBefore + 1);

        JsonNode item = queueItem(adminToken, "NEW_CLASS", classId);
        assertThat(item.path("type").asText()).isEqualTo("CLASS");
        assertThat(item.path("lessonTitles").get(0).asText()).isEqualTo("Asking prices");
        mockMvc.perform(api.authGet(REVIEWS + "/classes/" + classId, adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classPreview.title").value("Market Chinese"))
                .andExpect(jsonPath("$.data.lessons[0].title").value("Asking prices"));
        mockMvc.perform(api.authGet(REVIEWS, teacherToken)).andExpect(status().isForbidden());

        // Changes requested: a reason is required, and the teacher sees it on the class and the lesson.
        mockMvc.perform(api.jsonPost(REVIEWS + "/classes/" + classId + "/request-changes", adminToken, Map.of("reason", "")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(REVIEWS + "/classes/" + classId + "/request-changes", adminToken,
                        Map.of("reason", "Please add tones to the price words.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(TEACHER + "/classes/" + classId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.rejectionReason").value("Please add tones to the price words."))
                .andExpect(jsonPath("$.data.units[0].lessons[0].status").value("REJECTED"))
                .andExpect(jsonPath("$.data.units[0].lessons[0].reviewNote").value("Please add tones to the price words."));
        assertThat(notificationsOf(teacherEmail))
                .anyMatch(notification -> notification.getNotificationType() == NotificationType.CONTENT_REVIEW
                        && notification.getMessage().contains("Please add tones to the price words."));

        // Resubmitted and approved: the class and its lesson are live in the catalogue.
        mockMvc.perform(api.authPost(TEACHER + "/lessons/" + lessonId + "/submit", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SUBMITTED"));
        mockMvc.perform(api.authPost(REVIEWS + "/classes/" + classId + "/approve", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(REVIEWS + "/classes/" + classId + "/approve", adminToken))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + classId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary.title").value("Market Chinese"))
                .andExpect(jsonPath("$.data.summary.teacher.verified").value(true))
                .andExpect(jsonPath("$.data.summary.lessonCount").value(1))
                .andExpect(jsonPath("$.data.units[0].lessons[0].title").value("Asking prices"))
                .andExpect(jsonPath("$.data.units[0].lessons[0].activityCount").value(1));
        mockMvc.perform(get(PUBLIC_CLASSES).param("keyword", "Market Chinese"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + classId + ")]").isNotEmpty());

        JsonNode history = api.data(mockMvc.perform(api.authGet(TEACHER + "/classes/" + classId + "/history", teacherToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(history.findValuesAsText("type"))
                .contains("CLASS_CHANGES_REQUESTED", "CLASS_APPROVED", "LESSON_APPROVED");

        // The audit log names the class, keeps the admin's reason and files both decisions under Content.
        List<AuditLog> audit = auditLogRepository.findTop10ByTargetTypeAndTargetIdOrderByCreatedAtDesc("CoursePackage", classId);
        assertThat(audit).extracting(AuditLog::getActionType)
                .contains("ADMIN_CLASS_APPROVE", "ADMIN_CLASS_REQUEST_CHANGES");
        assertThat(audit).allSatisfy(entry -> {
            assertThat(entry.getTargetLabel()).isEqualTo("Market Chinese");
            assertThat(AuditCategory.of(entry.getActionType())).isEqualTo(AuditCategory.CONTENT);
        });
        assertThat(audit).filteredOn(entry -> entry.getActionType().equals("ADMIN_CLASS_REQUEST_CHANGES"))
                .extracting(AuditLog::getReason)
                .containsExactly("Please add tones to the price words.");

        // A published class can't be deleted; a fresh draft can.
        mockMvc.perform(api.authDelete(TEACHER + "/classes/" + classId, teacherToken))
                .andExpect(status().isBadRequest());
        Long draftId = api.draftClass(teacherToken, "Throwaway draft");
        api.draftLesson(teacherToken, api.unit(teacherToken, draftId, "Unit"), "Lesson");
        mockMvc.perform(api.authDelete(TEACHER + "/classes/" + draftId, teacherToken)).andExpect(status().isOk());
        mockMvc.perform(api.authGet(TEACHER + "/classes/" + draftId, teacherToken)).andExpect(status().isNotFound());
    }

    @Test
    void aRevisionOfAPublishedLessonStaysHiddenUntilApprovedAndKeepsProgress() throws Exception {
        String adminToken = api.adminToken();
        String teacherToken = api.approvedTeacher(uniqueEmail("revision-author"));
        PublishedClass published = api.publishedClass(teacherToken, "Revision class", "Greetings", "Say hello");
        String studentToken = api.registerStudent(uniqueEmail("revision-student"));
        api.join(studentToken, published.classId());

        String studentLesson = "/api/v1/students/classes/lessons/" + published.lessonId();
        JsonNode lesson = api.data(mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("Say hello"))
                .andExpect(jsonPath("$.data.stepsTotal").value(2))
                .andReturn());
        long flashcardsId = lesson.path("activities").get(0).path("id").asLong();
        mockMvc.perform(api.authPost("/api/v1/students/classes/activities/FLASHCARDS/" + flashcardsId + "/complete", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.stepsDone").value(1));

        // The teacher edits the published lesson: a working copy, students still see the published lesson.
        String editor = TEACHER + "/lessons/" + published.lessonId();
        mockMvc.perform(api.jsonPut(editor + "/content", teacherToken, Map.of(
                        "title", "Greetings",
                        "minutes", 15,
                        "summary", "Say hello and goodbye",
                        "keyPhrases", List.of(
                                Map.of("chinese", "你好", "pinyin", "nǐ hǎo", "meaning", "hello"),
                                Map.of("chinese", "再见", "pinyin", "zàijiàn", "meaning", "goodbye")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.revisionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.data.summary").value("Say hello and goodbye"));
        mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(jsonPath("$.data.summary").value("Say hello"))
                .andExpect(jsonPath("$.data.keyPhrases.length()").value(1));

        mockMvc.perform(api.authPost(editor + "/submit", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revisionStatus").value("SUBMITTED"))
                .andExpect(jsonPath("$.data.editable").value(false));
        mockMvc.perform(api.jsonPut(editor + "/content", teacherToken, Map.of("title", "Greetings", "summary", "Again")))
                .andExpect(status().isConflict());
        queueItem(adminToken, "LESSON_REVISION", published.classId());
        mockMvc.perform(api.authGet(REVIEWS + "/lessons/" + published.lessonId(), adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessons[0].summary").value("Say hello and goodbye"))
                .andExpect(jsonPath("$.data.changes").isNotEmpty());

        // Changes requested on the revision: the published lesson is untouched and the teacher can edit again.
        mockMvc.perform(api.jsonPost(REVIEWS + "/lessons/" + published.lessonId() + "/request-changes", adminToken,
                        Map.of("reason", "Add the pinyin of goodbye with its tone marks.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(editor, teacherToken))
                .andExpect(jsonPath("$.data.revisionStatus").value("REJECTED"))
                .andExpect(jsonPath("$.data.reviewNote").value("Add the pinyin of goodbye with its tone marks."))
                .andExpect(jsonPath("$.data.editable").value(true));
        mockMvc.perform(api.authPost(editor + "/submit", teacherToken)).andExpect(status().isOk());

        // Approved: applied in place, so the lesson keeps its id and the student keeps their progress.
        mockMvc.perform(api.authPost(REVIEWS + "/lessons/" + published.lessonId() + "/approve", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(studentLesson, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.summary").value("Say hello and goodbye"))
                .andExpect(jsonPath("$.data.keyPhrases.length()").value(2))
                .andExpect(jsonPath("$.data.activities[0].id").value(flashcardsId))
                .andExpect(jsonPath("$.data.activities[0].done").value(true))
                .andExpect(jsonPath("$.data.stepsDone").value(1));
        mockMvc.perform(api.authGet(editor, teacherToken))
                .andExpect(jsonPath("$.data.revisionStatus").doesNotExist())
                .andExpect(jsonPath("$.data.summary").value("Say hello and goodbye"));
    }

    @Test
    void detailsOfAPublishedClassChangeThroughReview() throws Exception {
        String adminToken = api.adminToken();
        String teacherToken = api.approvedTeacher(uniqueEmail("details-author"));
        PublishedClass published = api.publishedClass(teacherToken, "Travel basics");
        String classPath = TEACHER + "/classes/" + published.classId();
        Map<String, Object> details = Map.of(
                "title", "Travel Chinese",
                "description", "Getting around in China",
                "levelId", 1,
                "topicId", 1,
                "learningType", "HYBRID");

        mockMvc.perform(api.authPost(classPath + "/submit", teacherToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("There are no saved changes to submit."));
        mockMvc.perform(api.jsonPut(classPath, teacherToken, details))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Travel basics"))
                .andExpect(jsonPath("$.data.revision.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.revision.title").value("Travel Chinese"));
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + published.classId()))
                .andExpect(jsonPath("$.data.summary.title").value("Travel basics"));

        mockMvc.perform(api.authPost(classPath + "/submit", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.revision.status").value("SUBMITTED"));
        queueItem(adminToken, "CLASS_DETAILS", published.classId());
        mockMvc.perform(api.jsonPost(REVIEWS + "/classes/" + published.classId() + "/request-changes", adminToken,
                        Map.of("reason", "Keep the word basics in the title.")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(classPath, teacherToken))
                .andExpect(jsonPath("$.data.revision.status").value("REJECTED"))
                .andExpect(jsonPath("$.data.revision.rejectionReason").value("Keep the word basics in the title."));

        mockMvc.perform(api.jsonPut(classPath, teacherToken, Map.of(
                        "title", "Travel Chinese basics",
                        "description", "Getting around in China",
                        "levelId", 1,
                        "topicId", 1,
                        "learningType", "HYBRID")))
                .andExpect(status().isOk());
        mockMvc.perform(api.authPost(classPath + "/submit", teacherToken)).andExpect(status().isOk());
        mockMvc.perform(api.authPost(REVIEWS + "/classes/" + published.classId() + "/approve", adminToken))
                .andExpect(status().isOk());
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + published.classId()))
                .andExpect(jsonPath("$.data.summary.title").value("Travel Chinese basics"))
                .andExpect(jsonPath("$.data.summary.description").value("Getting around in China"));
        mockMvc.perform(api.authGet(classPath, teacherToken))
                .andExpect(jsonPath("$.data.title").value("Travel Chinese basics"))
                .andExpect(jsonPath("$.data.revision").doesNotExist());
    }

    @Test
    void adminArchivesATeachersClassAndTheTeacherIsTold() throws Exception {
        String adminToken = api.adminToken();
        String teacherEmail = uniqueEmail("archived-author");
        String teacherToken = api.approvedTeacher(teacherEmail);
        PublishedClass published = api.publishedClass(teacherToken, "Archive me class");
        String studentToken = api.registerStudent(uniqueEmail("archived-class-student"));
        api.join(studentToken, published.classId());
        String latecomerToken = api.registerStudent(uniqueEmail("archived-class-latecomer"));

        mockMvc.perform(api.authGet("/api/v1/admin/teacher-classes", adminToken)
                        .param("type", "CLASS")
                        .param("keyword", "Archive me class"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(published.classId()))
                .andExpect(jsonPath("$.data.items[0].status").value("PUBLISHED"));
        mockMvc.perform(api.authGet("/api/v1/admin/teacher-classes", teacherToken)).andExpect(status().isForbidden());

        String adminClass = "/api/v1/admin/teacher-classes/classes/" + published.classId();
        mockMvc.perform(api.authPost(adminClass + "/archive", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.row.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.canRestore").value(true));
        assertThat(notificationsOf(teacherEmail))
                .anyMatch(notification -> notification.getNotificationType() == NotificationType.MODERATION_ACTION);

        // Out of the catalogue and closed to new students; students who joined keep the class.
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + published.classId())).andExpect(status().isNotFound());
        mockMvc.perform(api.authPost("/api/v1/students/classes/" + published.classId() + "/join", latecomerToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/students/classes/" + published.classId(), studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.archived").value(true));

        mockMvc.perform(api.authPost(adminClass + "/restore", adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.row.status").value("PUBLISHED"));
        mockMvc.perform(get(PUBLIC_CLASSES + "/" + published.classId())).andExpect(status().isOk());
    }

    private long reviewQueueCount(String adminToken) throws Exception {
        return api.data(mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", adminToken))
                .andExpect(status().isOk())
                .andReturn()).path("reviewQueue").asLong();
    }

    private JsonNode queueItem(String adminToken, String kind, Long classId) throws Exception {
        JsonNode items = api.data(mockMvc.perform(api.authGet(REVIEWS, adminToken))
                .andExpect(status().isOk())
                .andReturn()).path("items");
        for (JsonNode item : items) {
            if (item.path("kind").asText().equals(kind) && item.path("classId").asLong() == classId) {
                return item;
            }
        }
        throw new AssertionError("No " + kind + " review item for class " + classId + " in " + items);
    }

    private List<Notification> notificationsOf(String email) {
        return notificationRepository.findUserBatchBefore(user(email).getId(), null, null, Pageable.unpaged());
    }
}
