package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.liveSessionBody;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Pageable;

import com.example.aichinese.entity.LiveClass;
import com.example.aichinese.entity.Notification;
import com.example.aichinese.repository.LiveClassRepository;
import com.example.aichinese.repository.NotificationRepository;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.LearningApiTestClient.PublishedClass;

/**
 * Live sessions of a class: scheduling (published classes only), booking with capacity, cancelling a booking, the
 * join window and attendance, rescheduling and cancelling (booked students are told), recordings for booked students
 * after the end, and the cancellation of upcoming sessions when the class is archived.
 */
class ClassLiveSessionIntegrationTests extends ApiIntegrationTest {

    private static final String TEACHER_SESSIONS = "/api/v1/teachers/live-sessions";
    private static final String STUDENT_SESSIONS = "/api/v1/students/classes/live-sessions";

    @Autowired
    private LiveClassRepository liveClassRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Test
    void bookingRespectsCapacityAndTheJoinWindowRecordsAttendance() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("live-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Live class");
        String firstToken = api.registerStudent(uniqueEmail("live-first"));
        String secondToken = api.registerStudent(uniqueEmail("live-second"));
        String outsiderToken = api.registerStudent(uniqueEmail("live-outsider"));
        api.join(firstToken, published.classId());
        api.join(secondToken, published.classId());

        // Only for published classes, in the future, for a lesson of the class.
        Long draftClass = api.draftClass(teacherToken, "Live draft class");
        mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken, session(draftClass, null, inUtc(60), 1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Live sessions can only be scheduled for published classes."));
        mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken, session(published.classId(), null, inUtc(-60), 1)))
                .andExpect(status().isBadRequest());

        // Starts in 10 minutes: the join window (15 minutes before the start) is open.
        Long sessionId = api.id(mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken,
                        session(published.classId(), published.lessonId(), inUtc(10), 1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("SCHEDULED"))
                .andExpect(jsonPath("$.data.lessonId").value(published.lessonId()))
                .andReturn());

        mockMvc.perform(api.liveSessionAction(outsiderToken, sessionId, "book")).andExpect(status().isNotFound());
        mockMvc.perform(api.liveSessionAction(firstToken, sessionId, "join"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Book a seat to join this live session."));
        mockMvc.perform(api.liveSessionAction(firstToken, sessionId, "book"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booked").value(true))
                .andExpect(jsonPath("$.data.status").value("FULL"))
                .andExpect(jsonPath("$.data.canJoin").value(true));
        mockMvc.perform(api.liveSessionAction(firstToken, sessionId, "book"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("You already booked this live session."));
        mockMvc.perform(api.liveSessionAction(secondToken, sessionId, "book"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This live session is full."));

        // Capacity can't drop below the seats booked.
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + sessionId, teacherToken,
                        session(published.classId(), published.lessonId(), inUtc(10), null)))
                .andExpect(status().isOk());
        mockMvc.perform(api.liveSessionAction(secondToken, sessionId, "book")).andExpect(status().isOk());
        Map<String, Object> tooSmall = session(published.classId(), published.lessonId(), inUtc(10), 1);
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + sessionId, teacherToken, tooSmall))
                .andExpect(status().isBadRequest());

        // Joining opens the meeting link and records attendance.
        mockMvc.perform(api.liveSessionAction(firstToken, sessionId, "join"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.meetingLink").isNotEmpty());
        mockMvc.perform(api.authGet(TEACHER_SESSIONS + "/" + sessionId, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.students.length()").value(2))
                .andExpect(jsonPath("$.data.session.attendedCount").value(1));
        mockMvc.perform(api.authGet(STUDENT_SESSIONS, firstToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming[0].id").value(sessionId))
                .andExpect(jsonPath("$.data.upcoming[0].attended").value(true));

        // A session further away: booking can be cancelled, joining is not open yet.
        Long laterId = api.id(mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken,
                        session(published.classId(), null, inUtc(24 * 60), 5)))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(api.liveSessionAction(secondToken, laterId, "book")).andExpect(status().isOk());
        mockMvc.perform(api.liveSessionAction(secondToken, laterId, "join"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("You can join from 15 minutes before the start."));
        mockMvc.perform(api.liveSessionAction(secondToken, laterId, "cancel-booking"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.booked").value(false))
                .andExpect(jsonPath("$.data.canBook").value(true));
    }

    @Test
    void reschedulingAndCancellingTellBookedStudentsAndRecordingsFollowTheSession() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("resched-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Reschedule class");
        String studentEmail = uniqueEmail("resched-student");
        String studentToken = api.registerStudent(studentEmail);
        String classmateToken = api.registerStudent(uniqueEmail("resched-classmate"));
        api.join(studentToken, published.classId());
        api.join(classmateToken, published.classId());

        Long sessionId = api.id(mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken,
                        session(published.classId(), null, inUtc(48 * 60), 10)))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(api.liveSessionAction(studentToken, sessionId, "book")).andExpect(status().isOk());

        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + sessionId, teacherToken,
                        session(published.classId(), null, inUtc(72 * 60), 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("RESCHEDULED"))
                .andExpect(jsonPath("$.data.rescheduledFrom").isNotEmpty());
        assertThat(titlesOf(studentEmail)).contains("Live session rescheduled");

        mockMvc.perform(api.authPost(TEACHER_SESSIONS + "/" + sessionId + "/cancel", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
        assertThat(titlesOf(studentEmail)).contains("Live session cancelled");
        mockMvc.perform(api.liveSessionAction(classmateToken, sessionId, "book"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("This live session was cancelled."));
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + sessionId, teacherToken,
                        session(published.classId(), null, inUtc(96 * 60), 10)))
                .andExpect(status().isConflict());

        // A past session: the recording goes to the students who booked it, not to the rest of the class.
        Long pastId = api.id(mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken,
                        session(published.classId(), null, inUtc(30), 10)))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(api.liveSessionAction(studentToken, pastId, "book")).andExpect(status().isOk());
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + pastId + "/recording", teacherToken,
                        Map.of("recordingUrl", "https://example.com/recording")))
                .andExpect(status().isBadRequest());
        LiveClass past = liveClassRepository.findById(pastId).orElseThrow();
        past.setScheduledStartAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(2));
        past.setScheduledEndAt(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        liveClassRepository.save(past);
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + pastId + "/recording", teacherToken,
                        Map.of("recordingUrl", "javascript:alert(1)")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(api.jsonPut(TEACHER_SESSIONS + "/" + pastId + "/recording", teacherToken,
                        Map.of("recordingUrl", "https://example.com/recording")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
        assertThat(titlesOf(studentEmail)).contains("Recording ready");

        mockMvc.perform(api.authGet(STUDENT_SESSIONS, studentToken))
                .andExpect(jsonPath("$.data.past[0].id").value(pastId))
                .andExpect(jsonPath("$.data.past[0].recordingUrl").value("https://example.com/recording"));
        mockMvc.perform(api.authGet(STUDENT_SESSIONS, classmateToken))
                .andExpect(jsonPath("$.data.past.length()").value(0));
        mockMvc.perform(api.authGet(TEACHER_SESSIONS, teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.past[?(@.id == " + pastId + ")].recordingUrl").value("https://example.com/recording"));
    }

    @Test
    void archivingAClassCancelsItsUpcomingSessions() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("archive-live-teacher"));
        PublishedClass published = api.publishedClass(teacherToken, "Archive live class");
        String studentEmail = uniqueEmail("archive-live-student");
        String studentToken = api.registerStudent(studentEmail);
        api.join(studentToken, published.classId());
        Long sessionId = api.id(mockMvc.perform(api.jsonPost(TEACHER_SESSIONS, teacherToken,
                        session(published.classId(), null, inUtc(24 * 60), null)))
                .andExpect(status().isOk())
                .andReturn());
        mockMvc.perform(api.liveSessionAction(studentToken, sessionId, "book")).andExpect(status().isOk());

        mockMvc.perform(api.authPost("/api/v1/teachers/classes/" + published.classId() + "/archive", teacherToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"));
        mockMvc.perform(api.authGet(TEACHER_SESSIONS + "/" + sessionId, teacherToken))
                .andExpect(jsonPath("$.data.session.status").value("CANCELLED"));
        assertThat(titlesOf(studentEmail)).contains("Live session cancelled");
        mockMvc.perform(api.authGet(STUDENT_SESSIONS, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.upcoming[0].status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.upcoming[0].canBook").value(false));
    }

    /** A session in UTC starting {@code minutesFromNow} from now, 45 minutes long. */
    private static Map<String, Object> session(Long classId, Long lessonId, LocalDateTime start, Integer capacity) {
        Map<String, Object> body = new HashMap<>(liveSessionBody(classId, lessonId, "Speaking practice", start));
        body.put("timeZone", "UTC");
        if (capacity != null) {
            body.put("capacity", capacity);
        }
        return body;
    }

    private static LocalDateTime inUtc(int minutesFromNow) {
        return LocalDateTime.now(ZoneOffset.UTC).plusMinutes(minutesFromNow).withNano(0);
    }

    private List<String> titlesOf(String email) {
        return notificationRepository.findUserBatchBefore(user(email).getId(), null, null, Pageable.unpaged()).stream()
                .map(Notification::getTitle)
                .toList();
    }
}
