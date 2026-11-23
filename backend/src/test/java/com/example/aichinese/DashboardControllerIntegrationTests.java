package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.noStore;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient;

import jakarta.persistence.EntityManager;

/**
 * End-to-end MockMvc coverage of the persona home pages (student Home, admin dashboard): each one's authorization
 * scope and the single-fetch response the page depends on.
 */
class DashboardControllerIntegrationTests extends ApiIntegrationTest {

    @Test
    void studentGetsOwnHome() throws Exception {
        String token = api.registerStudent(uniqueEmail("student-dashboard"));

        mockMvc.perform(api.authGet("/api/v1/students/home", token))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.firstName").isString())
                .andExpect(jsonPath("$.data.today.dailyGoalXp").isNumber())
                .andExpect(jsonPath("$.data.setupState").value("NOT_STARTED"));
    }

    @Test
    @Transactional
    void homeChoosesTheFirstWordOrTheShortestSentenceWithAMeaningInLessonOrder() throws Exception {
        String token = api.registerStudent(uniqueEmail("home-phrase"));
        String home = "/api/v1/students/home";
        long lessonId = api.data(mockMvc.perform(api.authGet(home, token)).andReturn()).path("next").path("lessonId").asLong();
        List<LearnQuestion> questions = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lessonId);
        questions.forEach(question -> question.setMeaning(null));
        questions.get(0).setHanzi("呀？");
        questions.get(0).setMeaning("\u3000");
        questions.get(1).setHanzi("词");
        questions.get(1).setMeaning("Word");
        learnQuestionRepository.flush();
        mockMvc.perform(api.authGet(home, token))
                .andExpect(jsonPath("$.data.next.phrase.hanzi").value("词"))
                .andExpect(jsonPath("$.data.next.phrase.meaning").value("Word"));

        questions.get(1).setHanzi("这个词。");
        List<LearnQuestion> tied = questions.subList(2, 4).stream()
                .sorted(Comparator.comparing(LearnQuestion::getId)).toList();
        LearnQuestion earlier = tied.getFirst();
        earlier.setHanzi("好！");
        earlier.setMeaning("Great!");
        LearnQuestion later = tied.getLast();
        later.setPosition(earlier.getPosition());
        later.setHanzi("谁？");
        later.setMeaning("Who?");
        learnQuestionRepository.flush();
        // Equal positions use question ID in the lesson player and its Home preview.
        mockMvc.perform(api.authGet("/api/v1/students/learn/lessons/" + lessonId, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions[2].id").value(earlier.getId()))
                .andExpect(jsonPath("$.data.questions[3].id").value(later.getId()));
        mockMvc.perform(api.authGet(home, token))
                .andExpect(jsonPath("$.data.next.phrase.hanzi").value("好！"))
                .andExpect(jsonPath("$.data.next.phrase.meaning").value("Great!"));
    }

    @Test
    void adminGetsAdminDashboardSummary() throws Exception {
        String token = api.adminToken();

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isOk())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.classDrafts.count").isNumber())
                .andExpect(jsonPath("$.data.classDrafts.items").isArray())
                .andExpect(jsonPath("$.data.aiReports.count").isNumber())
                .andExpect(jsonPath("$.data.support.count").isNumber())
                .andExpect(jsonPath("$.data.teacherApplications").doesNotExist())
                .andExpect(jsonPath("$.data.reviewQueue").doesNotExist());
    }

    @Test
    @Transactional
    void reportPreviewCountsAllPendingReportsAndShowsTheNewestTwo(@Autowired EntityManager entityManager) throws Exception {
        String email = uniqueEmail("dashboard-reports");
        String student = api.registerStudent(email);
        String admin = api.adminToken();
        long previous = jdbc.queryForObject("select count(*) from ai_response_reports where status = 'PENDING_REVIEW'",
                Long.class);
        List<Long> ids = new ArrayList<>();
        for (String reason : List.of("OTHER", "INCORRECT_CONTENT", "UNCLEAR_EXPLANATION", "SCORE_SEEMS_WRONG")) {
            ids.add(api.id(mockMvc.perform(api.jsonPost("/api/v1/ai/reports", student,
                            Map.of("featureKey", "HINT", "reasonCategory", reason, "aiResponseSnapshot", "A hint.")))
                    .andExpect(status().isOk()).andReturn()));
        }
        mockMvc.perform(api.jsonPost("/api/v1/admin/ai/reports/" + ids.getLast() + "/review", admin,
                        Map.of("status", "RESOLVED", "reviewerNote", "Checked.")))
                .andExpect(status().isOk());
        LocalDateTime reportedAt = LocalDate.now().plusDays(1).atStartOfDay();
        entityManager.flush();
        jdbc.update("update ai_response_reports set created_at = ? where student_user_id = ?",
                Timestamp.valueOf(reportedAt), user(email).getId());
        entityManager.clear();

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.aiReports.count").value(previous + 3))
                .andExpect(jsonPath("$.data.aiReports.items.length()").value(2))
                .andExpect(jsonPath("$.data.aiReports.items[0].id").value(ids.get(2)))
                .andExpect(jsonPath("$.data.aiReports.items[0].reason").value("UNCLEAR_EXPLANATION"))
                .andExpect(jsonPath("$.data.aiReports.items[0].studentName").value(user(email).getDisplayName()))
                .andExpect(jsonPath("$.data.aiReports.items[0].reportedAt").value(objectMapper.valueToTree(reportedAt).asText()))
                .andExpect(jsonPath("$.data.aiReports.items[1].id").value(ids.get(1)));
    }

    /** "Class drafts": a new draft class is counted and shown first, with the lessons it holds. */
    @Test
    void theClassDraftsCardShowsTheNewestDraft() throws Exception {
        String token = api.adminToken();
        String title = "Dashboard draft " + LearningApiTestClient.uniqueSuffix();
        Long classId = api.draftClass(token, title);
        api.draftLesson(token, api.unit(token, classId, "Unit 1"), "First lesson");

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classDrafts.count").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.data.classDrafts.items[0].id").value(classId))
                .andExpect(jsonPath("$.data.classDrafts.items[0].title").value(title))
                .andExpect(jsonPath("$.data.classDrafts.items[0].lessonCount").value(1))
                .andExpect(jsonPath("$.data.classDrafts.items[0].updatedAt").isString());
    }

    /** Empty drafts stay in the preview, archived lessons affect the edit date, and working copies never count. */
    @Test
    @Transactional
    void draftPreviewCountsLessonsAndTheirLastChange(@Autowired EntityManager entityManager) throws Exception {
        String token = api.adminToken();
        long previousDrafts = jdbc.queryForObject("select count(*) from course_packages where content_status = 'DRAFT'",
                Long.class);
        api.draftClass(token, "Earlier draft");
        Long empty = api.draftClass(token, "Empty draft");
        Long newest = api.draftClass(token, "Draft with lessons");
        Long unit = api.unit(token, newest, "Unit");
        Long lesson = api.draftLesson(token, unit, "First lesson");
        api.draftLesson(token, unit, "Second lesson");
        Long archived = api.draftLesson(token, unit, "Archived lesson");
        Long revision = api.draftLesson(token, unit, "Working copy");
        LocalDateTime created = LocalDate.now().plusDays(1).atStartOfDay();
        LocalDateTime edited = created.plusHours(1);
        LocalDateTime archivedAt = edited.plusHours(1);
        entityManager.flush();
        // Give the two newest drafts the same creation time to check the id tiebreaker.
        jdbc.update("update course_packages set created_at = ?, updated_at = ? where id in (?, ?)",
                Timestamp.valueOf(created), Timestamp.valueOf(edited), empty, newest);
        jdbc.update("update lessons set content_status = 'ARCHIVED', updated_at = ? where id = ?",
                Timestamp.valueOf(archivedAt), archived);
        jdbc.update("update lessons set revision_of_lesson_id = ?, updated_at = ? where id = ?", lesson,
                Timestamp.valueOf(archivedAt.plusHours(1)), revision);
        entityManager.clear();

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classDrafts.count").value(previousDrafts + 3))
                .andExpect(jsonPath("$.data.classDrafts.items.length()").value(2))
                .andExpect(jsonPath("$.data.classDrafts.items[0].id").value(newest))
                .andExpect(jsonPath("$.data.classDrafts.items[0].lessonCount").value(2))
                .andExpect(jsonPath("$.data.classDrafts.items[0].updatedAt").value(objectMapper.valueToTree(archivedAt).asText()))
                .andExpect(jsonPath("$.data.classDrafts.items[1].id").value(empty))
                .andExpect(jsonPath("$.data.classDrafts.items[1].lessonCount").value(0))
                .andExpect(jsonPath("$.data.classDrafts.items[1].updatedAt").value(objectMapper.valueToTree(edited).asText()));
    }

    @Test
    void shouldRejectUnauthenticatedDashboardRequest() throws Exception {
        mockMvc.perform(get("/api/v1/admin/dashboard"))
                .andExpect(status().isUnauthorized())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void shouldRejectStudentAccessToAdminDashboard() throws Exception {
        String token = api.registerStudent(uniqueEmail("student-for-admin-dashboard"));

        mockMvc.perform(api.authGet("/api/v1/admin/dashboard", token))
                .andExpect(status().isForbidden())
                .andExpect(noStore())
                .andExpect(jsonPath("$.success").value(false));
    }
}
