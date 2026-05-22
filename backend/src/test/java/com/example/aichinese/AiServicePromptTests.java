package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.lang.reflect.Method;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.ai.AiLearningRecommendationRequest;
import com.example.aichinese.dto.ai.AiQuizGeneratorRequest;
import com.example.aichinese.dto.ai.AiVocabularyHelperRequest;
import com.example.aichinese.service.AiService;
import com.example.aichinese.service.AiFileStorageService;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LessonProgressRepository;
import com.example.aichinese.repository.QuizAttemptAnswerRepository;
import com.example.aichinese.repository.StudentProfileRepository;
import com.example.aichinese.repository.UserRepository;
import com.example.aichinese.service.StudentLearningAccessService;
import com.example.aichinese.service.ai.AiAvailabilityService;
import com.example.aichinese.service.ai.AiPromptBuilder;
import com.example.aichinese.service.ai.AiProviderRouter;
import com.fasterxml.jackson.databind.ObjectMapper;

class AiServicePromptTests {

    @Test
    void vocabularyPromptUsesRequestedOutputLanguage() throws Exception {
        AiService service = newService();

        String prompt = invokeBuildVocabularyPrompt(service, new AiVocabularyHelperRequest(
                "apple",
                "en",
                "zh",
                "Beginner",
                "en"));

        assertThat(prompt).contains("Output language preference: zh");
    }

    @Test
    void quizPromptIncludesTargetRoleContextWhenPresent() throws Exception {
        AiService service = newService();

        String prompt = invokeBuildQuizPrompt(service, new AiQuizGeneratorRequest(
                "animals",
                "Beginner",
                "Vocabulary",
                3,
                List.of("MULTIPLE_CHOICE"),
                "teacher",
                "en"));

        assertThat(prompt).contains("Target role context: teacher");
    }

    @Test
    void learningRecommendationPromptIncludesSupplementalStudentContext() throws Exception {
        AiService service = newService();

        String prompt = invokeBuildLearningRecommendationPrompt(service, new AiLearningRecommendationRequest(
                7L,
                "Beginner",
                List.of("quiz 1"),
                List.of("question 2"),
                List.of("tones"),
                List.of("lesson 3"),
                "en"));

        assertThat(prompt).contains("Recent quiz results: quiz 1");
        assertThat(prompt).contains("Wrong answers: question 2");
        assertThat(prompt).contains("Weak areas: tones");
        assertThat(prompt).contains("Completed lessons: lesson 3");
    }

    private AiService newService() {
        return new AiService(
                mock(UserRepository.class),
                mock(StudentProfileRepository.class),
                mock(QuizAttemptAnswerRepository.class),
                mock(LessonProgressRepository.class),
                mock(StudentLearningAccessService.class),
                mock(AiChatSessionRepository.class),
                mock(AiChatMessageRepository.class),
                mock(AiUsageLogRepository.class),
                mock(AiPromptBuilder.class),
                mock(AiProviderRouter.class),
                mock(AiAvailabilityService.class),
                mock(AiLogService.class),
                mock(AiFileStorageService.class),
                new ObjectMapper());
    }

    private String invokeBuildVocabularyPrompt(AiService service, AiVocabularyHelperRequest request) throws Exception {
        Method method = AiService.class.getDeclaredMethod("buildVocabularyPrompt", AiVocabularyHelperRequest.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, request, "en");
    }

    private String invokeBuildQuizPrompt(AiService service, AiQuizGeneratorRequest request) throws Exception {
        Method method = AiService.class.getDeclaredMethod("buildQuizPrompt", AiQuizGeneratorRequest.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, request, "en", "TEMPORARY_SELF_PRACTICE");
    }

    private String invokeBuildLearningRecommendationPrompt(AiService service, AiLearningRecommendationRequest request) throws Exception {
        Method method = AiService.class.getDeclaredMethod("buildLearningRecommendationPrompt", AiLearningRecommendationRequest.class, String.class, String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, request, "summary", "en");
    }
}
