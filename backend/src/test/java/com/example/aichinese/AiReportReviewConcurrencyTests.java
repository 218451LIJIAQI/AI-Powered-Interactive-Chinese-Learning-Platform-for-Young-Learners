package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.pauseAfterLookup;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import com.example.aichinese.dto.ai.ReviewAiResponseReportRequest;
import com.example.aichinese.entity.enums.AiResponseReportStatus;
import com.example.aichinese.repository.AiResponseReportRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AiResponseReportService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * An admin reviews a learner's AI report while the learner's session is refreshed. The review locks the report; a
 * review that locked the learner's account row too would hold up the learner's own account writes, a token refresh
 * among them, until it ends. Here the review pauses right after its locked report lookup while the learner's refresh
 * token is rotated: the rotation must finish meanwhile.
 */
class AiReportReviewConcurrencyTests extends ApiIntegrationTest {

    private static final String CLIENT_IP = "203.0.113.8";
    private static final String USER_AGENT = "concurrency-test";

    @Autowired
    private AiResponseReportService reports;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @MockitoSpyBean
    private AiResponseReportRepository reportRepository;

    @Test
    void aLearnersTokenRefreshDoesNotWaitForTheReviewOfTheirReport() throws Exception {
        String email = uniqueEmail("report-review");
        String learnerToken = api.registerStudent(email);
        long reportId = api.data(mockMvc.perform(api.jsonPost("/api/v1/ai/reports", learnerToken, Map.of(
                        "featureKey", "HINT", "reasonCategory", "UNCLEAR_EXPLANATION",
                        "promptSnapshot", "LISTENING #14 / 2 · /student/practice/listening",
                        "aiResponseSnapshot", "It starts with 一.")))
                .andExpect(status().isOk())
                .andReturn()).path("id").asLong();
        String refreshToken = refreshTokenService.issue(user(email), CLIENT_IP, USER_AGENT, false).rawToken();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        AtomicReference<RefreshTokenService.RefreshSession> rotated = new AtomicReference<>();

        withPausedOperation(pause -> {
            pauseAfterLookup(pause).when(reportRepository).findLockedById(anyLong());
            reports.reviewReport(admin, reportId,
                    new ReviewAiResponseReportRequest("RESOLVED", "Thanks, the hint no longer gives the answer away."));
        }, () -> rotated.set(refreshTokenService.rotate(refreshToken, CLIENT_IP, USER_AGENT)));

        assertThat(rotated.get().user().getEmail()).isEqualTo(email);
        assertThat(reportRepository.findById(reportId).orElseThrow().getStatus())
                .isEqualTo(AiResponseReportStatus.RESOLVED);
    }
}
