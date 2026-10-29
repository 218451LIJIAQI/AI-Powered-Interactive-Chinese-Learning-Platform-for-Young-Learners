package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.entity.AiChatMessage;
import com.example.aichinese.entity.AiChatSession;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiChatSenderRole;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiChatMessageRepository;
import com.example.aichinese.repository.AiChatSessionRepository;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.AuditLogService;

/**
 * Unit coverage for {@code AiLogService}: the typed sender role of chat messages, the privacy-preserving
 * {@code content_length=N} metadata stored instead of prompts and replies, the role a request is counted under, the
 * usage totals by feature and the admin log filters.
 */
class AiLogServiceTests {

    private final AiUsageLogRepository aiUsageLogRepository = mock(AiUsageLogRepository.class);
    private final AiChatMessageRepository aiChatMessageRepository = mock(AiChatMessageRepository.class);
    private final AiLogService aiLogService = new AiLogService(
            aiUsageLogRepository, mock(AiChatSessionRepository.class), aiChatMessageRepository,
            mock(AuditLogService.class));

    @Test
    void appendChatMessageUsesTypedSenderRoleContract() {
        when(aiChatMessageRepository.save(any(AiChatMessage.class))).thenAnswer(returnsFirstArg());
        AiChatSession session = new AiChatSession();
        session.setId(123L);

        AiChatMessage saved = aiLogService.appendChatMessage(
                session, AiChatSenderRole.ASSISTANT, "Safe retained assistant response");

        assertThat(saved.getSession()).isSameAs(session);
        assertThat(saved.getSenderRole()).isEqualTo(AiChatSenderRole.ASSISTANT);
        assertThat(saved.getMessageText()).isEqualTo("Safe retained assistant response");
        verify(aiChatMessageRepository).save(any(AiChatMessage.class));
    }

    @Test
    void usageLogStoresContentLengthMetadataInsteadOfPromptOrResponseText() {
        when(aiUsageLogRepository.save(any(AiUsageLog.class))).thenAnswer(returnsFirstArg());

        AiUsageLog saved = aiLogService.logUsage(new AiLogService.UsageEntry(
                user(RoleName.STUDENT), AiFeatureKey.XIAOMEI_CONVERSATION, "en", AiRequestStatus.SUCCESS,
                "openai", "gpt-5.6-luna", 7,
                "Adult prompt with private learning details",
                "Provider answer that should not be retained in usage logs",
                null, null, null));

        assertThat(saved.getInputSummary()).isEqualTo("content_length=42");
        assertThat(saved.getOutputSummary()).isEqualTo("content_length=57");
        assertThat(saved.getTokenCount()).isEqualTo(7);
        assertThat(saved.getUserRole()).isEqualTo("STUDENT");
        assertThat(saved.getFeatureKey()).isEqualTo(AiFeatureKey.XIAOMEI_CONVERSATION);
        assertThat(saved.getStatus()).isEqualTo("SUCCESS");
    }

    @Test
    void summaryCountsEveryFeatureIncludingUnusedOnesAndSplitsResults() {
        when(aiUsageLogRepository.countByFeatureAndStatus()).thenReturn(List.of(
                row(AiFeatureKey.CHAT_TUTOR, "SUCCESS", 6),
                row(AiFeatureKey.CHAT_TUTOR, "FAILED", 1),
                row(AiFeatureKey.STORY_GENERATOR, "UNAVAILABLE", 2),
                row(AiFeatureKey.TEACHER_DRAFT, "SUCCESS", 1)));

        AdminAiUsageSummaryResponse summary = aiLogService.getUsageSummary();

        assertThat(summary.totalRequests()).isEqualTo(10);
        assertThat(summary.successful()).isEqualTo(7);
        assertThat(summary.unavailable()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.byFeature()).extracting(AdminAiUsageSummaryResponse.FeatureCount::featureKey)
                .containsExactly("CHAT_TUTOR", "XIAOMEI_CONVERSATION", "QUIZ_GENERATOR", "STORY_GENERATOR",
                        "PRACTICE_CHECK", "TEACHER_DRAFT", "LEARNING_PLAN");
        assertThat(summary.byFeature()).extracting(AdminAiUsageSummaryResponse.FeatureCount::requests)
                .containsExactly(7L, 0L, 0L, 2L, 0L, 1L, 0L);
    }

    @Test
    void usageFiltersAcceptKnownValuesAndAllAndRejectOthers() {
        AiLogService.UsageFilter filter = AiLogService.UsageFilter.parse("practice_check", "failed", "teacher");
        assertThat(filter.feature()).isEqualTo("PRACTICE_CHECK");
        assertThat(filter.status()).isEqualTo("FAILED");
        assertThat(filter.role()).isEqualTo("TEACHER");
        assertThat(AiLogService.UsageFilter.parse("ALL", "", null).feature()).isNull();

        assertThatThrownBy(() -> AiLogService.UsageFilter.parse("VOCABULARY_HELPER", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid AI feature filter.");
        assertThatThrownBy(() -> AiLogService.UsageFilter.parse(null, "LATE", null))
                .hasMessageContaining("Invalid AI result filter.");
    }

    private static AiUsageLogRepository.FeatureStatusCount row(AiFeatureKey feature, String status, long count) {
        return new AiUsageLogRepository.FeatureStatusCount() {
            @Override
            public AiFeatureKey getFeatureKey() {
                return feature;
            }

            @Override
            public String getStatus() {
                return status;
            }

            @Override
            public long getCount() {
                return count;
            }
        };
    }

    private static User user(RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(10L);
        user.setRoles(Set.of(role));
        return user;
    }
}
