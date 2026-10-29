package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;

/**
 * The teacher's students: the roster and student detail cover only students of the teacher's classes; feedback is
 * written to a student of the class (about a quiz attempt, a lesson or in general), read by that student only in the
 * class Feedback tab, and edited from Feedback history, which filters by student and type on the server.
 */
class ClassStudentsAndFeedbackIntegrationTests extends ApiIntegrationTest {

    private static final String TEACHER = "/api/v1/teachers";
    private static final String FEEDBACK = TEACHER + "/feedback";

    @Test
    void rosterAndStudentDetailShowOnlyStudentsOfTheTeachersClasses() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("roster-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Roster class");
        String studentEmail = uniqueEmail("roster-student");
        String studentToken = api.registerStudent(studentEmail);
        Long studentId = studentProfile(studentEmail).getId();
        api.submitQuiz(studentToken, published.quizId(), false, "wrong");
        String strangerEmail = uniqueEmail("roster-stranger");
        api.registerStudent(strangerEmail);

        mockMvc.perform(api.authGet(TEACHER + "/students", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.students[0].studentId").value(studentId))
                .andExpect(jsonPath("$.data.students[0].quizAverage").value(33))
                .andExpect(jsonPath("$.data.students[0].needsSupport").value(true));
        mockMvc.perform(api.authGet(TEACHER + "/students", teacherToken).param("filter", "SUPPORT"))
                .andExpect(jsonPath("$.data.students.length()").value(1));
        mockMvc.perform(api.authGet(TEACHER + "/students", teacherToken).param("search", "nobody-matches-this"))
                .andExpect(jsonPath("$.data.students.length()").value(0));

        mockMvc.perform(api.authGet(TEACHER + "/students/" + studentId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.classId").value(published.classId()))
                .andExpect(jsonPath("$.data.quizResults.length()").value(1))
                .andExpect(jsonPath("$.data.lessons[0].status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.data.feedbackTargets[0].kind").value("QUIZ_ATTEMPT"));
        mockMvc.perform(api.authGet(TEACHER + "/students/" + studentProfile(strangerEmail).getId(), teacherToken))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet(TEACHER + "/students/" + studentId, api.approvedTeacher(uniqueEmail("roster-other"))))
                .andExpect(status().isNotFound());

        mockMvc.perform(api.authGet(TEACHER + "/teaching-dashboard", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(1))
                .andExpect(jsonPath("$.data.publishedClasses").value(1))
                .andExpect(jsonPath("$.data.publishedLessons").value(1))
                .andExpect(jsonPath("$.data.needsSupportCount").value(1));
    }

    @Test
    void aStudentWhoJoinedButHasNotStartedIsListedWithoutActivity() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("roster-idle-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Idle roster class");
        String studentEmail = uniqueEmail("roster-idle-student");
        api.join(api.registerStudent(studentEmail), published.classId());
        Long studentId = studentProfile(studentEmail).getId();

        mockMvc.perform(api.authGet(TEACHER + "/students", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students[0].studentId").value(studentId))
                .andExpect(jsonPath("$.data.students[0].progressPercent").value(0));
        // Only started lessons are listed; the student was last active when joining.
        mockMvc.perform(api.authGet(TEACHER + "/students/" + studentId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.lessons.length()").value(0))
                .andExpect(jsonPath("$.data.lastActiveAt").isNotEmpty());
        mockMvc.perform(api.authGet(TEACHER + "/teaching-dashboard", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(1));
    }

    @Test
    void feedbackReachesOnlyItsStudentAndHistoryFiltersOnTheServer() throws Exception {
        String teacherEmail = uniqueEmail("feedback-teacher");
        String teacherToken = api.approvedTeacher(teacherEmail);
        PublishedClass published = api.publishedClass(teacherToken, "Feedback class");
        PublishedClass otherClass = api.publishedClass(teacherToken, "Feedback other class");
        String studentEmail = uniqueEmail("feedback-student");
        String studentToken = api.registerStudent(studentEmail);
        Long studentId = studentProfile(studentEmail).getId();
        String classmateEmail = uniqueEmail("feedback-classmate");
        String classmateToken = api.registerStudent(classmateEmail);
        Long classmateId = studentProfile(classmateEmail).getId();
        Long attemptId = api.data(api.submitQuiz(studentToken, published.quizId(), false, "hǎo")).path("attemptId").asLong();
        Long classmateAttemptId = api.data(api.submitQuiz(classmateToken, published.quizId(), true, "hǎo"))
                .path("attemptId").asLong();

        Long quizFeedbackId = api.id(mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, published.classId(), "CORRECTIVE", "Check the meaning of 你好 again.", null, attemptId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.about").value("QUIZ_ATTEMPT"))
                .andExpect(jsonPath("$.data.lessonTitle").value("Feedback class lesson"))
                .andExpect(jsonPath("$.data.attemptPercent").value(66))
                .andReturn());
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, published.classId(), "LESSON", "Nice work on the greetings lesson.", published.lessonId(), null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.about").value("LESSON"));
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        classmateId, published.classId(), "ENCOURAGING", "Great start, keep it up!", null, null)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.about").value("GENERAL"));

        // Only for students of the class, about their own attempts and the class's lessons, with a known type.
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, otherClass.classId(), "ENCOURAGING", "You have not joined this one.", null, null)))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, published.classId(), "CORRECTIVE", "Somebody else's attempt.", null, classmateAttemptId)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, published.classId(), "LESSON", "A lesson of another class.", otherClass.lessonId(), null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(FEEDBACK, teacherToken, feedback(
                        studentId, published.classId(), "HOMEWORK", "Unknown feedback type.", null, null)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPost(FEEDBACK, api.approvedTeacher(uniqueEmail("feedback-intruder")), feedback(
                        studentId, published.classId(), "ENCOURAGING", "Not my class.", null, null)))
                .andExpect(status().isNotFound());

        // The student reads their own feedback in the class; the classmate only theirs.
        mockMvc.perform(api.authGet("/api/v1/students/classes/" + published.classId() + "/feedback", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[?(@.id == " + quizFeedbackId + ")].teacherName").value("Teacher " + teacherEmail))
                .andExpect(jsonPath("$.data[0].studentId").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/students/classes/" + published.classId() + "/feedback", classmateToken))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].message").value("Great start, keep it up!"));

        // History: newest first, filtered by student and type; editing marks the feedback as edited.
        mockMvc.perform(api.authGet(FEEDBACK, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].message").value("Great start, keep it up!"));
        mockMvc.perform(api.authGet(FEEDBACK, teacherToken).param("studentId", String.valueOf(studentId)))
                .andExpect(jsonPath("$.data.length()").value(2));
        mockMvc.perform(api.authGet(FEEDBACK, teacherToken).param("type", "corrective"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(quizFeedbackId));
        mockMvc.perform(api.authGet(FEEDBACK, teacherToken).param("type", "HOMEWORK"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(api.jsonPut(FEEDBACK + "/" + quizFeedbackId, teacherToken,
                        Map.of("type", "WEAK_AREA", "message", "Review the meaning of 你好 once more.")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("WEAK_AREA"))
                .andExpect(jsonPath("$.data.editedAt").isNotEmpty());
        mockMvc.perform(api.jsonPut(FEEDBACK + "/" + quizFeedbackId, api.approvedTeacher(uniqueEmail("feedback-editor")),
                        Map.of("type", "WEAK_AREA", "message", "Not mine to edit.")))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.authGet("/api/v1/students/classes/" + published.classId() + "/feedback", studentToken))
                .andExpect(jsonPath("$.data[?(@.id == " + quizFeedbackId + ")].message").value("Review the meaning of 你好 once more."));
    }

    private static Map<String, Object> feedback(
            Long studentId, Long classId, String type, String message, Long lessonId, Long quizAttemptId) {
        Map<String, Object> body = new HashMap<>(Map.of(
                "studentId", studentId, "classId", classId, "type", type, "message", message));
        if (lessonId != null) {
            body.put("lessonId", lessonId);
        }
        if (quizAttemptId != null) {
            body.put("quizAttemptId", quizAttemptId);
        }
        return body;
    }
}
