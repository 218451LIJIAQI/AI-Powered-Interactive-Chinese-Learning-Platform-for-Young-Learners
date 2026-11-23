package com.example.aichinese;

import static com.example.aichinese.support.ConcurrentTransactions.withPausedOperation;
import static com.example.aichinese.support.ConcurrentTransactions.withRowLock;
import static com.example.aichinese.support.LearningApiTestClient.uniqueEmail;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.dto.support.CreateSupportRequest;
import com.example.aichinese.dto.support.VisitorSupportRequest;
import com.example.aichinese.entity.enums.SupportTopic;
import com.example.aichinese.security.SecurityUserDetails;
import com.example.aichinese.service.account.AccountDeletionService;
import com.example.aichinese.service.support.SupportRequestNumberSequence;
import com.example.aichinese.service.support.SupportService;
import com.example.aichinese.support.ApiIntegrationTest;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/** Number allocation must see committed allocations even when the request has an earlier read view. */
class SupportNumberingTests extends ApiIntegrationTest {

    @Autowired
    private SupportService supportService;

    @Autowired
    private SupportRequestNumberSequence numberSequence;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private AccountDeletionService accountDeletionService;

    @PersistenceContext
    private EntityManager entityManager;

    @ParameterizedTest
    @ValueSource(booleans = { true, false })
    void earlierSnapshotDoesNotReuseAnotherRequestsNumber(boolean signedIn) throws Exception {
        String email = uniqueEmail("support-number");
        api.registerStudentAccount(email);
        SecurityUserDetails requester = principalFor(email);
        AtomicInteger delayedNumber = new AtomicInteger();
        AtomicInteger otherNumber = new AtomicInteger();
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        withPausedOperation(pause -> transaction.executeWithoutResult(status -> {
            jdbc.queryForObject("select coalesce(max(request_number), 0) from support_requests", Integer.class);
            pause.run();
            delayedNumber.set(signedIn ? create(requester) : visitor());
        }), () -> otherNumber.set(visitor()));

        assertThat(delayedNumber.get()).isGreaterThan(otherNumber.get());
        assertThat(jdbc.queryForObject(
                "select count(*) from support_requests where request_number in (?, ?)", Long.class,
                delayedNumber.get(), otherNumber.get())).isEqualTo(2);
    }

    @Test
    void simultaneousVisitorsAndDifferentUsersAllReceiveUniqueNumbers() throws Exception {
        List<SecurityUserDetails> requesters = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String email = uniqueEmail("number-parallel");
            api.registerStudentAccount(email);
            requesters.add(principalFor(email));
        }
        CountDownLatch ready = new CountDownLatch(4);
        CountDownLatch start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            List<Future<Integer>> submissions = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                int index = i;
                submissions.add(executor.submit(() -> {
                    ready.countDown();
                    assertThat(start.await(10, TimeUnit.SECONDS)).isTrue();
                    return index < 2 ? create(requesters.get(index)) : visitor();
                }));
            }
            try {
                assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            } finally {
                start.countDown();
            }
            List<Integer> numbers = new ArrayList<>();
            for (Future<Integer> submission : submissions) numbers.add(submission.get(10, TimeUnit.SECONDS));
            assertThat(numbers).hasSize(4).doesNotHaveDuplicates();
            assertThat(jdbc.queryForList("""
                    select r.request_number from support_messages m join support_requests r on m.request_id = r.id
                    where r.request_number in (?, ?, ?, ?)
                    """, Integer.class, numbers.toArray())).containsExactlyInAnyOrderElementsOf(numbers);
        }
    }

    @Test
    void rolledBackCreationRollsBackItsNumberAndMessage() {
        AtomicInteger rolledBackNumber = new AtomicInteger();
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            rolledBackNumber.set(visitor());
            status.setRollbackOnly();
        });
        assertThat(jdbc.queryForObject("select count(*) from support_requests where request_number = ?",
                Long.class, rolledBackNumber.get())).isZero();
        assertThat(visitor()).isEqualTo(rolledBackNumber.get());
    }

    @Test
    void deletingTheLastRequestDoesNotReuseItsPublishedNumber() throws Exception {
        String email = uniqueEmail("number-deleted");
        api.registerStudentAccount(email);
        SecurityUserDetails requester = principalFor(email);
        int deletedNumber = create(requester);
        accountDeletionService.delete(requester, email);

        assertThat(visitor()).isGreaterThan(deletedNumber);
    }

    @Test
    void startupAdvancesPastExistingDemoOrDesktopNumbersWithoutRewinding() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            int original = visitor();
            int existingNumber = original + 100;
            jdbc.update("update support_requests set request_number = ? where request_number = ?",
                    existingNumber, original);
            numberSequence.run();
            int next = visitor();
            assertThat(next).isEqualTo(existingNumber + 1);
            int advancedCounter = next + 10;
            jdbc.update("update support_request_numbers set next_number = ? where id = 1", advancedCounter);
            entityManager.clear();
            numberSequence.run();
            assertThat(visitor()).isEqualTo(advancedCounter);
            status.setRollbackOnly();
        });
    }

    @Test
    void simultaneousStartupRestoresOneCounterPastExistingRequests() throws Exception {
        int issued = visitor();
        jdbc.update("delete from support_request_numbers where id = 1");
        try {
            withRowLock(jdbc, "support_request_numbers", new TransactionTemplate(transactionManager),
                    () -> numberSequence.run(), () -> {
                        numberSequence.run();
                        return null;
                    });
            assertThat(visitor()).isEqualTo(issued + 1);
        } finally {
            numberSequence.run();
        }
    }

    @Test
    void integerExhaustionIsRejectedWithoutWrapping() {
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            jdbc.update("update support_request_numbers set next_number = ? where id = 1", Integer.MAX_VALUE);
            assertThat(visitor()).isEqualTo(Integer.MAX_VALUE);
            assertThatThrownBy(this::visitor).isInstanceOfSatisfying(ResponseStatusException.class,
                    failure -> assertThat(failure.getStatusCode().value()).isEqualTo(503));
            status.setRollbackOnly();
        });
    }

    private int create(SecurityUserDetails requester) {
        return supportService.create(requester,
                new CreateSupportRequest(SupportTopic.ACCOUNT, "Number allocation", "Help please"), null)
                .number();
    }

    private int visitor() {
        return supportService.createForVisitor(new VisitorSupportRequest(
                uniqueEmail("visitor-number"), SupportTopic.ACCOUNT, "Help with my account", null)).number();
    }
}
