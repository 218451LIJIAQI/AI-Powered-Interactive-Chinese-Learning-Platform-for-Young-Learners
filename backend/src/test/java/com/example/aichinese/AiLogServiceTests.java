package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiFeedbackRecord;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.UploadedFile;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiFeedbackRecordRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.LessonRepository;
import com.example.aichinese.service.AiLogService;

/**
 * Unit coverage for {@code AiLogService} — pins the typed sender-role
 * contract for chat messages and the privacy-preserving
 * {@code content_length=N} metadata stored on usage-log and feedback rows
 * (instead of raw prompts, replies or learner uploads).
 */
class AiLogServiceTests {

    @Test
    void appendChatMessageUsesTypedSenderRoleContract() {
        AiChatMessageRepository aiChatMessageRepository = mock(AiChatMessageRepository.class);
        saveArgument(aiChatMessageRepository);
        AiLogService aiLogService = aiLogService(aiChatMessageRepository);
        AiChatSession session = new AiChatSession();
        session.setId(123L);

        AiChatMessage saved = aiLogService.appendChatMessage(
                session,
                AiChatSenderRole.ASSISTANT,
                "Safe retained assistant response");

        assertThat(saved.getSession()).isSameAs(session);
        assertThat(saved.getSenderRole()).isEqualTo(AiChatSenderRole.ASSISTANT);
        assertThat(saved.getMessageText()).isEqualTo("Safe retained assistant response");
        verify(aiChatMessageRepository).save(any(AiChatMessage.class));
    }

    @Test
    void usageLogStoresContentLengthMetadataInsteadOfPromptOrResponseText() {
        AiUsageLogRepository aiUsageLogRepository = mock(AiUsageLogRepository.class);
        saveArgument(aiUsageLogRepository);
        AiLogService aiLogService = aiLogService(aiUsageLogRepository);
        User user = new User();
        user.setId(10L);

        AiUsageLog saved = aiLogService.createUsageLog(
                user,
                "STUDENT",
                AiFeatureKey.CHAT_TUTOR,
                "en",
                AiRequestStatus.SUCCESS,
                "deepseek",
                "deepseek-chat",
                7,
                "Adult prompt with private learning details",
                "Provider answer that should not be retained in usage logs",
                null,
                "AiChatSession",
                99L);

        assertThat(saved.getInputSummary()).isEqualTo("content_length=42");
        assertThat(saved.getTokenCount()).isEqualTo(7);
        assertThat(saved.getOutputSummary()).isEqualTo("content_length=57");
        assertThat(saved.getInputSummary()).doesNotContain("Adult prompt");
        assertThat(saved.getOutputSummary()).doesNotContain("Provider answer");
        verify(aiUsageLogRepository).save(any(AiUsageLog.class));
    }

    @Test
    void feedbackRecordStoresOnlyMinimalMetadataForSensitiveAiContent() {
        AiFeedbackRecordRepository aiFeedbackRecordRepository = mock(AiFeedbackRecordRepository.class);
        saveArgument(aiFeedbackRecordRepository);
        AiLogService aiLogService = aiLogService(aiFeedbackRecordRepository);
        User user = new User();
        user.setId(11L);
        UploadedFile uploadedFile = new UploadedFile();
        uploadedFile.setId(88L);
        uploadedFile.setStoredFileName("private-storage-name.webm");

        AiFeedbackRecord saved = aiLogService.createFeedbackRecord(
                user,
                AiFeatureKey.PRONUNCIATION_FEEDBACK,
                "en",
                "Target text: hello adult learner",
                "Detailed coaching feedback that should not be retained verbatim",
                AiRequestStatus.UNAVAILABLE,
                uploadedFile);

        assertThat(saved.getInputText()).isEqualTo("content_length=32");
        assertThat(saved.getFeedbackText()).isEqualTo("content_length=63");
        assertThat(saved.getUploadedFile()).isSameAs(uploadedFile);
        assertThat(saved.getInputFileUrl()).isNull();
        verify(aiFeedbackRecordRepository).save(any(AiFeedbackRecord.class));
    }

    private static AiLogService aiLogService(AiUsageLogRepository aiUsageLogRepository) {
        return new AiLogService(
                aiUsageLogRepository,
                mock(AiFeedbackRecordRepository.class),
                mock(AiChatSessionRepository.class),
                mock(AiChatMessageRepository.class),
                mock(LessonRepository.class));
    }

    private static AiLogService aiLogService(AiFeedbackRecordRepository aiFeedbackRecordRepository) {
        return new AiLogService(
                mock(AiUsageLogRepository.class),
                aiFeedbackRecordRepository,
                mock(AiChatSessionRepository.class),
                mock(AiChatMessageRepository.class),
                mock(LessonRepository.class));
    }

    private static AiLogService aiLogService(AiChatMessageRepository aiChatMessageRepository) {
        return new AiLogService(
                mock(AiUsageLogRepository.class),
                mock(AiFeedbackRecordRepository.class),
                mock(AiChatSessionRepository.class),
                aiChatMessageRepository,
                mock(LessonRepository.class));
    }

    private static void saveArgument(AiUsageLogRepository repository) {
        when(repository.save(any(AiUsageLog.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void saveArgument(AiFeedbackRecordRepository repository) {
        when(repository.save(any(AiFeedbackRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private static void saveArgument(AiChatMessageRepository repository) {
        when(repository.save(any(AiChatMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }
}
