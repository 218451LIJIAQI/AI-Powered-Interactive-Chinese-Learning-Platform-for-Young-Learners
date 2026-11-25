package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withAfterMethod;
import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;

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
import com.example.aichinese.service.FileStorageService;
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
    private FileStorageService fileStorageService;

    @Autowired
    private UploadProperties uploadProperties;

    @ParameterizedTest
    @ValueSource(strings = { "delete", "deactivate", "logout" })
    void delayedCreationCannotPersistAfterAccountOrSessionRevocation(String revocation) throws Exception {
        String email = uniqueEmail("support-concurrent");
        api.registerStudent(email);
        SecurityUserDetails requester = "logout".equals(revocation)
                ? principalFor(email).withSession(true, refreshTokenService.issue(user(email), false).sessionId())
                : principalFor(email);
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        CreateSupportRequest form = new CreateSupportRequest(SupportTopic.ACCOUNT,
                "Private account question", "This personal message must not be saved after revocation.");
        MockMultipartFile screenshot = new MockMultipartFile(
                "screenshot", "private-screen.png", "image/png", TestUploadFixtures.minimalPng());
        Set<Path> filesBefore = supportFiles();

        // Pause after staging the screenshot (between the read-only preview check and the write transaction) without
        // adding an outer transaction, so the real two-transaction boundary, per-session revocation, and staged file
        // cleanup are all exercised together.
        assertHttpError(() -> withPausedOperation(
                pause -> withAfterMethod(fileStorageService, "stageSupportScreenshot",
                        args -> true, true, pause,
                        () -> supportService.create(requester, form, screenshot)),
                () -> {
                    switch (revocation) {
                        case "delete" -> accountDeletionService.delete(requester, email);
                        case "deactivate" -> adminUserService.deactivate(admin, requester.getUserId(), "Account review");
                        case "logout" -> authService.logout(requester);
                        default -> throw new AssertionError(revocation);
                    }
                }), 401);

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
        Runnable fail = () -> {
            throw new IllegalStateException("Test callback failure");
        };
        withAfterMethod(messageRepository, "save", args -> true, false,
                afterCommit ? () -> AfterCommit.run(fail) : fail,
                () -> assertThatThrownBy(() -> supportService.create(requester,
                        new CreateSupportRequest(SupportTopic.CLASSES, "Video playback", "The video did not load."),
                        new MockMultipartFile("screenshot", "screen.png", "image/png", image)))
                        .isInstanceOf(IllegalStateException.class).hasMessage("Test callback failure"));
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

        assertHttpError(() -> afterRequestRead(id,
                () -> supportService.reply(admin, id, "Late answer"),
                () -> supportService.solve(admin, id)), 400);

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SupportRequestStatus.SOLVED);
        assertThat(messageCount(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aDelayedReplyOrSolveCannotActAfterLogout(boolean solve) throws Exception {
        SecurityUserDetails requester = requester();
        String sessionId = refreshTokenService.issue(user(ADMIN_EMAIL), false).sessionId();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL).withSession(true, sessionId);
        Long id = createRequest(requester);

        assertHttpError(() -> afterRequestRead(id, () -> writeRequest(admin, id, solve), () -> authService.logout(admin)),
                401);

        assertThat(requestRepository.findById(id).orElseThrow().getStatus()).isEqualTo(SupportRequestStatus.OPEN);
        assertThat(messageCount(id)).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void aDelayedReplyOrSolveCannotRestoreADeletedRequest(boolean solve) throws Exception {
        SecurityUserDetails requester = requester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = createRequest(requester);

        assertHttpError(() -> afterRequestRead(id,
                () -> writeRequest(admin, id, solve),
                () -> accountDeletionService.delete(requester, requester.getUsername())), 404);

        assertThat(requestsOf(requester)).isZero();
        assertThat(messageCount(id)).isZero();
    }

    /**
     * A request sent from a confirmed account's address on the Help page goes with the account, though no account lock
     * guards it: a support reply or solve that read it before the deletion committed finds it gone.
     */
    @ParameterizedTest
    @ValueSource(booleans = { false, true })
    void delayedSupportWriteCannotRestoreAVisitorRequestTheAccountDeletionRemoved(boolean solve) throws Exception {
        SecurityUserDetails account = confirmedRequester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = visitorRequest(account.getUsername());

        assertHttpError(() -> afterRequestRead(id, () -> writeRequest(admin, id, solve),
                () -> accountDeletionService.delete(account, account.getUsername())), 404);

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
        SecurityUserDetails account = confirmedRequester();
        SecurityUserDetails admin = principalFor(ADMIN_EMAIL);
        Long id = visitorRequest(account.getUsername());
        withRowLock(jdbc, "support_requests",
                pause -> withAfterMethod(messageRepository, "deleteByRequestIdIn", pause,
                        () -> accountDeletionService.delete(account, account.getUsername())),
                () -> {
                    assertHttpError(() -> writeRequest(admin, id, solve), 404);
                    return null;
                });

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

    /** Account deletion removes unlinked visitor requests by email only once the account has confirmed its address. */
    private SecurityUserDetails confirmedRequester() throws Exception {
        SecurityUserDetails account = requester();
        jdbc.update("update users set email_verified_at = now() where id = ?", account.getUserId());
        return principalFor(account.getUsername());
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
        withPausedOperation(pause -> withAfterMethod(requestRepository, "findDetailedById",
                args -> id.equals(args[0]), true, pause, delayedWrite), concurrentWrite);
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
