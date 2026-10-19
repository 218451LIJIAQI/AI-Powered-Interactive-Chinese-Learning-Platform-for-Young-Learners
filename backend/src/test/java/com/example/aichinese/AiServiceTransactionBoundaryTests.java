package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.entity.User;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiFileStorageService;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.PracticeSubmissionService;

class AiServiceTransactionBoundaryTests {

    @Test
    void textProviderEndpointsDoNotWrapRemoteCallsInDatabaseTransactions() throws Exception {
        assertThat(textProviderEndpoints())
                .allSatisfy(AiServiceTransactionBoundaryTests::assertOutsideTransactionBoundary);
    }

    @Test
    void multipartFeedbackFileCopiesDoNotRunInsideDatabaseTransactions() throws Exception {
        assertThat(fileCopyingEndpoints())
                .allSatisfy(AiServiceTransactionBoundaryTests::assertOutsideTransactionBoundary);
    }

    @Test
    void auditedAdminPracticeFileViewRunsInAWritableTransaction() throws Exception {
        // The audit row is inserted inside this call; a read-only transaction would put the JDBC
        // connection in read-only mode (MySQL rejects the INSERT).
        Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(
                method(PracticeSubmissionService.class, "loadAdminFile", SecurityUserDetails.class, Long.class),
                Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.readOnly()).isFalse();
    }

    private static List<Method> textProviderEndpoints() throws NoSuchMethodException {
        return List.of(
                method(AiService.class, "chat", SecurityUserDetails.class, AiChatRequest.class),
                method(AiService.class, "vocabularyHelper", SecurityUserDetails.class, AiVocabularyHelperRequest.class),
                method(AiService.class, "sentencePractice", SecurityUserDetails.class, AiSentencePracticeRequest.class),
                method(AiService.class, "quizGenerator", SecurityUserDetails.class, AiQuizGeneratorRequest.class),
                method(AiService.class, "storyGenerator", SecurityUserDetails.class, AiStoryGeneratorRequest.class));
    }

    private static List<Method> fileCopyingEndpoints() throws NoSuchMethodException {
        return List.of(
                // The upload endpoints also persist the practice submission (D7a) and stay outside a transaction.
                method(
                        AiService.class,
                        "pronunciationFeedback",
                        SecurityUserDetails.class,
                        MultipartFile.class,
                        String.class,
                        String.class,
                        String.class,
                        String.class,
                        Long.class),
                method(
                        AiService.class,
                        "writingFeedback",
                        SecurityUserDetails.class,
                        MultipartFile.class,
                        String.class,
                        String.class,
                        Long.class),
                method(AiFileStorageService.class, "storePronunciationAudio", MultipartFile.class, User.class),
                method(AiFileStorageService.class, "storeWritingImage", MultipartFile.class, User.class));
    }

    private static Method method(Class<?> owner, String name, Class<?>... parameterTypes) throws NoSuchMethodException {
        return owner.getMethod(name, parameterTypes);
    }

    private static void assertOutsideTransactionBoundary(Method method) {
        assertThat(hasTransactionalAnnotation(method))
                .as("%s should not be transactional", method)
                .isFalse();
        assertThat(hasTransactionalAnnotation(method.getDeclaringClass()))
                .as("%s should not inherit a class-level transaction", method)
                .isFalse();
    }

    private static boolean hasTransactionalAnnotation(Method method) {
        return AnnotatedElementUtils.hasAnnotation(method, Transactional.class);
    }

    private static boolean hasTransactionalAnnotation(Class<?> type) {
        return AnnotatedElementUtils.hasAnnotation(type, Transactional.class);
    }
}
