package com.example.aichinese.util;

import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Side effects that must only follow committed data (best-effort audit and notification rows, file deletion). */
public final class AfterCommit {

    private AfterCommit() {
    }

    /** Runs {@code action} after the current transaction commits (never when it rolls back), or at once without one. */
    public static void run(Runnable action) {
        if (hasActiveSynchronizedTransaction()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }

    /** Runs {@code action} if the current transaction rolls back (no-op when no synchronized transaction is active). */
    public static void onRollback(Runnable action) {
        if (!hasActiveSynchronizedTransaction()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    action.run();
                }
            }
        });
    }

    private static boolean hasActiveSynchronizedTransaction() {
        return TransactionSynchronizationManager.isActualTransactionActive()
                && TransactionSynchronizationManager.isSynchronizationActive();
    }
}
