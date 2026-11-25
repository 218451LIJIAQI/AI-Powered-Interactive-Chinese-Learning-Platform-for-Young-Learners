package com.example.aichinese;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

import com.example.aichinese.dto.account.NavCountsResponse;
import com.example.aichinese.entity.Role;
import com.example.aichinese.entity.User;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.entity.enums.RoleName;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.repository.LearningMistakeRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.navigation.NavCountService;
import com.example.aichinese.service.support.SupportService;

/** Each role sees only its own sidebar badges. */
class NavCountServiceTests {

    private static final Long USER_ID = 5L;
    private final LearningMistakeRepository mistakeRepository = mock(LearningMistakeRepository.class);
    private final AiResponseReportRepository aiResponseReportRepository = mock(AiResponseReportRepository.class);
    private final SupportService supportService = mock(SupportService.class);
    private final NavCountService service = new NavCountService(mistakeRepository, aiResponseReportRepository, supportService);

    @Test
    void adminsSeeTheAiReportsAndSupportRequestsWaitingForThem() {
        when(aiResponseReportRepository.countByStatus(AiResponseReportStatus.PENDING_REVIEW)).thenReturn(1L);
        when(supportService.countOpen()).thenReturn(4L);

        assertThat(service.countsFor(user(RoleName.ADMIN))).isEqualTo(new NavCountsResponse(null, 1L, 4L));
        // The admins' daily summary e-mail reports the same numbers as these badges.
        assertThat(service.adminQueueCounts()).isEqualTo(new NavCountService.AdminQueueCounts(1L, 4L));
    }

    @Test
    void learnersSeeTheirMistakesOnly() {
        when(mistakeRepository.countByStudentUserIdAndClearedAtIsNull(USER_ID)).thenReturn(6L);

        assertThat(service.countsFor(user(RoleName.STUDENT))).isEqualTo(new NavCountsResponse(6L, null, null));
        verify(supportService, never()).countOpen();
    }

    @Test
    void anAccountWithBothRolesGetsBadgesForBothWorkspaces() {
        when(mistakeRepository.countByStudentUserIdAndClearedAtIsNull(USER_ID)).thenReturn(6L);
        when(aiResponseReportRepository.countByStatus(AiResponseReportStatus.PENDING_REVIEW)).thenReturn(1L);
        when(supportService.countOpen()).thenReturn(4L);

        assertThat(service.countsFor(user(RoleName.ADMIN, RoleName.STUDENT)))
                .isEqualTo(new NavCountsResponse(6L, 1L, 4L));
    }

    private static SecurityUserDetails user(RoleName... roles) {
        User user = new User();
        user.setId(USER_ID);
        user.setEmail("someone@example.com");
        user.setPasswordHash("hash");
        user.setDisplayName("Someone");
        for (RoleName name : roles) {
            Role role = new Role();
            role.setName(name);
            user.getRoles().add(role);
        }
        return new SecurityUserDetails(user);
    }
}
