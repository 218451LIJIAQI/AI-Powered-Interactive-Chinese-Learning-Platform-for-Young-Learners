package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.example.aichinese.entity.Badge;
import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.User;
import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.TeacherProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.support.LearningApiTestClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * FR-PROG-06/07/08 end to end: badges are awarded once from the seeded catalogue, the reward level
 * is floor(points / 100) + 1 and the learning streak grows on consecutive days (Streak Explorer at 3).
 */
@SpringBootTest(properties = {
        "DEFAULT_ADMIN_EMAIL=" + LearningApiTestClient.ADMIN_EMAIL,
        "DEFAULT_ADMIN_PASSWORD=" + LearningApiTestClient.ADMIN_PASSWORD
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RewardBadgeIntegrationTests {

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
    private DailyStreakRepository dailyStreakRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    private LearningApiTestClient api;

    @BeforeEach
    void setUp() {
        api = new LearningApiTestClient(mockMvc, objectMapper, userRepository, teacherProfileRepository);
    }

    @Test
    void highScoringQuizAwardsSeededBadgesOnceAndLevelFollowsPoints() throws Exception {
        long catalogueSize = badgeRepository.count();
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("reward-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Reward lesson", "你好 nǐ hǎo");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String studentToken = api.registerStudent(LearningApiTestClient.uniqueEmail("reward-student"));

        api.submitQuiz(studentToken, quizId, true, "hǎo");

        // 20 (completion) + 10 (>= 80% bonus) + 25 (lesson completed: its only quiz) = 55 points.
        JsonNode rewards = api.data(mockMvc.perform(api.authGet("/api/v1/students/rewards", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(55))
                .andExpect(jsonPath("$.data.currentLevelNumber").value(1))
                .andExpect(jsonPath("$.data.progressToNextLevelPercent").value(55))
                .andExpect(jsonPath("$.data.streak.currentStreak").value(1))
                .andReturn());
        assertThat(rewards.path("badges").findValuesAsText("name")).containsExactlyInAnyOrder(
                RewardBadge.FIRST_STEPS.badgeName(),
                RewardBadge.QUIZ_STAR.badgeName(),
                RewardBadge.LESSON_FINISHER.badgeName(),
                RewardBadge.POINT_STARTER.badgeName());
        assertThat(rewards.path("badges").findValuesAsText("description"))
                .contains(RewardBadge.QUIZ_STAR.description());

        // A retake awards nothing twice and no badge row is created outside the seeded catalogue.
        api.submitQuiz(studentToken, quizId, true, "hǎo");
        mockMvc.perform(api.authGet("/api/v1/students/rewards/badges", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalItems").value(4));
        mockMvc.perform(api.authGet("/api/v1/students/rewards", studentToken))
                .andExpect(jsonPath("$.data.totalPoints").value(55));
        assertThat(badgeRepository.count()).isEqualTo(catalogueSize);
        assertThat(badgeRepository.findAll()).extracting(Badge::getName)
                .doesNotContain("Quiz Explorer", "Daily Spark");
    }

    @Test
    void learningOnTheThirdConsecutiveDayAwardsStreakExplorer() throws Exception {
        String teacherToken = api.approvedTeacher(LearningApiTestClient.uniqueEmail("streak-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Streak lesson", "谢谢 xièxie");
        Long firstQuiz = api.publishedQuiz(teacherToken, lessonId, null, null);
        Long secondQuiz = api.publishedQuiz(teacherToken, lessonId, null, null);
        String email = LearningApiTestClient.uniqueEmail("streak-student");
        String studentToken = api.registerStudent(email);

        api.submitQuiz(studentToken, firstQuiz, false, "wrong");
        StudentProfile student = studentProfile(email);
        DailyStreak streak = dailyStreakRepository.findByStudentId(student.getId()).orElseThrow();
        assertThat(streak.getCurrentStreak()).isEqualTo(1);

        // Pretend the learner already learned yesterday and the day before.
        streak.setCurrentStreak(2);
        streak.setBestStreak(2);
        streak.setLastLearningDate(LocalDate.now().minusDays(1));
        dailyStreakRepository.save(streak);

        api.submitQuiz(studentToken, secondQuiz, false, "wrong");

        JsonNode rewards = api.data(mockMvc.perform(api.authGet("/api/v1/students/rewards", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.streak.currentStreak").value(3))
                .andExpect(jsonPath("$.data.streak.bestStreak").value(3))
                .andReturn());
        assertThat(rewards.path("badges").findValuesAsText("name"))
                .contains(RewardBadge.FIRST_STEPS.badgeName(), RewardBadge.STREAK_EXPLORER.badgeName())
                .doesNotContain(RewardBadge.QUIZ_STAR.badgeName());

        // A gap resets the current streak but keeps the best streak.
        DailyStreak afterThree = dailyStreakRepository.findByStudentId(student.getId()).orElseThrow();
        afterThree.setLastLearningDate(LocalDate.now().minusDays(3));
        dailyStreakRepository.save(afterThree);
        api.submitQuiz(studentToken, firstQuiz, false, "wrong");
        mockMvc.perform(api.authGet("/api/v1/students/rewards/streak", studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(1))
                .andExpect(jsonPath("$.data.bestStreak").value(3));
    }

    private StudentProfile studentProfile(String email) {
        User user = userRepository.findByEmailWithRoles(email).orElseThrow();
        return studentProfileRepository.findByUserId(user.getId()).orElseThrow();
    }
}
