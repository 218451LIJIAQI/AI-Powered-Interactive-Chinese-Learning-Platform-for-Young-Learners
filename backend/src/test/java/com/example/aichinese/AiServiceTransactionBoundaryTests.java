package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.QuizDraftRequest;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.dto.plan.PlanRequests;
import com.example.aichinese.entity.User;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.FileStorageService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.service.ai.AiGateway;
import com.example.aichinese.service.plan.LearningPlanService;
import com.example.aichinese.service.plan.PlanAdvisor;
import com.example.aichinese.service.plan.PlanComposer;

/** A remote AI call (up to the provider timeout) must never hold a database connection and row locks open. */
class AiServiceTransactionBoundaryTests {

    @Test
    void aiRequestsDoNotWrapRemoteCallsInDatabaseTransactions() throws Exception {
        assertThat(aiRequestMethods())
                .allSatisfy(AiServiceTransactionBoundaryTests::assertOutsideTransactionBoundary);
    }

    private static List<Method> aiRequestMethods() throws NoSuchMethodException {
        return List.of(
                AiGateway.class.getMethod("execute", AiGateway.AiCall.class),
                AiGateway.class.getMethod("assessPronunciation", User.class, String.class, String.class,
                        String.class, FileStorageService.PracticeUpload.class),
                AiService.class.getMethod("chat", SecurityUserDetails.class, AiChatRequest.class),
                AiService.class.getMethod("prepareChat", SecurityUserDetails.class, AiChatRequest.class),
                AiService.class.getMethod("completeChat", SecurityUserDetails.class, AiService.PreparedChat.class),
                AiService.class.getMethod("quizGenerator", SecurityUserDetails.class, AiQuizGeneratorRequest.class),
                AiService.class.getMethod("quizDraft", SecurityUserDetails.class, QuizDraftRequest.class),
                AiService.class.getMethod("storyGenerator", SecurityUserDetails.class, AiStoryGeneratorRequest.class),
                XiaomeiService.class.getMethod("reply", SecurityUserDetails.class, XiaomeiReplyRequest.class),
                XiaomeiService.class.getMethod("summary", SecurityUserDetails.class, XiaomeiConversationRequest.class),
                AiPracticeCheckService.class.getMethod("check", SecurityUserDetails.class,
                        AiPracticeCheckService.CheckRequest.class),
                LearningPlanService.class.getMethod("build", SecurityUserDetails.class, PlanRequests.BuildRequest.class),
                LearningPlanService.class.getMethod("updateGoal", SecurityUserDetails.class,
                        PlanRequests.UpdateGoalRequest.class),
                LearningPlanService.class.getMethod("retake", SecurityUserDetails.class, PlanRequests.RetakeRequest.class),
                PlanAdvisor.class.getMethod("draft", User.class, String.class, String.class,
                        PlanComposer.Goal.class, PlanComposer.Placement.class, PlanComposer.Candidates.class,
                        LocalDate.class));
    }

    private static void assertOutsideTransactionBoundary(Method method) {
        assertThat(AnnotatedElementUtils.hasAnnotation(method, Transactional.class))
                .as("%s should not be Spring @Transactional", method)
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), Transactional.class))
                .as("%s should not inherit a class-level Spring @Transactional", method)
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(method, jakarta.transaction.Transactional.class))
                .as("%s should not be Jakarta @Transactional", method)
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), jakarta.transaction.Transactional.class))
                .as("%s should not inherit a class-level Jakarta @Transactional", method)
                .isFalse();
    }
}
