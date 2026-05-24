package com.example.aichinese.service;

import java.nio.charset.StandardCharsets;

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

/**
 * Persists the audit / observability tail of every AI invocation:
 * usage logs, chat sessions, chat messages, and feedback records.
 *
 * <p>Centralising the writes here keeps {@link AiService} focused on
 * the prompt-and-respond pipeline and ensures consistent privacy
 * handling — child-facing AI uploads and feedback can contain
 * sensitive personal content, so {@link AiFeedbackRecord#getInputText()}
 * and {@link AiFeedbackRecord#getFeedbackText()} are stored as
 * length-only metadata ({@code "content_length=N"}), not the raw
 * text. Operational signals (status, provider, model, token count,
 * input/output summaries) live in {@link AiUsageLog}, with
 * {@link #summarizeForTinyText} guarding against multi-byte UTF-8
 * truncation that would otherwise corrupt the column.</p>
 */
@Service
public class AiLogService {

    public static final int TINYTEXT_SAFE_BYTES = 240;

    private final AiUsageLogRepository aiUsageLogRepository;
    private final AiFeedbackRecordRepository aiFeedbackRecordRepository;
    private final AiChatSessionRepository aiChatSessionRepository;
    private final AiChatMessageRepository aiChatMessageRepository;

    public AiLogService(
            AiUsageLogRepository aiUsageLogRepository,
            AiFeedbackRecordRepository aiFeedbackRecordRepository,
            AiChatSessionRepository aiChatSessionRepository,
            AiChatMessageRepository aiChatMessageRepository) {
        this.aiUsageLogRepository = aiUsageLogRepository;
        this.aiFeedbackRecordRepository = aiFeedbackRecordRepository;
        this.aiChatSessionRepository = aiChatSessionRepository;
        this.aiChatMessageRepository = aiChatMessageRepository;
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
        // Child-facing AI uploads and feedback can contain sensitive personal
        // content. Retain only bounded metadata in the feedback table; richer
        // operational signals already live in AiUsageLog as content-length
        // summaries plus status/provider metadata.
        record.setInputText(contentMetadata(inputText));
        record.setFeedbackText(contentMetadata(feedbackText));
        record.setStatus(status.name());
        record.setUploadedFile(uploadedFile);
        // The foreign key to UploadedFile is sufficient to trace the stored
        // blob. Avoid duplicating internal storage names into a second column.
        record.setInputFileUrl(null);
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

    private String defaultSessionTitle(AiFeatureKey featureKey) {
        return switch (featureKey) {
            case CHAT_TUTOR -> "AI Tutor Chat";
            default -> featureKey.name();
        };
    }

    public static String summarizeForTinyText(String value) {
        return truncateUtf8Bytes(value, TINYTEXT_SAFE_BYTES);
    }

    private static String truncateUtf8Bytes(String value, int maxBytes) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        byte[] bytes = trimmed.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= maxBytes) {
            return trimmed;
        }

        int endIndex = 0;
        int byteCount = 0;
        while (endIndex < trimmed.length()) {
            String candidate = trimmed.substring(endIndex, endIndex + 1);
            int candidateBytes = candidate.getBytes(StandardCharsets.UTF_8).length;
            if (byteCount + candidateBytes > maxBytes) {
                break;
            }
            byteCount += candidateBytes;
            endIndex++;
        }

        return trimmed.substring(0, endIndex);
    }

    private String summarize(String value, int limit) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return trimmed.length() <= limit ? trimmed : trimmed.substring(0, limit);
    }

    private String contentMetadata(String value) {
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
