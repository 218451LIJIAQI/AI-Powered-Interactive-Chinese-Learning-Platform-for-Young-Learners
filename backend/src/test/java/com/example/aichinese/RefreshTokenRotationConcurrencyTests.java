package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withAfterMethod;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.example.aichinese.repository.RefreshTokenRepository;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.support.ApiIntegrationTest;

/**
 * Two learners' refresh tokens rotated at the same moment. A rotation locks the learner's account row and token row;
 * every learner has the same STUDENT role, so a rotation that locked its account's role rows too would hold up every
 * other learner's rotation (and every sign-up's role insert) until it ends. Here the first rotation pauses right after
 * saving its replacement token (while still holding its account and token write locks) as the second learner's rotation
 * runs: the second must finish meanwhile.
 */
class RefreshTokenRotationConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void twoLearnersRotateTheirRefreshTokensAtOnce() throws Exception {
        String firstEmail = registeredLearner();
        String secondEmail = registeredLearner();
        String firstToken = refreshTokenService.issue(user(firstEmail), false).rawToken();
        String secondToken = refreshTokenService.issue(user(secondEmail), false).rawToken();
        AtomicReference<RefreshTokenService.RefreshSession> first = new AtomicReference<>();
        AtomicReference<RefreshTokenService.RefreshSession> second = new AtomicReference<>();

        withPausedOperation(
                pause -> withAfterMethod(refreshTokenRepository, "save", pause,
                        () -> first.set(refreshTokenService.rotate(firstToken))),
                () -> second.set(refreshTokenService.rotate(secondToken)));

        assertThat(first.get().user().getEmail()).isEqualTo(firstEmail);
        assertThat(second.get().user().getEmail()).isEqualTo(secondEmail);
    }

    /** Two tabs presenting the exact same refresh token concurrently serialize on the row lock: one wins, the other gets 401. */
    @Test
    void concurrentRotationOfSameTokenSucceedsExactlyOnce() throws Exception {
        String email = registeredLearner();
        String rawToken = refreshTokenService.issue(user(email), false).rawToken();
        AtomicReference<RefreshTokenService.RefreshSession> winner = new AtomicReference<>();

        withRowLock(jdbc, "users",
                pause -> withAfterMethod(refreshTokenRepository, "save", pause,
                        () -> winner.set(refreshTokenService.rotate(rawToken))),
                () -> {
                    assertHttpError(() -> refreshTokenService.rotate(rawToken), 401);
                    return null;
                });

        assertThat(winner.get()).isNotNull();
        // The winning replacement token remains valid within the grace window (the losing tab did not revoke the family).
        RefreshTokenService.RefreshSession next = refreshTokenService.rotate(winner.get().refreshToken().rawToken());
        assertThat(next.user().getEmail()).isEqualTo(email);
    }

    private String registeredLearner() throws Exception {
        String email = uniqueEmail("rotation");
        api.registerStudentAccount(email);
        return email;
    }
}
