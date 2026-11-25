package com.example.aichinese.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Predicate;

import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.Advised;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Concurrency test harness: prove row-lock serialization against InnoDB ({@link #withRowLock}), interleave a
 * competing commit after an earlier read ({@link #withEarlierRead}), or pause at a Spring AOP boundary
 * ({@link #withPausedOperation}, {@link #withAfterMethod}).
 */
public final class ConcurrentTransactions {

    private ConcurrentTransactions() {
    }

    public static TransactionTemplate readCommitted(PlatformTransactionManager transactionManager) {
        TransactionTemplate transaction = new TransactionTemplate(transactionManager);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        return transaction;
    }

    /** Start the competing request at the first operation's chosen boundary and prove it blocked before continuing. */
    public static <T> T withRowLock(JdbcTemplate jdbc, String table, Consumer<Runnable> first, Callable<T> second)
            throws Exception {
        var started = new AtomicBoolean();
        var waiting = new AtomicReference<Future<T>>();
        var worker = Executors.newSingleThreadExecutor();
        try {
            first.accept(() -> {
                if (started.compareAndSet(false, true)) {
                    Future<T> request = worker.submit(second);
                    waiting.set(request);
                    awaitRowLock(jdbc, table, request);
                }
            });
            assertThat(waiting.get()).as("the first operation reached its lock boundary").isNotNull();
            return awaitUnwrapped(waiting.get(), 20, TimeUnit.SECONDS);
        } finally {
            shutdownAndAwait(worker);
        }
    }

    public static <T> T withRowLock(JdbcTemplate jdbc, String table, TransactionTemplate transaction,
            Runnable first, Callable<T> second) throws Exception {
        return withRowLock(jdbc, table, pause -> transaction.executeWithoutResult(status -> {
            first.run();
            pause.run();
        }), second);
    }

    /** Wait until the competing operation is actually blocked on the named MySQL table. */
    private static void awaitRowLock(JdbcTemplate jdbc, String table, Future<?> operation) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (!operation.isDone() && System.nanoTime() < deadline) {
            Boolean waiting = jdbc.queryForObject("""
                    select exists(select 1 from performance_schema.data_locks
                                  where object_schema = database() and object_name = ? and lock_status = 'WAITING')
                    """, Boolean.class, table);
            if (Boolean.TRUE.equals(waiting)) {
                return;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
        if (operation.isDone()) {
            try {
                operation.get();
            } catch (ExecutionException failure) {
                throw new AssertionError("Concurrent request failed before waiting on " + table, failure.getCause());
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError(interrupted);
            }
        }
        throw new AssertionError("No concurrent request waited for a row lock on " + table);
    }

    private static void await(CountDownLatch latch) {
        try {
            assertThat(latch.await(20, TimeUnit.SECONDS)).as("transaction reached its expected step").isTrue();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new AssertionError(interrupted);
        }
    }

    public static void withEarlierRead(PlatformTransactionManager transactionManager,
            Runnable read, Runnable delayedSave, Runnable concurrentSave) throws Exception {
        withPausedOperation(pause -> new TransactionTemplate(transactionManager).executeWithoutResult(status -> {
            read.run();
            pause.run();
            delayedSave.run();
        }), concurrentSave);
    }

    /**
     * Runs {@code hook} once after {@code methodName} returns on the calling thread while {@code action} executes
     * against a Spring AOP proxy, removing the advice in {@code finally}.
     */
    public static void withAfterMethod(Object advisedBean, String methodName, Runnable hook, Runnable action) {
        withAfterMethod(advisedBean, methodName, args -> true, true, hook, action);
    }

    /**
     * Runs {@code hook} immediately after matching calls to {@code methodName} return on the calling thread while
     * {@code action} executes against a Spring AOP proxy, removing the advice in {@code finally}.
     */
    public static void withAfterMethod(
            Object advisedBean,
            String methodName,
            Predicate<Object[]> argsMatch,
            boolean once,
            Runnable hook,
            Runnable action) {
        Thread installingThread = Thread.currentThread();
        AtomicBoolean fired = new AtomicBoolean();
        MethodInterceptor interceptor = invocation -> {
            Object result = invocation.proceed();
            if (Thread.currentThread() == installingThread
                    && invocation.getMethod().getName().equals(methodName)
                    && argsMatch.test(invocation.getArguments())
                    && (!once || fired.compareAndSet(false, true))) {
                hook.run();
            }
            return result;
        };
        Advised advised = (Advised) advisedBean;
        synchronized (advised) {
            advised.addAdvice(0, interceptor);
        }
        try {
            action.run();
        } finally {
            synchronized (advised) {
                advised.removeAdvice(interceptor);
            }
        }
    }

    /** The first operation chooses its pause boundary, with or without a surrounding transaction. */
    public static void withPausedOperation(Consumer<Runnable> operation, Runnable concurrentSave) throws Exception {
        CompletableFuture<Boolean> reachedPause = new CompletableFuture<>();
        CountDownLatch continueSave = new CountDownLatch(1);
        AtomicBoolean aborted = new AtomicBoolean();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var delayed = executor.submit(() -> {
                try {
                    operation.accept(() -> {
                        reachedPause.complete(true);
                        await(continueSave);
                        if (aborted.get()) {
                            throw new AssertionError("Concurrent test aborted before post-pause writes.");
                        }
                    });
                } finally {
                    // Wake the test even if the worker failed or returned before reaching its pause.
                    reachedPause.complete(false);
                }
            });
            boolean paused = reachedPause.get(20, TimeUnit.SECONDS);
            if (!paused) {
                try {
                    delayed.get(10, TimeUnit.SECONDS);
                } catch (ExecutionException failure) {
                    throw new AssertionError("The first operation failed before reaching its pause", failure.getCause());
                }
                throw new AssertionError("The first operation returned before reaching its pause");
            }
            concurrentSave.run();
            continueSave.countDown();
            awaitUnwrapped(delayed, 10, TimeUnit.SECONDS);
        } finally {
            aborted.set(true);
            continueSave.countDown();
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    shutdownAndAwait(executor);
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                shutdownAndAwait(executor);
            }
        }
    }

    private static <T> T awaitUnwrapped(Future<T> future, long timeout, TimeUnit unit) throws Exception {
        try {
            return future.get(timeout, unit);
        } catch (ExecutionException failure) {
            if (failure.getCause() instanceof Exception cause) {
                throw cause;
            }
            if (failure.getCause() instanceof Error cause) {
                throw cause;
            }
            throw failure;
        }
    }

    private static void shutdownAndAwait(ExecutorService executor) {
        executor.shutdownNow();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
