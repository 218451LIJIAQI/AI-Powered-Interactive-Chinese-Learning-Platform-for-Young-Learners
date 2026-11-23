package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.AdditionalAnswers.returnsFirstArg;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.PlatformProperties;
import com.example.aichinese.dto.ai.AdminAiUsageSummaryResponse;
import com.example.aichinese.entity.AiUsageLog;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiFeatureKey;
import com.example.aichinese.entity.enums.AiRequestStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiUsageLogRepository;
import com.example.aichinese.repository.AiUsageLogRepository.FeatureStatusCount;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiLogService;
import com.example.aichinese.service.AuditLogService;
import com.example.aichinese.service.account.CurrentAccount;
import com.example.aichinese.service.learn.LearnClock;

/**
 * Unit coverage for {@code AiLogService}: the privacy-preserving {@code content_length=N} metadata stored instead of
 * prompts and replies, the role a request is counted under, the usage totals by feature and the admin log filters.
 */
class AiLogServiceTests {

    private final AiUsageLogRepository aiUsageLogRepository = mock(AiUsageLogRepository.class);
    private final CurrentAccount currentAccount = mock(CurrentAccount.class);
    private final LearnClock clock = new LearnClock(new PlatformProperties("Asia/Singapore"));
    private final AiLogService aiLogService = new AiLogService(
            aiUsageLogRepository, mock(AuditLogService.class), currentAccount, clock);

    @Test
    void usageLogStoresContentLengthMetadataInsteadOfPromptOrResponseText() {
        when(aiUsageLogRepository.save(any(AiUsageLog.class))).thenAnswer(returnsFirstArg());
        User student = user(10L, RoleName.STUDENT);
        when(currentAccount.lock(student.getId(), student.getSessionVersion(), null)).thenReturn(student);

        AiUsageLog saved = aiLogService.logUsage(new AiLogService.UsageEntry(
                student, null, AiFeatureKey.XIAOMEI_CONVERSATION, "en", AiRequestStatus.SUCCESS,
                "openai", "gpt-5.6-luna", 7,
                "Adult prompt with private learning details",
                "Provider answer that should not be retained in usage logs",
                null));

        assertThat(saved.getInputSummary()).isEqualTo("content_length=42");
        assertThat(saved.getOutputSummary()).isEqualTo("content_length=57");
        assertThat(saved.getTokenCount()).isEqualTo(7);
        assertThat(saved.getUserRole()).isEqualTo("STUDENT");
        assertThat(saved.getFeatureKey()).isEqualTo(AiFeatureKey.XIAOMEI_CONVERSATION);
        assertThat(saved.getStatus()).isEqualTo(AiRequestStatus.SUCCESS);
    }

    @Test
    void summaryCountsEveryFeatureIncludingUnusedOnesAndSplitsResults() {
        when(aiUsageLogRepository.countByFeatureAndStatus()).thenReturn(List.of(
                new FeatureStatusCount(AiFeatureKey.CHAT_TUTOR, AiRequestStatus.SUCCESS, 6),
                new FeatureStatusCount(AiFeatureKey.CHAT_TUTOR, AiRequestStatus.FAILED, 1),
                new FeatureStatusCount(AiFeatureKey.STORY_GENERATOR, AiRequestStatus.UNAVAILABLE, 2),
                new FeatureStatusCount(AiFeatureKey.QUIZ_DRAFT, AiRequestStatus.SUCCESS, 1)));

        AdminAiUsageSummaryResponse summary = aiLogService.getUsageSummary();

        assertThat(summary.totalRequests()).isEqualTo(10);
        assertThat(summary.successful()).isEqualTo(7);
        assertThat(summary.unavailable()).isEqualTo(2);
        assertThat(summary.failed()).isEqualTo(1);
        assertThat(summary.byFeature())
                .extracting(AdminAiUsageSummaryResponse.FeatureCount::featureKey,
                        AdminAiUsageSummaryResponse.FeatureCount::requests)
                .containsExactly(tuple(AiFeatureKey.CHAT_TUTOR, 7L), tuple(AiFeatureKey.XIAOMEI_CONVERSATION, 0L),
                        tuple(AiFeatureKey.QUIZ_GENERATOR, 0L), tuple(AiFeatureKey.STORY_GENERATOR, 2L), tuple(AiFeatureKey.PRACTICE_CHECK, 0L),
                        tuple(AiFeatureKey.QUIZ_DRAFT, 1L), tuple(AiFeatureKey.LEARNING_PLAN, 0L));
    }

    @Test
    void usageFiltersAcceptKnownValuesAndAllAndRejectOthers() {
        AiLogService.UsageFilter filter = AiLogService.UsageFilter.parse("practice_check", "failed", "student");
        assertThat(filter.feature()).isEqualTo(AiFeatureKey.PRACTICE_CHECK);
        assertThat(filter.status()).isEqualTo(AiRequestStatus.FAILED);
        assertThat(filter.role()).isEqualTo(RoleName.STUDENT);
        assertThat(AiLogService.UsageFilter.parse("quiz_draft", null, "admin").role()).isEqualTo(RoleName.ADMIN);
        assertThat(AiLogService.UsageFilter.parse("ALL", "", null).feature()).isNull();
        assertThatThrownBy(() -> AiLogService.UsageFilter.parse(null, null, "TEACHER"))
                .hasMessageContaining("Invalid user role filter.");

        assertThatThrownBy(() -> AiLogService.UsageFilter.parse("VOCABULARY_HELPER", null, null))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("Invalid AI feature filter.");
        assertThatThrownBy(() -> AiLogService.UsageFilter.parse(null, "LATE", null))
                .hasMessageContaining("Invalid AI result filter.");
    }

    /** The log pages in the export's order: newest first, the id deciding between rows of the same time. */
    @Test
    void theLogIsPagedInTheExportOrderWithTheIdBreakingTies() {
        when(aiUsageLogRepository.findAdminPage(isNull(), isNull(), isNull(), any(Pageable.class)))
                .thenAnswer(invocation -> Page.empty(invocation.getArgument(3, Pageable.class)));

        AiLogService.UsageFilter all = AiLogService.UsageFilter.parse(null, null, null);
        aiLogService.getUsageLogs(all, 1, 20);
        aiLogService.exportUsageLogsCsv(new SecurityUserDetails(user(1L, RoleName.ADMIN)), all);

        ArgumentCaptor<Pageable> pages = ArgumentCaptor.forClass(Pageable.class);
        verify(aiUsageLogRepository).findAdminPage(isNull(), isNull(), isNull(), pages.capture());
        verify(aiUsageLogRepository).findExportRows(isNull(), isNull(), isNull(), pages.capture());
        assertThat(pages.getAllValues()).extracting(Pageable::getSort)
                .containsOnly(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        assertThat(pages.getAllValues()).extracting(Pageable::getPageNumber, Pageable::getPageSize)
                .containsExactly(tuple(1, 20), tuple(0, AiLogService.MAX_EXPORT_ROWS));
    }

    private static User user(long id, RoleName roleName) {
        Role role = new Role();
        role.setName(roleName);
        User user = new User();
        user.setId(id);
        user.setRoles(Set.of(role));
        return user;
    }
}
