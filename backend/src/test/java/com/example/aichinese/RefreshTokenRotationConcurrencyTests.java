package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.Advised;
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

    private static final String CLIENT_IP = "203.0.113.7";
    private static final String USER_AGENT = "concurrency-test";

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void twoLearnersRotateTheirRefreshTokensAtOnce() throws Exception {
        String firstEmail = registeredLearner();
        String secondEmail = registeredLearner();
        String firstToken = refreshTokenService.issue(user(firstEmail), CLIENT_IP, USER_AGENT, false).rawToken();
        String secondToken = refreshTokenService.issue(user(secondEmail), CLIENT_IP, USER_AGENT, false).rawToken();
        AtomicReference<RefreshTokenService.RefreshSession> first = new AtomicReference<>();
        AtomicReference<RefreshTokenService.RefreshSession> second = new AtomicReference<>();

        withPausedOperation(pause -> {
            Thread paused = Thread.currentThread();
            AtomicBoolean fired = new AtomicBoolean();
            MethodInterceptor afterReplacementSave = invocation -> {
                Object result = invocation.proceed();
                if (Thread.currentThread() == paused
                        && invocation.getMethod().getName().equals("save")
                        && fired.compareAndSet(false, true)) {
                    pause.run();
                }
                return result;
            };
            Advised repository = (Advised) refreshTokenRepository;
            repository.addAdvice(0, afterReplacementSave);
            try {
                first.set(refreshTokenService.rotate(firstToken, CLIENT_IP, USER_AGENT));
            } finally {
                repository.removeAdvice(afterReplacementSave);
            }
        }, () -> second.set(refreshTokenService.rotate(secondToken, CLIENT_IP, USER_AGENT)));

        assertThat(first.get().user().getEmail()).isEqualTo(firstEmail);
        assertThat(second.get().user().getEmail()).isEqualTo(secondEmail);
    }

    private String registeredLearner() throws Exception {
        String email = uniqueEmail("rotation");
        api.registerStudentAccount(email);
        return email;
    }
}
