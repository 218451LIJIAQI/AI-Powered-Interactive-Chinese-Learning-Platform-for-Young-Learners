package com.example.aichinese.learn;

import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.JsonNode;

import com.example.aichinese.entity.DailyStreak;
import com.example.aichinese.entity.LearnLesson;
import com.example.aichinese.entity.LearnQuestion;
import com.example.aichinese.entity.PointsTransaction;
import com.example.aichinese.entity.PracticeCompletion;
import com.example.aichinese.entity.StudentProfile;
import com.example.aichinese.entity.base.PracticeActivityContent;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearnQuestionType;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.repository.DailyStreakRepository;
import com.example.aichinese.repository.PointsTransactionRepository;
import com.example.aichinese.repository.PracticeCompletionRepository;
import com.example.aichinese.service.learn.LearnClock;
import com.example.aichinese.service.learn.XpService;
import com.example.aichinese.support.ApiIntegrationTest;

/** The Mistakes review, the daily goal, the streak and the Progress page. */
class MistakesAndXpIntegrationTests extends ApiIntegrationTest {

    private static final String FIRST_LESSON = "The four tones · 声调";

    @Autowired
    private PointsTransactionRepository pointsTransactionRepository;

    @Autowired
    private DailyStreakRepository dailyStreakRepository;

    @Autowired
    private PracticeCompletionRepository practiceCompletionRepository;

    @Autowired
    private LearnClock clock;

    @Test
    void aReviewClearsTheMistakesAnsweredRightAndPaysOnceADay() throws Exception {
        String token = api.registerStudent(uniqueEmail("mistakes-review"));
        List<LearnQuestion> questions = firstLessonQuestions();
        for (LearnQuestion question : questions.subList(0, 3)) {
            mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", token,
                            Map.of("questionId", question.getId(), "answer", "wrong")))
                    .andExpect(jsonPath("$.data.correct").value(false));
        }
        mockMvc.perform(api.authGet("/api/v1/users/me/nav-counts", token))
                .andExpect(jsonPath("$.data.mistakes").value(3));

        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.questions.length()").value(3))
                .andExpect(jsonPath("$.data.context[0].yourAnswer").value("wrong"))
                .andExpect(jsonPath("$.data.context[0].fromLearn").value(true))
                .andReturn());
        assertThat(review.path("questions").toString()).doesNotContain("\"answer\"");

        List<Long> mistakeIds = review.path("questions").valueStream().map(question -> question.path("id").asLong()).toList();
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/check", token,
                        Map.of("questionId", mistakeIds.get(0), "answer", questions.get(0).getAnswer())))
                .andExpect(jsonPath("$.data.correct").value(true));

        // Two right, one still wrong: two cleared, one left, +10 XP.
        List<Map<String, Object>> answers = List.of(
                Map.of("questionId", mistakeIds.get(0), "answer", questions.get(0).getAnswer()),
                Map.of("questionId", mistakeIds.get(1), "answer", questions.get(1).getAnswer()),
                Map.of("questionId", mistakeIds.get(2), "answer", "still wrong"));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", answers)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(2))
                .andExpect(jsonPath("$.data.left").value(1))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
        // The one still wrong keeps the review's answer as its last one, as the next review says.
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(jsonPath("$.data.all").value(1))
                .andExpect(jsonPath("$.data.page.items[0].yourAnswer").value("still wrong"));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.context[0].yourAnswer").value("still wrong"));

        // A second review the same day still clears, but review XP is paid once a day.
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", List.of(
                        Map.of("questionId", mistakeIds.get(2), "answer", questions.get(2).getAnswer())))))
                .andExpect(jsonPath("$.data.cleared").value(1))
                .andExpect(jsonPath("$.data.left").value(0))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
    }

    /**
     * Each signed review has its own allowance for distinct mistakes. A hinted mistake can still be cleared for
     * the usual review XP.
     */
    @Test
    void aReviewGetsHintsForAsManyMistakesAsTheLevelAllowsAndStillClearsWhatIsRight() throws Exception {
        String token = api.registerStudent(uniqueEmail("mistakes-hints"));
        List<LearnQuestion> questions = firstLessonQuestions().subList(0, 3);
        for (LearnQuestion question : questions) {
            mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", token,
                            Map.of("questionId", question.getId(), "answer", "wrong")))
                    .andExpect(status().isOk());
        }
        String hint = "/api/v1/students/mistakes/review/hint";
        try (HintRules rules = HintRules.saved(mockMvc, api)) {
            rules.setHsk1(2);
            JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                    .andExpect(status().isOk())
                    .andReturn());
            assertThat(review.path("runStartedAt").asText()).isNotBlank();
            assertThat(review.path("runSignature").asText()).isNotBlank();
            List<Long> mistakeIds = review.path("questions").valueStream().map(question -> question.path("id").asLong()).toList();

            for (Long mistakeId : mistakeIds.subList(0, 2)) {
                mockMvc.perform(api.jsonPost(hint, token, reviewHint(mistakeId, review)))
                        .andExpect(status().isOk());
            }
            Map<String, Object> thirdHint = reviewHint(mistakeIds.get(2), review);
            mockMvc.perform(api.jsonPost(hint, token, thirdHint))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("There are no hints left in this set."));
            // A mistake that had its hint in this review may ask again.
            mockMvc.perform(api.jsonPost(hint, token, reviewHint(mistakeIds.get(0), review)))
                    .andExpect(status().isOk());
            // The review's start moved to now is not a run the server started.
            Map<String, Object> moved = new HashMap<>(thirdHint);
            moved.put("runStartedAt", OffsetDateTime.now().toString());
            mockMvc.perform(api.jsonPost(hint, token, moved))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Open the questions again to get a hint."));
            // Another student's mistake gets no hint at all.
            mockMvc.perform(api.jsonPost(hint, api.registerStudent(uniqueEmail("mistakes-hints-other")),
                            thirdHint))
                    .andExpect(status().isNotFound());

            // The review read again is a new run with its own hints.
            JsonNode next = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token)).andReturn());
            mockMvc.perform(api.jsonPost(hint, token, reviewHint(mistakeIds.get(2), next)))
                    .andExpect(status().isOk());

            // Right answers after hints clear their mistakes and pay the review, as without hints. (The review asks
            // the oldest mistake first: in the order they were made.)
            List<Map<String, Object>> answers = new ArrayList<>();
            for (int index = 0; index < mistakeIds.size(); index++) {
                answers.add(Map.of("questionId", mistakeIds.get(index), "answer", questions.get(index).getAnswer()));
            }
            mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", token, Map.of("answers", answers)))
                    .andExpect(jsonPath("$.data.cleared").value(3))
                    .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
        }
    }

    @Test
    void aSkippedFirstReviewAnswerCannotBeReplacedByADuplicateCorrectAnswer() throws Exception {
        String token = api.registerStudent(uniqueEmail("mistakes-first-answer"));
        LearnQuestion question = firstLessonQuestions().get(0);
        mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", token,
                        Map.of("questionId", question.getId(), "answer", "wrong")))
                .andExpect(status().isOk());
        Long mistakeId = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes", token)).andReturn())
                .path("page").path("items").get(0).path("id").asLong();
        String path = "/api/v1/students/mistakes/review/complete";
        Map<String, Object> correct = Map.of("questionId", mistakeId, "answer", question.getAnswer());
        mockMvc.perform(api.jsonPost(path, token,
                        Map.of("answers", List.of(Map.of("questionId", mistakeId), correct))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(0))
                .andExpect(jsonPath("$.data.left").value(1))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(0));
        // A skipped mistake stays in the mistakes as it was ("Skipped · It stays in your mistakes"): its last answer too.
        mockMvc.perform(api.jsonPost(path, token,
                        Map.of("answers", List.of(Map.of("questionId", mistakeId, "answer", "\u00a0\u202f")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cleared").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(jsonPath("$.data.context[0].yourAnswer").value("wrong"));
        // A correct answer in a later review still clears the mistake and earns its normal first daily reward.
        mockMvc.perform(api.jsonPost(path, token, Map.of("answers", List.of(correct))))
                .andExpect(jsonPath("$.data.cleared").value(1))
                .andExpect(jsonPath("$.data.xp.xpEarned").value(10));
    }

    @Test
    void aLegacyWhitespaceOnlyAnswerIsSkippedInBothMistakeViews() throws Exception {
        String email = uniqueEmail("mistakes-legacy-skip");
        String token = api.registerStudent(email);
        LearnQuestion question = firstLessonQuestions().get(0);
        mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", token,
                        Map.of("questionId", question.getId(), "answer", "wrong")))
                .andExpect(status().isOk());
        assertThat(jdbc.update("update learning_mistakes set your_answer = ? where student_id = ?",
                "\u00a0\u202f", studentProfile(email).getId())).isOne();

        JsonNode list = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes", token))
                .andExpect(status().isOk()).andReturn());
        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(status().isOk()).andReturn());
        assertThat(list.at("/page/items/0/yourAnswer").isNull()).isTrue();
        assertThat(review.at("/context/0/yourAnswer").isNull()).isTrue();
    }

    /** A built answer reads the same in the Mistakes tab and the review as in the lesson's verdict. */
    @Test
    void builtAnswersReadInTheMistakesTabAndTheReviewAsTheLessonShowsThem() throws Exception {
        String token = api.registerStudent(uniqueEmail("mistakes-built"));
        LearnQuestion drill = firstLessonQuestions().stream()
                .filter(question -> question.getQuestionKind() == LearnQuestionType.LISTEN_BUILD)
                .findFirst()
                .orElseThrow();
        mockMvc.perform(api.jsonPost(lessonPath(drill) + "/check", token,
                        Map.of("questionId", drill.getId(), "answer", "mā mà ma")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("mā mǎ ma"));
        // Syllables keep their spaces, as in the verdict above.
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token).param("source", "learn"))
                .andExpect(jsonPath("$.data.page.items[0].yourAnswer").value("mā mà ma"))
                .andExpect(jsonPath("$.data.page.items[0].correctAnswer").value("mā mǎ ma"));

        // Chinese tiles close up.
        PracticeActivityContent sentences = practiceSet(PracticeType.DRAG_DROP, "Places on Campus");
        String path = "/api/v1/students/practice/drag-drop/sets/" + sentences.getId();
        String itemKey = api.data(mockMvc.perform(api.authGet(path, token)).andExpect(status().isOk()).andReturn())
                .path("items").get(0).path("key").asText();
        mockMvc.perform(api.jsonPost(path + "/check", token, Map.of("itemKey", itemKey, "answer", "图书馆 去 我")))
                .andExpect(jsonPath("$.data.correct").value(false))
                .andExpect(jsonPath("$.data.correctAnswer").value("我去图书馆"));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", token).param("source", "practice"))
                .andExpect(jsonPath("$.data.page.items[0].yourAnswer").value("图书馆去我"))
                .andExpect(jsonPath("$.data.page.items[0].correctAnswer").value("我去图书馆"));

        // The review's "you answered … last time" reads the same.
        JsonNode review = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes/review", token))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(review.path("context").findValuesAsText("yourAnswer"))
                .containsExactlyInAnyOrder("mā mà ma", "图书馆去我");
    }

    @Test
    void studentsOnlySeeTheirOwnMistakes() throws Exception {
        String owner = api.registerStudent(uniqueEmail("mistakes-owner"));
        String other = api.registerStudent(uniqueEmail("mistakes-other"));
        LearnQuestion question = firstLessonQuestions().get(0);
        mockMvc.perform(api.jsonPost(lessonPath(question) + "/check", owner,
                Map.of("questionId", question.getId(), "answer", "wrong")));
        Long mistakeId = api.data(mockMvc.perform(api.authGet("/api/v1/students/mistakes", owner)).andReturn())
                .path("page").path("items").get(0).path("id").asLong();

        mockMvc.perform(api.authGet("/api/v1/students/mistakes", other))
                .andExpect(jsonPath("$.data.all").value(0));
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/check", other,
                        Map.of("questionId", mistakeId, "answer", question.getAnswer())))
                .andExpect(status().isNotFound());
        mockMvc.perform(api.jsonPost("/api/v1/students/mistakes/review/complete", other, Map.of("answers", List.of(
                        Map.of("questionId", mistakeId, "answer", question.getAnswer())))))
                .andExpect(jsonPath("$.data.cleared").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/mistakes", owner))
                .andExpect(jsonPath("$.data.all").value(1));
    }

    @Test
    void theDailyGoalIsTenTwentyOrThirtyAndALowerGoalCanBeReachedAtOnce() throws Exception {
        String token = api.registerStudent(uniqueEmail("daily-goal"));
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(20));
        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 25)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Daily goal must be 10, 20 or 30 XP."));

        LearnLesson lesson = learnLesson(FIRST_LESSON);
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete", token,
                        Map.of("answers", rightAnswers(lesson))))
                .andExpect(jsonPath("$.data.xp.goalReached").value(false));
        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.dailyGoalXp").value(10));
        mockMvc.perform(api.authGet("/api/v1/students/today", token))
                .andExpect(jsonPath("$.data.dailyGoalXp").value(10))
                .andExpect(jsonPath("$.data.todayXp").value(15))
                .andExpect(jsonPath("$.data.goalReached").value(true))
                .andExpect(jsonPath("$.data.week.length()").value(7))
                .andExpect(jsonPath("$.data.week[6].goalReached").value(true))
                .andExpect(jsonPath("$.data.week[5].goalReached").value(false));
        mockMvc.perform(api.authGet("/api/v1/students/rewards", token))
                .andExpect(jsonPath("$.data.currentStreak").value(1));
        mockMvc.perform(api.authGet("/api/v1/students/daily-goal", api.adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void reachingTheGoalTheDayAfterExtendsTheStreakAndEarnsStreakExplorer() throws Exception {
        String email = uniqueEmail("streak");
        String token = api.registerStudent(email);
        StudentProfile student = studentProfile(email);
        LocalDate today = clock.today();
        // Goal reached on the two days before today.
        DailyStreak streak = new DailyStreak();
        streak.setStudent(student);
        streak.setCurrentStreak(2);
        streak.setBestStreak(2);
        streak.setLastLearningDate(today.minusDays(1));
        dailyStreakRepository.save(streak);
        for (int daysAgo = 2; daysAgo >= 1; daysAgo--) {
            PointsTransaction goal = new PointsTransaction();
            goal.setStudent(student);
            goal.setSourceType(XpService.SOURCE_DAILY_GOAL);
            goal.setSourceId(today.minusDays(daysAgo).toEpochDay());
            goal.setPointsDelta(5);
            goal.setReason("Daily goal reached");
            goal.setCreatedAt(clock.startOf(today.minusDays(daysAgo)).plusHours(12));
            pointsTransactionRepository.save(goal);
        }
        mockMvc.perform(api.authGet("/api/v1/students/rewards", token))
                .andExpect(jsonPath("$.data.currentStreak").value(2));

        mockMvc.perform(api.jsonPut("/api/v1/students/daily-goal", token, Map.of("dailyGoalXp", 10)));
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete", token,
                        Map.of("answers", rightAnswers(lesson))))
                .andExpect(jsonPath("$.data.xp.bonusXp").value(5))
                .andExpect(jsonPath("$.data.xp.currentStreak").value(3))
                .andExpect(jsonPath("$.data.xp.newBadges").value(hasItem("STREAK_EXPLORER")));

        JsonNode progress = api.data(mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreak").value(3))
                .andExpect(jsonPath("$.data.bestStreak").value(3))
                // 10 for the lesson and the 5 goal bonus; the two earlier days were written directly.
                .andExpect(jsonPath("$.data.totalXp").value(15))
                .andExpect(jsonPath("$.data.badges.length()").value(8))
                .andExpect(jsonPath("$.data.badges[0].key").value("FIRST_STEPS"))
                .andExpect(jsonPath("$.data.badges[0].earned").value(true))
                .andReturn());
        assertThat(progress.path("calendar").path("goalDays").toString()).contains(today.toString());
        mockMvc.perform(api.authGet("/api/v1/students/rewards", token))
                .andExpect(jsonPath("$.data.currentStreak").value(3));

        // A missed day breaks the current streak but keeps the best.
        DailyStreak stored = dailyStreakRepository.findByStudentId(student.getId()).orElseThrow();
        stored.setLastLearningDate(today.minusDays(2));
        dailyStreakRepository.save(stored);
        mockMvc.perform(api.authGet("/api/v1/students/rewards", token))
                .andExpect(jsonPath("$.data.currentStreak").value(0));
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(jsonPath("$.data.currentStreak").value(0))
                .andExpect(jsonPath("$.data.bestStreak").value(3));
    }

    @Test
    void progressOverviewStartsEmptyAndRejectsABadMonth() throws Exception {
        String token = api.registerStudent(uniqueEmail("progress-new"));
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token))
                .andExpect(jsonPath("$.data.totalXp").value(0))
                .andExpect(jsonPath("$.data.level").value(1))
                .andExpect(jsonPath("$.data.nextLevelXp").value(100))
                .andExpect(jsonPath("$.data.calendar.previousMonth").doesNotExist())
                .andExpect(jsonPath("$.data.calendar.nextMonth").doesNotExist());
        mockMvc.perform(api.authGet("/api/v1/students/progress/overview", token).param("month", "May"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void skillsStartAtZeroWithNoWeakestAndComeFromRealCompletions() throws Exception {
        String email = uniqueEmail("skills");
        String token = api.registerStudent(email);
        String skills = "/api/v1/students/progress/skills";
        mockMvc.perform(api.authGet(skills, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.weakest").doesNotExist())
                .andExpect(jsonPath("$.data.skills[0].skill").value("VOCABULARY"))
                .andExpect(jsonPath("$.data.skills[1].skill").value("LISTENING"))
                .andExpect(jsonPath("$.data.skills[2].skill").value("CHARACTERS"))
                .andExpect(jsonPath("$.data.skills[3].skill").value("SENTENCES"))
                .andExpect(jsonPath("$.data.skills[0].percent").value(0))
                .andExpect(jsonPath("$.data.skills[0].weeklyChange").value(0));

        // A Unit 1 lesson counts for Listening: 1 of 6 pronunciation lessons plus 5 listening sets.
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        mockMvc.perform(api.jsonPost("/api/v1/students/learn/lessons/" + lesson.getId() + "/complete", token,
                        Map.of("answers", rightAnswers(lesson))))
                .andExpect(status().isOk());
        mockMvc.perform(api.authGet(skills, token))
                .andExpect(jsonPath("$.data.skills[1].percent").value(9))
                .andExpect(jsonPath("$.data.skills[1].weeklyChange").value(9))
                .andExpect(jsonPath("$.data.weakest").value("VOCABULARY"));

        // The same lesson finished before the last 7 days still counts, but not as this week's change.
        StudentProfile student = studentProfile(email);
        assertThat(jdbc.update("update learn_lesson_completions set first_completed_at = ? where student_id = ? and lesson_id = ?",
                LocalDateTime.now().minusDays(10), student.getId(), lesson.getId())).isOne();
        mockMvc.perform(api.authGet(skills, token))
                .andExpect(jsonPath("$.data.skills[1].percent").value(9))
                .andExpect(jsonPath("$.data.skills[1].weeklyChange").value(0));
        mockMvc.perform(api.authGet(skills, api.adminToken()))
                .andExpect(status().isForbidden());
    }

    @Test
    void practiceProgressUsesTheFirstSuccessfulRunOfEachPublishedSetForThisStudentAndType() throws Exception {
        String email = uniqueEmail("practice-progress");
        String token = api.registerStudent(email);
        StudentProfile student = studentProfile(email);
        String otherEmail = uniqueEmail("practice-progress-other");
        api.registerStudentAccount(otherEmail);
        StudentProfile other = studentProfile(otherEmail);
        List<Long> ids = practiceSets.sets(PracticeType.LISTENING, ContentStatus.PUBLISHED).stream()
                .map(PracticeActivityContent::getId).toList();
        LocalDateTime old = clock.startOf(clock.today().minusDays(10));
        LocalDateTime weekStart = clock.weekStart();
        LocalDateTime now = LocalDateTime.now();

        practiceCompletionRepository.saveAll(List.of(
                // Repeating an already successful set does not make it new progress this week.
                practiceRun(student, PracticeType.LISTENING, ids.get(0), 3, 3, old),
                practiceRun(student, PracticeType.LISTENING, ids.get(0), 3, 3, now),
                practiceRun(student, PracticeType.LISTENING, ids.get(0), 1, 3, now),
                // Earlier failures do not move the first success out of this week; its first instant is included.
                practiceRun(student, PracticeType.LISTENING, ids.get(1), 1, 3, old),
                practiceRun(student, PracticeType.LISTENING, ids.get(1), 3, 3, weekStart),
                practiceRun(student, PracticeType.LISTENING, ids.get(1), 3, 3, now),
                practiceRun(student, PracticeType.LISTENING, ids.get(2), 1, 3, now),
                practiceRun(student, PracticeType.LISTENING, ids.get(3), 0, 0, now),
                // Neither another student, another type with the same numeric id, nor an absent set counts.
                practiceRun(other, PracticeType.LISTENING, ids.get(4), 3, 3, now),
                practiceRun(student, PracticeType.TRACING, ids.get(4), 3, 3, now),
                practiceRun(student, PracticeType.LISTENING, Long.MAX_VALUE, 3, 3, now)));

        String sets = "/api/v1/students/practice/listening/sets";
        mockMvc.perform(api.authGet(sets, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sets[0].done").value(true))
                .andExpect(jsonPath("$.data.sets[1].done").value(true))
                .andExpect(jsonPath("$.data.sets[2].done").value(false))
                .andExpect(jsonPath("$.data.sets[3].done").value(false))
                .andExpect(jsonPath("$.data.sets[4].done").value(false));
        mockMvc.perform(api.authGet(sets + "/" + ids.get(0), token))
                .andExpect(jsonPath("$.data.done").value(true));
        mockMvc.perform(api.authGet("/api/v1/students/progress/skills", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.skills[1].percent").value(18))
                .andExpect(jsonPath("$.data.skills[1].weeklyChange").value(9));
    }

    private static PracticeCompletion practiceRun(StudentProfile student, PracticeType type, Long id,
            int correct, int items, LocalDateTime at) {
        PracticeCompletion completion = new PracticeCompletion();
        completion.setStudent(student);
        completion.setPracticeType(type);
        completion.setActivityId(id);
        completion.setCorrectCount(correct);
        completion.setItemCount(items);
        completion.setCreatedAt(at);
        return completion;
    }

    private String lessonPath(LearnQuestion question) {
        return "/api/v1/students/learn/lessons/" + question.getLesson().getId();
    }

    /** A hint request for a mistake, in the run of the review as the server sent it. */
    private static Map<String, Object> reviewHint(Long mistakeId, JsonNode review) {
        return Map.of("questionId", mistakeId,
                "runStartedAt", review.path("runStartedAt").asText(),
                "runSignature", review.path("runSignature").asText());
    }

    /** The questions of the first lesson, each knowing its lesson. */
    private List<LearnQuestion> firstLessonQuestions() {
        LearnLesson lesson = learnLesson(FIRST_LESSON);
        List<LearnQuestion> questions = learnQuestionRepository.findByLessonIdOrderByPositionAscIdAsc(lesson.getId());
        questions.forEach(question -> question.setLesson(lesson));
        return questions;
    }
}
