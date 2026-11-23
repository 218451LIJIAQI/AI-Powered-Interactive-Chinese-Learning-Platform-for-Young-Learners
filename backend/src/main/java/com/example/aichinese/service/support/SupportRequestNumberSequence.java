package com.example.aichinese.service.support;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.example.aichinese.entity.SupportRequestNumber;
import com.example.aichinese.exception.FriendlyErrorMessageResolver;
import com.example.aichinese.repository.SupportRequestRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

import lombok.RequiredArgsConstructor;

/**
 * Seeds and allocates sequential support request numbers ({@code #1000}, {@code #1001}, ...) under a single-row
 * database write lock held until the creating transaction commits or rolls back.
 */
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@RequiredArgsConstructor
public class SupportRequestNumberSequence implements CommandLineRunner {

    private static final int COUNTER_ROW_ID = 1;

    private final SupportRequestRepository requestRepository;
    private final EntityManager entityManager;

    /** Flyway seeds the main database; the Hibernate-created demo and test schemas need the same starting point. */
    @Override
    @Transactional
    public void run(String... args) {
        entityManager.createNativeQuery("""
                insert into support_request_numbers (id, next_number) values (1, 1000)
                on duplicate key update id = id
                """).executeUpdate();
        SupportRequestNumber counter = lockedCounter();
        counter.setNextNumber(Math.max(counter.getNextNumber(), (long) requestRepository.findHighestNumber() + 1));
    }

    /** Allocates the next sequential request number inside the caller's write transaction. */
    @Transactional(propagation = Propagation.MANDATORY)
    int nextNumber() {
        SupportRequestNumber counter = lockedCounter();
        if (counter == null || counter.getNextNumber() > Integer.MAX_VALUE) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, FriendlyErrorMessageResolver.serverUnavailableMessage());
        }
        int number = (int) counter.getNextNumber();
        counter.setNextNumber((long) number + 1);
        return number;
    }

    private SupportRequestNumber lockedCounter() {
        return entityManager.find(SupportRequestNumber.class, COUNTER_ROW_ID, LockModeType.PESSIMISTIC_WRITE);
    }
}
