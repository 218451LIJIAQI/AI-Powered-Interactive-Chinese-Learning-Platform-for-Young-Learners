package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.XiaomeiConversationRequest;
import com.example.aichinese.dto.ai.XiaomeiReplyRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiPracticeCheckService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.XiaomeiService;
import com.example.aichinese.service.ai.AiGateway;

/** A remote AI call (up to the provider timeout) must never hold a database connection and row locks open. */
class AiServiceTransactionBoundaryTests {

    @Test
    void aiRequestsDoNotWrapRemoteCallsInDatabaseTransactions() throws Exception {
        assertThat(aiRequestMethods())
                .allSatisfy(AiServiceTransactionBoundaryTests::assertOutsideTransactionBoundary);
    }

    private static List<Method> aiRequestMethods() throws NoSuchMethodException {
        return List.of(
                method(AiGateway.class, "execute", AiGateway.AiCall.class),
                method(AiService.class, "chat", SecurityUserDetails.class, AiChatRequest.class),
                method(AiService.class, "quizGenerator", SecurityUserDetails.class, AiQuizGeneratorRequest.class),
                method(AiService.class, "storyGenerator", SecurityUserDetails.class, AiStoryGeneratorRequest.class),
                method(XiaomeiService.class, "reply", SecurityUserDetails.class, XiaomeiReplyRequest.class),
                method(XiaomeiService.class, "suggestions", SecurityUserDetails.class, XiaomeiConversationRequest.class),
                method(XiaomeiService.class, "summary", SecurityUserDetails.class, XiaomeiConversationRequest.class),
                method(AiPracticeCheckService.class, "check", SecurityUserDetails.class,
                        AiPracticeCheckService.CheckRequest.class));
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return owner.getMethod(name, parameterTypes);
    }

    private static void assertOutsideTransactionBoundary(Method method) {
        assertThat(AnnotatedElementUtils.hasAnnotation(method, Transactional.class))
                .as("%s should not be transactional", method)
                .isFalse();
        assertThat(AnnotatedElementUtils.hasAnnotation(method.getDeclaringClass(), Transactional.class))
                .as("%s should not inherit a class-level transaction", method)
                .isFalse();
    }
}
