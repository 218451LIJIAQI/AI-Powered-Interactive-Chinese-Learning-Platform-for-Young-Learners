package com.example.aichinese;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.repository.BadgeRepository;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.service.RewardBadge;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * Classes earn no XP and do not touch the streak; a high class quiz score still earns Quiz Star from the seeded
 * catalogue, once. The reward summary the sidebar reads keeps its shape.
 */
class RewardBadgeIntegrationTests extends ApiIntegrationTest {

    private static final String REWARDS = "/api/v1/students/rewards";

    @Autowired
    private DailyStreakRepository dailyStreakRepository;

    @Autowired
    private BadgeRepository badgeRepository;

    @Test
    void classQuizEarnsQuizStarButNoXpOrStreak() throws Exception {
        long catalogueSize = badgeRepository.count();
        String teacherToken = api.approvedTeacher(uniqueEmail("reward-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Reward lesson", "你好 nǐ hǎo");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String email = uniqueEmail("reward-student");
        String studentToken = api.registerStudent(email);

        api.submitQuiz(studentToken, quizId, true, "hǎo");
        api.submitQuiz(studentToken, quizId, true, "hǎo");

        mockMvc.perform(api.authGet(REWARDS, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(0))
                .andExpect(jsonPath("$.data.currentStreak").value(0));
        assertThat(earnedBadges(studentToken)).containsExactly(RewardBadge.QUIZ_STAR.name());
        assertThat(dailyStreakRepository.findByStudentId(studentProfile(email).getId())).isEmpty();
        assertThat(badgeRepository.count()).isEqualTo(catalogueSize);
    }

    @Test
    void lowClassQuizScoreEarnsNoBadge() throws Exception {
        String teacherToken = api.approvedTeacher(uniqueEmail("reward-low-teacher"));
        Long lessonId = api.publishedLesson(teacherToken, "Low score lesson", "谢谢 xièxie");
        Long quizId = api.publishedQuiz(teacherToken, lessonId, null, null);
        String studentToken = api.registerStudent(uniqueEmail("reward-low-student"));

        api.submitQuiz(studentToken, quizId, false, "wrong");

        mockMvc.perform(api.authGet(REWARDS, studentToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalPoints").value(0));
        assertThat(earnedBadges(studentToken)).isEmpty();
    }

    /** The keys of the badges the student's Progress page shows as earned. */
    private List<String> earnedBadges(String studentToken) throws Exception {
        JsonNode progress = api.data(mockMvc.perform(api.authGet("/api/v1/students/progress/overview", studentToken))
                .andExpect(status().isOk())
                .andReturn());
        List<String> earned = new ArrayList<>();
        progress.path("badges").forEach(badge -> {
            if (badge.path("earned").asBoolean()) {
                earned.add(badge.path("key").asText());
            }
        });
        return earned;
    }

}
