package com.example.aichinese.service;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Shared programmatic transaction boundaries for services that keep slow external I/O (AI calls, file staging) outside
 * database locks: read-only snapshots and short {@code READ_COMMITTED} writes.
 */
@Component
public class TransactionRunner {

    private final TransactionTemplate readOnlyTemplate;
    private final TransactionTemplate readCommittedWriteTemplate;

    public TransactionRunner(PlatformTransactionManager transactionManager) {
        this.readOnlyTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTemplate.setReadOnly(true);
        this.readCommittedWriteTemplate = new TransactionTemplate(transactionManager);
        this.readCommittedWriteTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public <T> T readOnly(Supplier<T> work) {
        return readOnlyTemplate.execute(status -> work.get());
    }

    public void readOnly(Runnable work) {
        readOnlyTemplate.executeWithoutResult(status -> work.run());
    }

    public <T> T writeReadCommitted(Supplier<T> work) {
        return readCommittedWriteTemplate.execute(status -> work.get());
    }

    public void writeReadCommitted(Runnable work) {
        readCommittedWriteTemplate.executeWithoutResult(status -> work.run());
    }
}
