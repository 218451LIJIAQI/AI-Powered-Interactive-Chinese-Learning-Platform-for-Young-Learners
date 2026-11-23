package com.example.aichinese.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorResponse;
import com.example.aichinese.dto.ai.AiStoryLineResponse;
import com.example.aichinese.dto.ai.QuizDraftRequest;
import com.example.aichinese.dto.ai.XiaomeiResponses;
import com.example.aichinese.dto.auth.AcceptAdminInviteRequest;
import com.example.aichinese.dto.auth.AuthResponse;
import com.example.aichinese.dto.auth.ChangePasswordRequest;
import com.example.aichinese.dto.auth.EmailChangeRequest;
import com.example.aichinese.dto.auth.LinkTokenResponse;
import com.example.aichinese.dto.auth.LoginRequest;
import com.example.aichinese.dto.auth.RegisterStudentRequest;
import com.example.aichinese.dto.auth.ResetPasswordRequest;
import com.example.aichinese.dto.communication.NotificationResponse;
import com.example.aichinese.dto.learn.AdminPracticeSetDetail;
import com.example.aichinese.dto.plan.PlanViews;
import com.example.aichinese.dto.support.SupportRequestDetailResponse;
import com.example.aichinese.dto.support.SupportRequestSummaryResponse;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.ContentStatus;
import com.example.aichinese.entity.enums.LearningStage;
import com.example.aichinese.entity.enums.PlanGoal;
import com.example.aichinese.entity.enums.PlanReason;
import com.example.aichinese.entity.enums.PlanStepKind;
import com.example.aichinese.entity.enums.PracticeType;
import com.example.aichinese.entity.enums.QuizQuestionType;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportRequesterRole;
import com.example.aichinese.entity.enums.SupportTopic;

/**
 * Request and response DTOs carrying credentials, tokens, or personal free text keep them out of {@code toString()}
 * so they never leak into logs or exception messages.
 */
class DtoToStringRedactionTests {

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 22, 9, 30);

    @Test
    void supportDetailAndSummaryHideRequesterIdentitySubjectAndAttachmentFilename() {
        var summary = new SupportRequestSummaryResponse(
                12L, 1042, SupportTopic.ACCOUNT, "Jane's login issue", SupportRequestStatus.OPEN,
                "Jane Doe", "jane@example.com", SupportRequesterRole.STUDENT, AT, AT, null, null, null);
        var message = new SupportRequestDetailResponse.Message(
                7L, false, "Jane Doe", "My IC number is on the photo.", AT,
                new SupportRequestDetailResponse.Attachment("Jane_Doe_IC.png", 2048));
        var detail = new SupportRequestDetailResponse(summary, 3L, List.of(message));

        assertThat(detail.toString())
                .doesNotContain("Jane", "jane@example.com", "login issue", "IC number")
                .contains("number=1042", "fileSize=2048");
    }

    @Test
    void notificationHidesItsTitleAndMessage() {
        var notification = new NotificationResponse(
                5L, "New question from Jane Doe", "A visitor (jane@example.com) sent #1042.", "SUPPORT", "UNREAD",
                "/admin/support", AT);

        assertThat(notification.toString())
                .doesNotContain("Jane", "jane@example.com")
                .contains("type=SUPPORT", "status=UNREAD", "targetPath=/admin/support");
    }

    @Test
    void xiaomeiAndAiGeneratorDtosRedactFreeTextAndPersonalTopics() {
        var line = new AiStoryLineResponse("你好，小红", "Nǐ hǎo, Xiǎohóng", "Hello, Xiaohong");
        var note = new XiaomeiResponses.MemoryNote(9L, "Traveling to Beijing with Jane Doe", PlanReason.TRAVEL);
        var reply = new XiaomeiResponses.Reply(AiRequestStatus.SUCCESS, null, line, "去北京看小红", "Keep Jane's name private");
        var summary = new XiaomeiResponses.Summary(AiRequestStatus.SUCCESS, null, line,
                "Great chat with Jane", List.of(line), List.of(line), List.of(note));
        var quizReq = new AiQuizGeneratorRequest("Ordering food for Jane", "HSK 1 personal", 5, "en");
        var draftReq = new QuizDraftRequest(11L, "Ordering food for Jane", "HSK 1 personal", 5, "en");
        var storyReq = new AiStoryGeneratorRequest("Jane's trip to Penang", "ms");
        var quizRes = new AiQuizGeneratorResponse(
                "Ordering food for Jane",
                List.of(new AiQuizGeneratorResponse.Question(
                        "What did Jane order?", QuizQuestionType.MULTIPLE_CHOICE, List.of("茶", "饭"),
                        "茶", "Jane ordered tea.", "chá", "tea")),
                AiRequestStatus.SUCCESS);
        var storyRes = new AiStoryGeneratorResponse(
                "Jane's trip to Penang",
                "Jane's Penang Story",
                List.of(line),
                List.of(new AiStoryGeneratorResponse.VocabularyItem("小红", "Xiǎohóng", "Xiaohong", "Xiaohong")),
                List.of(new AiStoryGeneratorResponse.ComprehensionQuestion("Who is Jane?", List.of("A"), "A")),
                AiRequestStatus.SUCCESS);

        assertThat(line.toString()).doesNotContain("小红", "Xiǎohóng", "Xiaohong");
        assertThat(note.toString()).doesNotContain("Beijing", "Jane").contains("id=9", "planReason=TRAVEL");
        assertThat(reply.toString()).doesNotContain("小红", "北京", "Jane").contains("status=SUCCESS");
        assertThat(summary.toString()).doesNotContain("小红", "Jane", "Beijing")
                .contains("newExpressions=1", "wordsUsed=1", "memory=1");
        assertThat(quizReq.toString()).doesNotContain("Jane", "Ordering", "personal")
                .contains("numberOfQuestions=5", "languagePreference=en");
        assertThat(draftReq.toString()).doesNotContain("Jane", "Ordering", "personal")
                .contains("lessonId=11", "numberOfQuestions=5", "languagePreference=en");
        assertThat(storyReq.toString()).doesNotContain("Jane", "Penang").contains("languagePreference=ms");
        assertThat(quizRes.toString()).doesNotContain("Jane", "Ordering").contains("questions=1", "status=SUCCESS");
        assertThat(quizRes.questions().getFirst().toString()).doesNotContain("Jane", "tea", "茶");
        assertThat(storyRes.toString()).doesNotContain("Jane", "Penang", "小红")
                .contains("lines=1", "vocabularyList=1", "comprehensionQuestions=1", "status=SUCCESS");
        assertThat(storyRes.vocabularyList().getFirst().toString()).doesNotContain("小红", "Xiǎohóng", "Xiaohong");
        assertThat(storyRes.comprehensionQuestions().getFirst().toString()).doesNotContain("Jane");
    }

    @Test
    void adminPracticeSetDetailAndHistoryRedactAuthorAndActorPii() {
        var entry = new AdminPracticeSetDetail.HistoryEntry("UPDATED", "admin.jane@example.com", AT);
        var detail = new AdminPracticeSetDetail(
                4L, PracticeType.FLASHCARDS, "Greetings", LearningStage.GUIDED, 1, 10,
                ContentStatus.PUBLISHED, "Jane Admin", 15L, List.of(), null, null, List.of(entry));

        assertThat(entry.toString()).doesNotContain("admin.jane@example.com").contains("action=UPDATED");
        assertThat(detail.toString()).doesNotContain("Jane Admin", "admin.jane@example.com")
                .contains("id=4", "type=FLASHCARDS", "history=1");
    }

    @Test
    void authAndCredentialDtosRedactSecretsTokensAndEmails() {
        var login = new LoginRequest("jane@example.com", "SuperSecret123!", true);
        var register = new RegisterStudentRequest("Jane Doe", "jane@example.com", "SuperSecret123!", "SuperSecret123!", "en");
        var changePassword = new ChangePasswordRequest("OldSecret123!", "NewSecret456!", "NewSecret456!");
        var reset = new ResetPasswordRequest("raw-reset-token-999", "NewSecret456!", "NewSecret456!");
        var emailChange = new EmailChangeRequest("newjane@example.com", "OldSecret123!");
        var acceptInvite = new AcceptAdminInviteRequest("raw-invite-token-888", "Jane Admin", "NewSecret456!", "NewSecret456!", "ms");
        var auth = new AuthResponse("eyJhbGciOiJIUzI1NiJ9.secret.payload");
        var link = new LinkTokenResponse("jane@example.com");

        for (Object dto : List.of(login, register, changePassword, reset, emailChange, acceptInvite, auth, link)) {
            assertThat(dto.toString()).doesNotContain(
                    "jane@example.com", "newjane@example.com", "Jane",
                    "SuperSecret123!", "OldSecret123!", "NewSecret456!",
                    "raw-reset-token-999", "raw-invite-token-888", "eyJhbGciOiJIUzI1NiJ9");
        }
    }

    @Test
    void planStepIncludesChineseTitleWhileRedactingReasonAndWeekTasksSerializeKind() throws Exception {
        var step = new PlanViews.Step(
                1, PlanStepKind.LEARN_UNIT, 10L, "Say hello", "你好", 1, 5, 2, 40,
                PlanViews.StepStatus.IN_PROGRESS, false, false, 1, 2, null,
                "Tailored for Jane's family trip to Penang", List.of(PlanGoal.INTRODUCE_MYSELF), null);
        assertThat(step.toString())
                .contains("title=Say hello", "titleZh=你好")
                .doesNotContain("Jane", "Penang");

        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        List<PlanViews.WeekTask> tasks = List.of(
                new PlanViews.WeekTask.LearnDone("Greetings", LocalDate.of(2026, 5, 18)),
                new PlanViews.WeekTask.LearnNext("Numbers", 4, 22L),
                new PlanViews.WeekTask.Practice("At the Café", PracticeType.LISTENING, 9L),
                new PlanViews.WeekTask.Xiaomei(true));
        JsonNode json = mapper.readTree(mapper.writeValueAsString(tasks));
        assertThat(json.get(0).path("kind").asText()).isEqualTo("LEARN_DONE");
        assertThat(json.get(0).path("title").asText()).isEqualTo("Greetings");
        assertThat(json.get(0).has("lessonId")).isFalse();
        assertThat(json.get(1).path("kind").asText()).isEqualTo("LEARN_NEXT");
        assertThat(json.get(1).path("lessonId").asLong()).isEqualTo(22L);
        assertThat(json.get(2).path("kind").asText()).isEqualTo("PRACTICE");
        assertThat(json.get(2).path("practiceSetId").asLong()).isEqualTo(9L);
        assertThat(json.get(3).path("kind").asText()).isEqualTo("XIAOMEI");
        assertThat(json.get(3).path("done").asBoolean()).isTrue();
    }
}
