package com.example.aichinese.service;

import java.nio.charset.StandardCharsets;
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

        int byteCount = 0;
        int charIndex = 0;
        while (charIndex < trimmed.length()) {
            int codePoint = trimmed.codePointAt(charIndex);
            int cpBytes = utf8ByteLength(codePoint);
            if (byteCount + cpBytes > maxBytes) {
                break;
            }
            byteCount += cpBytes;
            charIndex += Character.charCount(codePoint);
        }

        return trimmed.substring(0, charIndex);
    }

    private static int utf8ByteLength(int codePoint) {
        if (codePoint <= 0x7F) return 1;
        if (codePoint <= 0x7FF) return 2;
        if (codePoint <= 0xFFFF) return 3;
        return 4;
    }

    private String summarize(String value, int limit) {
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
