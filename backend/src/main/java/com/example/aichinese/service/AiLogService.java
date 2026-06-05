package com.example.aichinese.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

@Service
public class AiLogService {

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiFeedbackRecordRepository aiFeedbackRecordRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;

    public AiLogService(
            AiUsageLogRepository aiUsageLogRepository,
            AiFeedbackRecordRepository aiFeedbackRecordRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository) {
        this.aiUsageLogRepository = Objects.requireNonNull(aiUsageLogRepository, "aiUsageLogRepository");
        this.aiFeedbackRecordRepository = Objects.requireNonNull(aiFeedbackRecordRepository, "aiFeedbackRecordRepository");
        this.aiChatSessionRepository = Objects.requireNonNull(aiChatSessionRepository, "aiChatSessionRepository");
        this.aiChatMessageRepository = Objects.requireNonNull(aiChatMessageRepository, "aiChatMessageRepository");
    }

    @Transactional
    public AiChatSession createChatSession(User user, AiFeatureKey featureKey, String languagePreference, Long relatedLessonId) {
        AiChatSession session = new AiChatSession();
        session.setUser(user);
        session.setFeatureKey(featureKey.name());
        session.setSessionTitle(defaultSessionTitle(featureKey));
        session.setLanguagePreference(languagePreference);
        session.setRelatedLessonId(relatedLessonId);
        session.setActive(true);
        return aiChatSessionRepository.save(session);
    }

    @Transactional
    public AiChatMessage appendChatMessage(AiChatSession session, AiChatSenderRole senderRole, String messageText) {
        AiChatMessage message = new AiChatMessage();
        message.setSession(session);
        message.setSenderRole(senderRole);
        message.setMessageText(messageText);
        return aiChatMessageRepository.save(message);
    }

    @Transactional
    public AiFeedbackRecord createFeedbackRecord(
            User user,
            AiFeatureKey featureKey,
            String languagePreference,
            String inputText,
            String feedbackText,
            AiRequestStatus status,
            UploadedFile uploadedFile) {
        AiFeedbackRecord record = new AiFeedbackRecord();
        record.setUser(user);
        record.setFeatureKey(featureKey.name());
        record.setLanguagePreference(languagePreference);
        record.setInputText(contentMetadata(inputText));
        record.setFeedbackText(contentMetadata(feedbackText));
        record.setStatus(status.name());
        record.setUploadedFile(uploadedFile);
        return aiFeedbackRecordRepository.save(record);
    }

    @Transactional
    public AiUsageLog createUsageLog(
            User user,
            String userRole,
            AiFeatureKey featureKey,
            String languagePreference,
            AiRequestStatus status,
            String providerName,
            String modelName,
            Integer tokenCount,
            String inputSummary,
            String outputSummary,
            String errorSummary,
            String relatedEntityType,
            Long relatedEntityId) {
        AiUsageLog usageLog = new AiUsageLog();
        usageLog.setUser(user);
        usageLog.setUserRole(userRole);
        usageLog.setFeatureKey(featureKey.name());
        usageLog.setLanguagePreference(languagePreference);
        usageLog.setStatus(status.name());
        usageLog.setProviderName(providerName);
        usageLog.setModelName(modelName);
        usageLog.setTokenCount(tokenCount);
        usageLog.setInputSummary(contentMetadata(inputSummary));
        usageLog.setOutputSummary(contentMetadata(outputSummary));
        usageLog.setErrorSummary(summarize(errorSummary, 255));
        usageLog.setRelatedEntityType(relatedEntityType);
        usageLog.setRelatedEntityId(relatedEntityId);
        return aiUsageLogRepository.save(usageLog);
    }

    private static String defaultSessionTitle(AiFeatureKey featureKey) {
        return switch (featureKey) {
            case CHAT_TUTOR -> "AI Tutor Chat";
            default -> featureKey.name();
        };
    }

    private static String summarize(String value, int limit) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        if (trimmed.length() <= limit) {
            return trimmed;
        }
        int end = limit;
        if (Character.isHighSurrogate(trimmed.charAt(end - 1))) {
            end--;
        }
        return trimmed.substring(0, end);
    }

    private static String contentMetadata(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return "content_length=" + trimmed.length();
    }
}
