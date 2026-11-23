package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.ConcurrentTransactions.withEarlierRead;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.LearningApiTestClient.ADMIN_EMAIL;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.framework.Advised;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.config.UploadProperties;
import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.entity.enums.SupportClosedBy;
import com.example.aichinese.entity.enums.SupportRequestStatus;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.repository.SupportMessageRepository;
import com.example.aichinese.repository.SupportRequestRepository;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.AuthService;
import com.example.aichinese.service.RefreshTokenService;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.admin.AdminUserService;
import com.example.aichinese.service.support.SupportService;
import com.example.aichinese.support.ApiIntegrationTest;
import com.example.aichinese.support.TestUploadFixtures;
import com.example.aichinese.util.AfterCommit;

/**
 * A request admitted by authentication must recheck the account before creating personal support content, and a
 * support write that waited must see what changed meanwhile (a solve, a sign-out, the requester's account deleted).
 */
class SupportConcurrencyTests extends ApiIntegrationTest {

    @Autowired
    private SupportService supportService;

    @Autowired
    private SupportRequestRepository requestRepository;

    @Autowired
    private SupportMessageRepository messageRepository;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private AuthService authService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private UploadProperties uploadProperties;

    @ParameterizedTest
    @ValueSource(strings = { "delete", "deactivate", "logout" })
    void delayedCreationCannotPersistAfterAccountOrSessionRevocation(String revocation) throws Exception {
        String email = uniqueEmail("support-concurrent");
        api.registerStudent(email);
        SecurityUserDetails requester = principalFor(email);
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        CreateSupportRequest form = new CreateSupportRequest(SupportTopic.ACCOUNT,
                "Private account question", "This personal message must not be saved after revocation.");
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot", "private-screen.png", "image/png", TestUploadFixtures.minimalPng());
        Set<Path> filesBefore = supportFiles();

        assertThatThrownBy(() -> withEarlierRead(transactionManager,
                () -> userRepository.findWithRolesById(requester.getUserId()).orElseThrow(),
                () -> supportService.create(requester, form, screenshot),
                () -> {
                    switch (revocation) {
                        case "delete" -> accountDeletionService.delete(requester, email);
                        case "deactivate" -> adminUserService.deactivate(admin, requester.getUserId(), "Account review");
                        case "logout" -> authService.logout(requester);
                        default -> throw new AssertionError(revocation);
                    }
                })).isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));

        assertThat(requestsOf(requester)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from uploaded_files where owner_user_id = ?", Long.class, requester.getUserId())).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from support_messages where author_user_id = ?", Long.class, requester.getUserId())).isZero();
        assertThat(supportFiles()).isEqualTo(filesBefore);
    }

    @Test
    void creationCommittedBeforeDeletionIsRemovedWithItsScreenshot() throws Exception {
        String email = uniqueEmail("support-created-first");
        api.registerStudent(email);
        SecurityUserDetails requester = principalFor(email);
        supportService.create(requester, new CreateSupportRequest(SupportTopic.ACCOUNT, "Account question", "Help please"),
                new MockMultipartFile("screenshot", "screen.png", "image/png", TestUploadFixtures.minimalPng()));
        String storedName = jdbc.queryForObject(
                "select stored_file_name from uploaded_files where owner_user_id = ?", String.class, requester.getUserId());
        Path screenshot = Path.of(uploadProperties.getDirectory(), "support", storedName);
        assertThat(screenshot).exists();

        accountDeletionService.delete(requester, email);

        assertThat(requestsOf(requester)).isZero();
        assertThat(jdbc.queryForObject(
                "select count(*) from uploaded_files where owner_user_id = ?", Long.class, requester.getUserId())).isZero();
        assertThat(screenshot).doesNotExist();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void screenshotFollowsTheDatabaseOutcomeWhenAWriteOrCommitCallbackThrows(boolean afterCommit) throws Exception {
        String email = uniqueEmail("support-file-outcome");
        api.registerStudent(email);
        SecurityUserDetails requester = principalFor(email);
        Set<Path> filesBefore = supportFiles();
        byte[] image = TestUploadFixtures.minimalPng();
        MethodInterceptor failAfterMessage = invocation -> {
            Object result = invocation.proceed();
            if (invocation.getMethod().getName().equals("save")) {
                Runnable fail = () -> {
                    throw new IllegalStateException("Test callback failure");
                };
                if (afterCommit) {
                    AfterCommit.run(fail);
                } else {
                    fail.run();
                }
            }
            return result;
        };
        Advised messages = (Advised) messageRepository;
        messages.addAdvice(0, failAfterMessage);
        try {
            assertThatThrownBy(() -> supportService.create(requester,
                    new CreateSupportRequest(SupportTopic.CLASSES, "Video playback", "The video did not load."),
                    new MockMultipartFile("screenshot", "screen.png", "image/png", image)))
                    .isInstanceOf(IllegalStateException.class).hasMessage("Test callback failure");
        } finally {
            messages.removeAdvice(failAfterMessage);
        }
        if (afterCommit) {
            assertThat(requestsOf(requester)).isEqualTo(1);
            String stored = jdbc.queryForObject("select stored_file_name from uploaded_files where owner_user_id = ?",
                    String.class, requester.getUserId());
            assertThat(Path.of(uploadProperties.getDirectory(), "support", stored)).hasBinaryContent(image);
        } else {
            assertThat(requestsOf(requester)).isZero();
            assertThat(supportFiles()).isEqualTo(filesBefore);
        }
    }

    /** One admin solves a request while another's reply waits: the late reply finds it solved and adds nothing. */
    @Test
    void aDelayedReplyCannotReopenARequestAnotherAdminSolved() throws Exception {
        SecurityUserDetails requester = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = createRequest(requester);

        assertThatThrownBy(() -> afterRequestRead(id,
                () -> supportService.reply(admin, id, "Late answer"),
                () -> supportService.solve(admin, id)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(400));

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SupportRequestStatus.SOLVED);
        assertThat(messageCount(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aDelayedReplyOrSolveCannotActAfterLogout(boolean solve) throws Exception {
        SecurityUserDetails requester = requester();
        String sessionId = refreshTokenService.issue(user(ADMIN_EMAIL), "127.0.0.1", "test-browser", false).sessionId();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL).withSession(true, sessionId);
        Long id = createRequest(requester);

        assertThatThrownBy(() -> afterRequestRead(id, () -> writeRequest(admin, id, solve), () -> authService.logout(admin)))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(401));

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SupportRequestStatus.OPEN);
        assertThat(messageCount(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aDelayedReplyOrSolveCannotRestoreADeletedRequest(boolean solve) throws Exception {
        SecurityUserDetails requester = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = createRequest(requester);

        assertThatThrownBy(() -> afterRequestRead(id,
                () -> writeRequest(admin, id, solve),
                () -> accountDeletionService.delete(requester, requester.getUsername())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));

        assertThat(requestsOf(requester)).isZero();
        assertThat(messageCount(id)).isZero();
    }

    /**
     * A request sent from the account's address on the Help page goes with the account, though no account lock guards
     * it: a support reply or solve that read it before the deletion committed finds it gone.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void delayedSupportWriteCannotRestoreAVisitorRequestTheAccountDeletionRemoved(boolean solve) throws Exception {
        SecurityUserDetails account = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = visitorRequest(account.getUsername());

        assertThatThrownBy(() -> afterRequestRead(id, () -> writeRequest(admin, id, solve),
                () -> accountDeletionService.delete(account, account.getUsername())))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404));

        assertThat(requestRepository.findById(id)).isEmpty();
        assertThat(messageCount(id)).isZero();
    }

    /**
     * The other order: the deletion has removed the messages of the visitor's request when support answers or solves
     * it. The deletion holds the request's lock, so the write waits, then finds the request gone; it does not add a
     * message the deletion of the request would then fail on.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aSupportWriteWaitsForTheDeletionOfAVisitorRequestAndFindsItGone(boolean solve) throws Exception {
        SecurityUserDetails account = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = visitorRequest(account.getUsername());
        withRowLock(jdbc, "support_requests", pause -> {
            MethodInterceptor afterDeletingMessages = invocation -> {
                Object result = invocation.proceed();
                if (invocation.getMethod().getName().equals("deleteByRequestIdIn")) {
                    pause.run();
                }
                return result;
            };
            Advised messages = (Advised) messageRepository;
            messages.addAdvice(0, afterDeletingMessages);
            try {
                accountDeletionService.delete(account, account.getUsername());
            } finally {
                messages.removeAdvice(afterDeletingMessages);
            }
        }, () -> assertThatThrownBy(() -> writeRequest(admin, id, solve)).isInstanceOfSatisfying(
                ResponseStatusException.class, failure -> assertThat(failure.getStatusCode().value()).isEqualTo(404)));

        assertThat(requestRepository.findById(id)).isEmpty();
        assertThat(messageCount(id)).isZero();
    }

    @Test
    void solvingAnExpiredAnswerPreservesItsAutomaticClosingTime() throws Exception {
        SecurityUserDetails requester = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = createRequest(requester);
        supportService.reply(admin, id, "An earlier answer");
        LocalDateTime deadline = LocalDateTime.now().minusDays(1).withNano(0);
        // Bound as a Timestamp, as Hibernate binds it, so the connection's time-zone conversion applies.
        jdbc.update("update support_requests set closes_at = ? where id = ?", Timestamp.valueOf(deadline), id);

        supportService.solve(admin, id);

        var saved = requestRepository.findById(id).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(SupportRequestStatus.SOLVED);
        assertThat(saved.getClosedBy()).isEqualTo(SupportClosedBy.AUTO);
        assertThat(saved.getSolvedAt()).isEqualTo(deadline);
    }

    private SecurityUserDetails requester() throws Exception {
        String email = uniqueEmail("support-state-race");
        api.registerStudentAccount(email);
        return principalFor(email);
    }

    private Long createRequest(SecurityUserDetails requester) {
        return supportRequestId(supportService.create(requester,
                new CreateSupportRequest(SupportTopic.ACCOUNT, "A private question", "Help please"), null).number());
    }

    /** A request sent from this address through the public Help page (signed out, so no account is linked). */
    private Long visitorRequest(String email) {
        return supportRequestId(supportService.createForVisitor(
                new VisitorSupportRequest(email, SupportTopic.ACCOUNT, "I can't sign in to my account", null)).number());
    }

    private long requestsOf(SecurityUserDetails requester) {
        return jdbc.queryForObject(
                "select count(*) from support_requests where requester_user_id = ?", Long.class, requester.getUserId());
    }

    private void afterRequestRead(Long id, Runnable delayedWrite, Runnable concurrentWrite) throws Exception {
        // Pause the real service transaction after its repository read, without adding an outer transaction.
        withPausedOperation(pause -> {
            Thread delayedThread = Thread.currentThread();
            AtomicBoolean paused = new AtomicBoolean();
            MethodInterceptor pauseAfterRead = invocation -> {
                Object result = invocation.proceed();
                if (Thread.currentThread() == delayedThread
                        && invocation.getMethod().getName().equals("findDetailedById")
                        && id.equals(invocation.getArguments()[0]) && paused.compareAndSet(false, true)) {
                    pause.run();
                }
                return result;
            };
            Advised repository = (Advised) requestRepository;
            repository.addAdvice(0, pauseAfterRead);
            try {
                delayedWrite.run();
            } finally {
                repository.removeAdvice(pauseAfterRead);
            }
        }, concurrentWrite);
    }

    private void writeRequest(SecurityUserDetails admin, Long id, boolean solve) {
        if (solve) {
            supportService.solve(admin, id);
        } else {
            supportService.reply(admin, id, "Support reply");
        }
    }

    private long messageCount(Long id) {
        return jdbc.queryForObject("select count(*) from support_messages where request_id = ?", Long.class, id);
    }

    private Set<Path> supportFiles() throws IOException {
        Path directory = Path.of(uploadProperties.getDirectory(), "support");
        if (!Files.isDirectory(directory)) {
            return Set.of();
        }
        try (var files = Files.list(directory)) {
            return files.collect(Collectors.toSet());
        }
    }
}
