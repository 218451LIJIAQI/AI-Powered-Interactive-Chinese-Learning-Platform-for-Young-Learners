package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.aichinese.dto.ai.AiChatRequest;
import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiSentencePracticeRequest;
import com.example.aichinese.dto.ai.AiStoryGeneratorRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiFileStorageService;
import com.example.aichinese.service.AiService;

class AiServiceTransactionBoundaryTests {

    @Test
    void textProviderEndpointsDoNotWrapRemoteCallsInDatabaseTransactions() throws Exception {
        List<Method> providerCallingMethods = List.of(
                AiService.class.getMethod("chat", SecurityUserDetails.class, AiChatRequest.class),
                AiService.class.getMethod("vocabularyHelper", SecurityUserDetails.class, AiVocabularyHelperRequest.class),
                AiService.class.getMethod("sentencePractice", SecurityUserDetails.class, AiSentencePracticeRequest.class),
                AiService.class.getMethod("quizGenerator", SecurityUserDetails.class, AiQuizGeneratorRequest.class),
                AiService.class.getMethod("storyGenerator", SecurityUserDetails.class, AiStoryGeneratorRequest.class),
                AiService.class.getMethod("learningRecommendation", SecurityUserDetails.class, AiLearningRecommendationRequest.class));

        assertThat(providerCallingMethods)
                .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    }

    @Test
    void multipartFeedbackFileCopiesDoNotRunInsideDatabaseTransactions() throws Exception {
        List<Method> fileCopyingMethods = List.of(
                AiService.class.getMethod("pronunciationFeedback", SecurityUserDetails.class, MultipartFile.class,
                        String.class, String.class, String.class, String.class),
                AiService.class.getMethod("writingFeedback", SecurityUserDetails.class, MultipartFile.class,
                        String.class, String.class),
                AiFileStorageService.class.getMethod("storePronunciationAudio", MultipartFile.class,
                        com.example.aichinese.entity.User.class),
                AiFileStorageService.class.getMethod("storeWritingImage", MultipartFile.class,
                        com.example.aichinese.entity.User.class));

        assertThat(fileCopyingMethods)
                .noneMatch(method -> method.isAnnotationPresent(Transactional.class));
    }
}
